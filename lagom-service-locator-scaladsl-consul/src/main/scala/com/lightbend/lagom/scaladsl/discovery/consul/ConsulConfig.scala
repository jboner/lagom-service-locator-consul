package com.lightbend.lagom.scaladsl.discovery.consul

import com.typesafe.config.ConfigException.BadValue
import play.api.Configuration

trait ConsulConfig {
  def agentHostname: String

  def agentPort: Int

  def scheme: String

  def routingPolicy: RoutingPolicy
}

object ConsulConfig {

  class ConsulConfigImpl(config: Configuration) extends ConsulConfig {
    override val agentHostname: String = config.get[String]("lagom.discovery.consul.agent-hostname")
    override val agentPort: Int = config.get[Int]("lagom.discovery.consul.agent-port")
    override val scheme: String = config.get[String]("lagom.discovery.consul.uri-scheme")
    override val routingPolicy: RoutingPolicy = RoutingPolicy(config.get[String]("lagom.discovery.consul.routing-policy"))
  }

}

object RoutingPolicy {
  def apply(policy: String): RoutingPolicy = policy match {
    case "first" => First
    case "random" => Random
    case "round-robin" => RoundRobin
    case unknown => throw new BadValue("lagom.discovery.consul.routing-policy", s"[$unknown] is not a valid routing algorithm")
  }
}

sealed trait RoutingPolicy

case object First extends RoutingPolicy

case object Random extends RoutingPolicy

case object RoundRobin extends RoutingPolicy
