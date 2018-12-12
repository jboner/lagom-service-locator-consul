package com.lightbend.lagom.scaladsl.discovery.consul

import java.net.{InetAddress, URI}

import com.ecwid.consul.v1.agent.model.NewService
import com.ecwid.consul.v1.{ConsulClient, QueryParams}
import com.lightbend.lagom.internal.client.CircuitBreakers
import com.lightbend.lagom.scaladsl.client.CircuitBreakersPanel
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpecLike}
import play.api.{Configuration, Environment}

import scala.collection.JavaConverters._

class ConsulServiceDiscoverySpec extends WordSpecLike with Matchers with ScalaFutures {
  val config = Configuration.load(Environment.simple())
  val testTimeoutInSeconds: Long = 5
  val localAddress = InetAddress.getLoopbackAddress.getHostAddress


  def withServiceDiscovery(testCode: ConsulServiceLocator => ConsulClient => Any): Unit = {
    import scala.concurrent.ExecutionContext.Implicits._
    val client = new ConsulClient("localhost")
    val locator = new ConsulServiceLocator(client, new ConsulConfig.ConsulConfigImpl(config), null)
    testCode(locator)(client)
  }

  def withRegisteredService(client: ConsulClient)(name: String, port: Int, id: String)(f: => Any): Any = {
    val service1 = new NewService
    service1.setName(name)
    service1.setPort(port)
    service1.setId(id)
    client.agentServiceRegister(service1)
    try {
      f
    } finally {
      client.agentServiceDeregister(id)
    }
  }

  "A Consul-based service locator" should {
    "allow lookup of a registered service" in withServiceDiscovery { locator =>
      client =>
        val srv1 = "scala-service-1"
        withRegisteredService(client)(srv1, 9001, s"$srv1-1") {
          val future = locator.locate(srv1)
          whenReady(future) { maybeUri =>
            val expectedUrl = Some(new URI(s"http://$localAddress:9001"))
            maybeUri shouldBe expectedUrl
          }
        }
    }

    "allow lookup of a service even if the same instance has been registered twice" in withServiceDiscovery { locator =>
      client =>
        val srv2 = "scala-service-2"
        withRegisteredService(client)(srv2, 9002, s"$srv2-1") {
          withRegisteredService(client)(srv2, 9002, s"$srv2-2") {
            Thread.sleep(500)
            val registeredUrl = locator.locate(srv2)
            whenReady(registeredUrl) { url =>
              val expectedUrl = Some(new URI(s"http://$localAddress:9002"))
              expectedUrl shouldBe url
            }
          }
        }
    }

    "allow lookup of one service instance out of many" in withServiceDiscovery { locator =>
      client =>
        val srv3 = "scala-service-3"
        withRegisteredService(client)(srv3, 9003, s"$srv3-1") {
          withRegisteredService(client)(srv3, 9004, s"$srv3-2") {
            Thread.sleep(500)
            val registeredUrl = locator.locate(srv3)
            whenReady(registeredUrl) { url =>
              val expectedUrl = Some(new URI(s"http://$localAddress:9003"))
              expectedUrl shouldBe url
            }
          }
        }
    }

    "return Future[None] for lookup of services that aren't registered" in withServiceDiscovery { locator =>
      client =>
        val registeredUrl = locator.locate("non-existing-service")

        whenReady(registeredUrl) { url =>
          url shouldBe None
        }
    }

    "allow round-robin routing of a service during a static set of services" in withServiceDiscovery { locator =>
      client =>
        val srv4 = "scala-service-4"
        withRegisteredService(client)(srv4, 9005, s"$srv4-1") {
          withRegisteredService(client)(srv4, 9006, s"$srv4-2") {
            withRegisteredService(client)(srv4, 9007, s"$srv4-3") {
              Thread.sleep(500)

              val services = client.getCatalogService(srv4, QueryParams.DEFAULT).getValue.asScala.toList
              services.size shouldBe 3

              val serviceURI1 = locator.pickRoundRobinInstance(srv4, services)
              serviceURI1.getHost shouldBe localAddress
              serviceURI1.getPort shouldBe 9005

              val serviceURI2 = locator.pickRoundRobinInstance(srv4, services)
              serviceURI2.getHost shouldBe localAddress
              serviceURI2.getPort shouldBe 9006

              val serviceURI3 = locator.pickRoundRobinInstance(srv4, services)
              serviceURI3.getHost shouldBe localAddress
              serviceURI3.getPort shouldBe 9007

              val serviceURI4 = locator.pickRoundRobinInstance(srv4, services)
              serviceURI4.getHost shouldBe localAddress
              serviceURI4.getPort shouldBe 9005
            }
          }
        }
    }

    "allow round-robin routing of a service while adding a service" in withServiceDiscovery { locator =>
      client =>
        val srv5 = "scala-service-4"
        withRegisteredService(client)(srv5, 9008, s"$srv5-1") {
          withRegisteredService(client)(srv5, 9009, s"$srv5-2") {
            Thread.sleep(500)

            val services1 = client.getCatalogService(srv5, QueryParams.DEFAULT).getValue.asScala.toList
            services1.size shouldBe 2

            val serviceURI1 = locator.pickRoundRobinInstance(srv5, services1)
            serviceURI1.getHost shouldBe localAddress
            serviceURI1.getPort shouldBe 9008

            val serviceURI2 = locator.pickRoundRobinInstance(srv5, services1)
            serviceURI2.getHost shouldBe localAddress
            serviceURI2.getPort shouldBe 9009

            val serviceURI3 = locator.pickRoundRobinInstance(srv5, services1)
            serviceURI3.getHost shouldBe localAddress
            serviceURI3.getPort shouldBe 9008

            withRegisteredService(client)(srv5, 9010, s"$srv5-3") {
              Thread.sleep(500)
              val services2 = client.getCatalogService(srv5, QueryParams.DEFAULT).getValue.asScala.toList
              services2.size shouldBe 3

              val serviceURI4 = locator.pickRoundRobinInstance(srv5, services2)
              serviceURI4.getHost shouldBe localAddress
              serviceURI4.getPort shouldBe 9009

              val serviceURI5 = locator.pickRoundRobinInstance(srv5, services2)
              serviceURI5.getHost shouldBe localAddress
              serviceURI5.getPort shouldBe 9010

            }
          }
        }
    }

    "allow round-robin routing of a service while removing a service" in withServiceDiscovery { locator =>
      client =>
        val srv6 = "scala-service-6"
        withRegisteredService(client)(srv6, 9011, s"$srv6-1") {
          withRegisteredService(client)(srv6, 9012, s"$srv6-2") {
            withRegisteredService(client)(srv6, 9013, s"$srv6-3") {

              Thread.sleep(500)

              val services1 = client.getCatalogService(srv6, QueryParams.DEFAULT).getValue.asScala.toList
              services1.size shouldBe 3

              val serviceURI1 = locator.pickRoundRobinInstance(srv6, services1)
              serviceURI1.getHost shouldBe localAddress
              serviceURI1.getPort shouldBe 9011

            }
            Thread.sleep(500)

            val services2 = client.getCatalogService(srv6, QueryParams.DEFAULT).getValue.asScala.toList
            services2.size shouldBe 2

            val serviceURI2 = locator.pickRoundRobinInstance(srv6, services2)
            serviceURI2.getHost shouldBe localAddress
            serviceURI2.getPort shouldBe 9012

            val serviceURI3 = locator.pickRoundRobinInstance(srv6, services2)
            serviceURI3.getHost shouldBe localAddress
            serviceURI3.getPort shouldBe 9011
          }
        }
    }

    "allow random routing of a service" in withServiceDiscovery { locator =>
      client =>
        val srv7 = "scala-service-7"
        withRegisteredService(client)(srv7, 9014, s"$srv7-1") {
          withRegisteredService(client)(srv7, 9015, s"$srv7-2") {
            withRegisteredService(client)(srv7, 9016, s"$srv7-3") {

              Thread.sleep(500)

              val services = client.getCatalogService(srv7, QueryParams.DEFAULT).getValue.asScala.toList
              services.size shouldBe 3

              locator.pickRandomInstance(services).getHost shouldBe localAddress
              locator.pickRandomInstance(services).getHost shouldBe localAddress
              locator.pickRandomInstance(services).getHost shouldBe localAddress
            }
          }
        }
    }

    "allow routing to first instance of a service" in withServiceDiscovery { locator =>
      client =>
        val srv8 = "scala-service-8"
        withRegisteredService(client)(srv8, 9017, s"$srv8-1") {
          withRegisteredService(client)(srv8, 9018, s"$srv8-2") {
            withRegisteredService(client)(srv8, 9019, s"$srv8-3") {
              Thread.sleep(500)

              val services = client.getCatalogService(srv8, QueryParams.DEFAULT).getValue.asScala.toList
              services.size shouldBe 3

              val serviceURI1 = locator.pickFirstInstance(services)
              serviceURI1.getHost shouldBe localAddress
              serviceURI1.getPort shouldBe 9017

              val serviceURI2 = locator.pickFirstInstance(services)
              serviceURI2.getHost shouldBe localAddress
              serviceURI2.getPort shouldBe 9017
            }
          }
        }
    }
  }
}
