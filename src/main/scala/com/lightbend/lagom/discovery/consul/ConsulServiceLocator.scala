package com.lightbend.lagom.discovery.consul

import java.net.InetAddress
import java.net.URI
import java.util.Optional
import java.util.concurrent.CompletionStage
import java.util.function.{ Function => JFunction }

import scala.collection.JavaConverters._
import scala.collection.concurrent.Map
import scala.collection.concurrent.TrieMap
import scala.compat.java8.FutureConverters._
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.{Random => JRandom}

import com.ecwid.consul.v1.ConsulClient
import com.ecwid.consul.v1.QueryParams
import com.ecwid.consul.v1.catalog.model.CatalogService
import com.lightbend.lagom.javadsl.api.ServiceLocator

import javax.inject.Inject

class ConsulServiceLocator @Inject()(client: ConsulClient, config: ConsulConfig)(implicit ec: ExecutionContext) extends ServiceLocator {

  private val roundRobinIndexFor: Map[String, Int] = TrieMap.empty[String, Int]

  override def locate(name: String): CompletionStage[Optional[URI]] =
    locateAsScala(name).map(_.asJava).toJava

  override def doWithService[T](name: String, block: JFunction[URI, CompletionStage[T]]): CompletionStage[Optional[T]] =
    locateAsScala(name).flatMap { uriOpt =>
      uriOpt.fold(Future.successful(Optional.empty[T])) { uri =>
        block.apply(uri).toScala.map(Optional.of(_))
      }
    }.toJava

  private def locateAsScala(name: String): Future[Option[URI]] = Future {
    val instances = client.getCatalogService(name, QueryParams.DEFAULT).getValue.asScala.toList
    instances.size match {
      case 0 => None
      case 1 => toURIs(instances).headOption
      case _ =>
        config.routingPolicy match {
          case First      => Some(pickFirstInstance(instances))
          case Random     => Some(pickRandomInstance(instances))
          case RoundRobin => Some(pickRoundRobinInstance(name, instances))
        }
    }
  }

  private implicit object DefaultOrdering extends Ordering[URI] {
    override def compare(x: URI, y: URI): Int = x.compareTo(y)
  }

  private[consul] def pickFirstInstance(services: List[CatalogService]): URI = {
    assert(services.nonEmpty)
    toURIs(services).sorted.head
  }

  private[consul] def pickRandomInstance(services: List[CatalogService]): URI = {
    assert(services.nonEmpty)
    toURIs(services).sorted.apply(JRandom.nextInt(services.size - 1))
  }

  private[consul] def pickRoundRobinInstance(name: String, services: List[CatalogService]): URI = {
    assert(services.nonEmpty)
    roundRobinIndexFor.putIfAbsent(name, 0)
    val sortedServices = toURIs(services).sorted
    val currentIndex = roundRobinIndexFor(name)
    val nextIndex =
      if (sortedServices.size > currentIndex + 1) currentIndex + 1
      else 0
    roundRobinIndexFor += (name -> nextIndex)
    sortedServices.apply(currentIndex)
  }

  private def toURIs(services: List[CatalogService]): List[URI] =
    services.map { service =>
      val address = service.getServiceAddress
      val serviceAddress =
        if (address.trim.isEmpty || address == "localhost") InetAddress.getLoopbackAddress.getHostAddress
        else address
      new URI(s"${config.scheme}://$serviceAddress:${service.getServicePort}")
    }
}
