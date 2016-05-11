package com.lightbend.lagom.discovery.consul

import java.io.File
import java.net.{InetAddress, URI}
import java.util.Optional
import java.util.concurrent.{ConcurrentHashMap, CompletionStage}
import java.util.function.{Function => JFunction}
import javax.inject.Inject

import com.ecwid.consul.v1.catalog.model.CatalogService
import com.ecwid.consul.v1.{QueryParams, ConsulClient}
import com.lightbend.lagom.javadsl.api.ServiceLocator
import com.typesafe.config.ConfigException.BadValue
import play.api.{Configuration, Environment, Mode}

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.convert.decorateAsScala._
import scala.collection.concurrent.Map
import scala.collection.JavaConversions._
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.util.Random

class ConsulServiceLocator @Inject()(implicit ec: ExecutionContext) extends ServiceLocator {

  val config = Configuration.load(Environment(new File("."), getClass.getClassLoader, Mode.Prod)).underlying
  val hostname = config.getString("lagom.discovery.consul.agent-hostname")
  val scheme = config.getString("lagom.discovery.consul.uri-scheme")
  val routingPolicy = config.getString("lagom.discovery.consul.routing-policy")

  private val client = new ConsulClient(hostname)
  private val roundRobinIndexFor: Map[String, Int] = new ConcurrentHashMap[String, Int]().asScala

  override def locate(name: String): CompletionStage[Optional[URI]] =
    locateAsScala(name).map(_.asJava).toJava

  override def doWithService[T](name: String, block: JFunction[URI, CompletionStage[T]]): CompletionStage[Optional[T]] =
    locateAsScala(name).flatMap { uriOpt =>
      uriOpt.fold(Future.successful(Optional.empty[T])) { uri =>
        block.apply(uri).toScala.map(Optional.of(_))
      }
    }.toJava

  private def locateAsScala(name: String): Future[Option[URI]] = Future {
    val services = client.getCatalogService(name, QueryParams.DEFAULT).getValue.toList
    services.size match {
      case 0 => None
      case 1 => Some(toURIs(services).get(0))
      case _ =>
        routingPolicy match {
          case "first"       => Some(pickFirstInstance(services))
          case "random"      => Some(pickRandomInstance(services))
          case "round-robin" => Some(pickRoundRobinInstance(name, services))
          case unknown       => throw new BadValue("lagom.discovery.consul.routing-policy", s"[$unknown] is not a valid routing algorithm")
        }
    }
  }


  private[consul] def pickFirstInstance(services: List[CatalogService]): URI = {
    assert(services.size > 1)
    toURIs(services).sortWith(_.toString < _.toString).get(0)
  }

  private[consul] def pickRandomInstance(services: List[CatalogService]): URI = {
    assert(services.size > 1)
    toURIs(services).sortWith(_.toString < _.toString).get(Random.nextInt(services.size - 1))
  }

  private[consul] def pickRoundRobinInstance(name: String, services: List[CatalogService]): URI = {
    assert(services.size > 1)
    roundRobinIndexFor.putIfAbsent(name, 0)
    val sortedServices = toURIs(services).sortWith(_.toString < _.toString)
    val currentIndex = roundRobinIndexFor(name)
    val nextIndex =
      if (sortedServices.size > currentIndex + 1) currentIndex + 1
      else 0
    roundRobinIndexFor += (name -> nextIndex)
    sortedServices.get(currentIndex)
  }

  private def toURIs(services: List[CatalogService]): List[URI] =
    services.map { service =>
      val address = service.getServiceAddress
      val serviceAddress =
        if (address == "" || address == "localhost") InetAddress.getLoopbackAddress.getHostAddress
        else address
      new URI(s"$scheme://${serviceAddress}:${service.getServicePort}")
    }
}
