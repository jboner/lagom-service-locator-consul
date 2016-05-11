package com.lightbend.lagom.discovery.consul

import java.io.File
import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{Function => JFunction}
import javax.inject.Inject

import com.ecwid.consul.v1.catalog.model.CatalogService
import com.ecwid.consul.v1.{QueryParams, ConsulClient}
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.typesafe.config.ConfigException.BadValue
import play.api.{Configuration, Environment, Mode}

import scala.collection.JavaConversions._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

class ConsulServiceLocator @Inject()(implicit ec: ExecutionContext) extends ServiceLocator {

  val config = Configuration.load(Environment(new File("."), getClass.getClassLoader, Mode.Prod)).underlying
  val hostname = config.getString("lagom.discovery.consul.hostname")
  val scheme = config.getString("lagom.discovery.consul.scheme")
  val routing = config.getString("lagom.discovery.consul.routing")

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
    val services = client.getCatalogService(name, QueryParams.DEFAULT).getValue
    val toURI = (service: CatalogService) => Some(new URI(s"$scheme://${service.getServiceAddress}:${service.getServicePort}"))
    services.size match {
      case 0    => None
      case 1    => toURI(services.get(0))
      case more =>
        routing match {
          case "first"       => toURI(services.sortWith(_.toString < _.toString).get(0))
          case "random"      => toURI(services.sortWith(_.toString < _.toString).get(Random.nextInt(more - 1)))
          case "round-robin" => toURI(services.sortWith(_.toString < _.toString).get(Random.nextInt(more - 1)))
          case unknown       => throw new BadValue("lagom.discovery.consul.routing", s"[$unknown] is not a valid routing algorithm")
    }
  }
}
