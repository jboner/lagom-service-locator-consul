package com.lightbend.lagom.discovery.consul

import java.io.File
import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{Function => JFunction}
import javax.inject.Inject

import com.ecwid.consul.v1.ConsulClient
import com.lightbend.lagom.javadsl.api.ServiceLocator
import play.api.{Configuration, Environment, Mode}

import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.{ExecutionContext, Future}

class ConsulServiceLocator @Inject()(implicit ec: ExecutionContext) extends ServiceLocator {

  val config = Configuration.load(Environment(new File("."), getClass.getClassLoader, Mode.Prod)).underlying
  val hostname = config.getString("lagom.discovery.consul.hostname")
  val scheme = config.getString("lagom.discovery.consul.scheme")

  val client = new ConsulClient(hostname)

  override def locate(name: String): CompletionStage[Optional[URI]] =
    locateAsScala(name).map(_.asJava).toJava

  override def doWithService[T](name: String, block: JFunction[URI, CompletionStage[T]]): CompletionStage[Optional[T]] =
    locateAsScala(name).flatMap { uriOpt =>
      uriOpt.fold(Future.successful(Optional.empty[T])) { uri =>
        block.apply(uri).toScala.map(Optional.of(_))
      }
    }.toJava

  private def locateAsScala(name: String): Future[Option[URI]] = Future {
    // FIXME: use services or members?
    val services = client.getAgentServices.getValue
    if (services.containsKey(name)) {
      val service = services.get(name)
      Some(new URI(s"$scheme://${service.getAddress}:${service.getPort}"))
    } else None
  }
}
