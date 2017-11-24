package com.lightbend.lagom.scaladsl.discovery.consul

import akka.actor.ActorSystem
import com.ecwid.consul.v1.ConsulClient
import com.lightbend.lagom.internal.spi.CircuitBreakerMetricsProvider
import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.client.CircuitBreakerComponents
import play.api.Configuration

import scala.concurrent.ExecutionContext

/**
  * This module binds the ServiceLocator interface from Lagom to the `ConsulServiceLocator`.
  * The `ConsulServiceLocator` is only bound if the application has been started in `Prod` mode.
  * In `Dev` mode the embedded service locator of Lagom is used.
  */
trait ConsulServiceLocatorComponents extends CircuitBreakerComponents {
  def actorSystem: ActorSystem
  def configuration: Configuration
  def executionContext: ExecutionContext
  def circuitBreakerMetricsProvider: CircuitBreakerMetricsProvider



  lazy val consulConfig = new ConsulConfig.ConsulConfigImpl(configuration)
  lazy val consulClient: ConsulClient = new ConsulClient(consulConfig.agentHostname, consulConfig.agentPort)

  lazy val serviceLocator: ServiceLocator = new ConsulServiceLocator(consulClient, consulConfig, circuitBreakers)(executionContext)
}

