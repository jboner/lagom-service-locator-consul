package com.lightbend.lagom.discovery.consul

import javax.inject.Inject
import javax.inject.Singleton
import com.typesafe.config.ConfigException.BadValue

import play.api.Configuration

trait ConsulConfig {
  def agentHostname: String
  def agentPort: Int
  def scheme: String
  def routingPolicy: RoutingPolicy
  def serviceName: String
  def serviceId: String
  def serviceAddress: String
  def servicePort: Int
}

object ConsulConfig {

  @Singleton
  class ConsulConfigImpl @Inject()(config: Configuration) extends ConsulConfig {
    override val agentHostname = config.getString("lagom.discovery.consul.agent-hostname").get
    override val agentPort = config.getInt("lagom.discovery.consul.agent-port").get
    override val scheme = config.getString("lagom.discovery.consul.uri-scheme").get
    override val routingPolicy = RoutingPolicy(config.getString("lagom.discovery.consul.routing-policy").get)
    override val serviceName = config.getString("lagom.register.serviceName").get
    override val serviceId = config.getString("lagom.register.serviceId").get
    override val serviceAddress = config.getString("lagom.register.serviceAddress").get
    override val servicePort = config.getInt("lagom.register.servicePort").get
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
