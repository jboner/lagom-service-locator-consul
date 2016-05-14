package com.lightbend.lagom.discovery.consul

import java.net.{InetAddress, URI}
import java.util.Optional
import java.util.concurrent.TimeUnit

import com.ecwid.consul.v1.{QueryParams, ConsulClient}
import com.ecwid.consul.v1.agent.model.NewService
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

import scala.collection.JavaConverters._
import play.api.Configuration
import play.api.Environment

class ConsulServiceDiscoverySpec extends WordSpecLike with Matchers {
  val config = Configuration.load(Environment.simple())
  val testTimeoutInSeconds: Long = 5
  val localAddress = InetAddress.getLoopbackAddress.getHostAddress

  def withServiceDiscovery(testCode: ConsulServiceLocator => ConsulClient => Any): Unit = {
    import scala.concurrent.ExecutionContext.Implicits._
    val client = new ConsulClient("localhost")
    val locator = new ConsulServiceLocator(client, new ConsulConfig.ConsulConfigImpl(config))
    testCode(locator)(client)
  }

  "A Consul-based service locator" should {
    "allow lookup of a registered service" in withServiceDiscovery { locator => client =>
      val service1 = new NewService
      service1.setName("service-1")
      service1.setPort(9001)
      service1.setId("service-1-1")
      client.agentServiceRegister(service1)

      val registeredUrl = locator.locate("service-1").toCompletableFuture.get(
        testTimeoutInSeconds, TimeUnit.SECONDS
      )
      val expectedUrl = Optional.of(new URI(s"http://$localAddress:9001"))
      expectedUrl shouldBe registeredUrl

      client.agentServiceDeregister("service-instance-1-1")
    }

    "allow lookup of a service even if the same instance has been registered twice" in withServiceDiscovery { locator => client =>
      val service1 = new NewService
      service1.setName("service-2")
      service1.setPort(9002)
      service1.setId("service-2-1")
      client.agentServiceRegister(service1)
      val service2 = new NewService
      service2.setName("service-2")
      service2.setPort(9002)
      service1.setId("service-2-2")
      client.agentServiceRegister(service2)
      Thread.sleep(500)

      val registeredUrl = locator.locate("service-2").toCompletableFuture.get(
        testTimeoutInSeconds, TimeUnit.SECONDS
      )
      val expectedUrl = Optional.of(new URI(s"http://$localAddress:9002"))
      expectedUrl shouldBe registeredUrl

      client.agentServiceDeregister("service-instance-2-1")
      client.agentServiceDeregister("service-instance-2-2")
    }

    "allow lookup of one service instance out of many" in withServiceDiscovery { locator => client =>
      val service1 = new NewService
      service1.setName("service-3")
      service1.setId("service-instance-3-1")
      service1.setPort(9003)
      client.agentServiceRegister(service1)
      val service2 = new NewService
      service2.setName("service-3")
      service2.setId("service-instance-3-2")
      service2.setPort(9004)
      client.agentServiceRegister(service2)
      Thread.sleep(500)

      val registeredUrl = locator.locate("service-2").toCompletableFuture.get(
        testTimeoutInSeconds, TimeUnit.SECONDS
      )
      val expectedUrl = Optional.of(new URI(s"http://$localAddress:9002"))
      expectedUrl shouldBe registeredUrl

      client.agentServiceDeregister("service-instance-3-1")
      client.agentServiceDeregister("service-instance-3-2")
    }

    "return CompletableFuture[Empty] for lookup of services that aren't registered" in withServiceDiscovery { locator => client =>
      val registeredUrl = locator.locate("non-existing-service").toCompletableFuture.get(
        testTimeoutInSeconds, TimeUnit.SECONDS
      )
      registeredUrl shouldBe Optional.empty[URI]()
    }

    "allow round-robin routing of a service during a static set of services" in withServiceDiscovery { locator => client =>
      val service1 = new NewService
      service1.setName("service-4")
      service1.setId("service-instance-4-1")
      service1.setPort(9005)
      client.agentServiceRegister(service1)
      val service2 = new NewService
      service2.setName("service-4")
      service2.setId("service-instance-4-2")
      service2.setPort(9006)
      client.agentServiceRegister(service2)
      val service3 = new NewService
      service3.setName("service-4")
      service3.setId("service-instance-4-3")
      service3.setPort(9007)
      client.agentServiceRegister(service3)
      Thread.sleep(500)

      val services = client.getCatalogService("service-4", QueryParams.DEFAULT).getValue.asScala.toList
      services.size shouldBe 3

      val serviceURI1 = locator.pickRoundRobinInstance("service-4", services)
      serviceURI1.getHost shouldBe localAddress
      serviceURI1.getPort shouldBe 9005

      val serviceURI2 = locator.pickRoundRobinInstance("service-4", services)
      serviceURI2.getHost shouldBe localAddress
      serviceURI2.getPort shouldBe 9006

      val serviceURI3 = locator.pickRoundRobinInstance("service-4", services)
      serviceURI3.getHost shouldBe localAddress
      serviceURI3.getPort shouldBe 9007

      val serviceURI4 = locator.pickRoundRobinInstance("service-4", services)
      serviceURI4.getHost shouldBe localAddress
      serviceURI4.getPort shouldBe 9005

      client.agentServiceDeregister("service-instance-4-1")
      client.agentServiceDeregister("service-instance-4-2")
      client.agentServiceDeregister("service-instance-4-3")
    }

    "allow round-robin routing of a service while adding a service" in withServiceDiscovery { locator => client =>
      val service1 = new NewService
      service1.setName("service-5")
      service1.setId("service-instance-5-1")
      service1.setPort(9008)
      client.agentServiceRegister(service1)
      val service2 = new NewService
      service2.setName("service-5")
      service2.setId("service-instance-5-2")
      service2.setPort(9009)
      client.agentServiceRegister(service2)
      Thread.sleep(500)

      val services1 = client.getCatalogService("service-5", QueryParams.DEFAULT).getValue.asScala.toList
      services1.size shouldBe 2

      val serviceURI1 = locator.pickRoundRobinInstance("service-5", services1)
      serviceURI1.getHost shouldBe localAddress
      serviceURI1.getPort shouldBe 9008

      val serviceURI2 = locator.pickRoundRobinInstance("service-5", services1)
      serviceURI2.getHost shouldBe localAddress
      serviceURI2.getPort shouldBe 9009

      val serviceURI3 = locator.pickRoundRobinInstance("service-5", services1)
      serviceURI3.getHost shouldBe localAddress
      serviceURI3.getPort shouldBe 9008

      val service3 = new NewService
      service3.setName("service-5")
      service3.setId("service-instance-5-3")
      service3.setPort(9010)
      client.agentServiceRegister(service3)
      Thread.sleep(500)

      val services2 = client.getCatalogService("service-5", QueryParams.DEFAULT).getValue.asScala.toList
      services2.size shouldBe 3

      val serviceURI4 = locator.pickRoundRobinInstance("service-5", services2)
      serviceURI4.getHost shouldBe localAddress
      serviceURI4.getPort shouldBe 9009

      val serviceURI5 = locator.pickRoundRobinInstance("service-5", services2)
      serviceURI5.getHost shouldBe localAddress
      serviceURI5.getPort shouldBe 9010

      client.agentServiceDeregister("service-instance-5-1")
      client.agentServiceDeregister("service-instance-5-2")
      client.agentServiceDeregister("service-instance-5-3")
    }

    "allow round-robin routing of a service while removing a service" in withServiceDiscovery { locator => client =>
      val service1 = new NewService
      service1.setName("service-6")
      service1.setId("service-instance-6-1")
      service1.setPort(9011)
      client.agentServiceRegister(service1)
      val service2 = new NewService
      service2.setName("service-6")
      service2.setId("service-instance-6-2")
      service2.setPort(9012)
      client.agentServiceRegister(service2)
      val service3 = new NewService
      service3.setName("service-6")
      service3.setId("service-instance-6-3")
      service3.setPort(9013)
      client.agentServiceRegister(service3)
      Thread.sleep(500)

      val services1 = client.getCatalogService("service-6", QueryParams.DEFAULT).getValue.asScala.toList
      services1.size shouldBe 3

      val serviceURI1 = locator.pickRoundRobinInstance("service-6", services1)
      serviceURI1.getHost shouldBe localAddress
      serviceURI1.getPort shouldBe 9011

      client.agentServiceDeregister("service-instance-6-3")
      Thread.sleep(500)

      val services2 = client.getCatalogService("service-6", QueryParams.DEFAULT).getValue.asScala.toList
      services2.size shouldBe 2

      val serviceURI2 = locator.pickRoundRobinInstance("service-6", services2)
      serviceURI2.getHost shouldBe localAddress
      serviceURI2.getPort shouldBe 9012

      val serviceURI3 = locator.pickRoundRobinInstance("service-6", services2)
      serviceURI3.getHost shouldBe localAddress
      serviceURI3.getPort shouldBe 9011

      client.agentServiceDeregister("service-instance-6-1")
      client.agentServiceDeregister("service-instance-6-2")
    }

    "allow random routing of a service" in withServiceDiscovery { locator => client =>
      val service1 = new NewService
      service1.setName("service-7")
      service1.setId("service-instance-7-1")
      service1.setPort(9014)
      client.agentServiceRegister(service1)
      val service2 = new NewService
      service2.setName("service-7")
      service2.setId("service-instance-7-2")
      service2.setPort(9015)
      client.agentServiceRegister(service2)
      val service3 = new NewService
      service3.setName("service-7")
      service3.setId("service-instance-7-3")
      service3.setPort(9016)
      client.agentServiceRegister(service3)
      Thread.sleep(500)

      val services = client.getCatalogService("service-7", QueryParams.DEFAULT).getValue.asScala.toList
      services.size shouldBe 3

      locator.pickRandomInstance(services).getHost shouldBe localAddress
      locator.pickRandomInstance(services).getHost shouldBe localAddress
      locator.pickRandomInstance(services).getHost shouldBe localAddress

      client.agentServiceDeregister("service-instance-7-1")
      client.agentServiceDeregister("service-instance-7-2")
      client.agentServiceDeregister("service-instance-7-3")
    }

    "allow routing to first instance of a service" in withServiceDiscovery { locator => client =>
      val service1 = new NewService
      service1.setName("service-8")
      service1.setId("service-instance-8-1")
      service1.setPort(9017)
      client.agentServiceRegister(service1)
      val service2 = new NewService
      service2.setName("service-8")
      service2.setId("service-instance-8-2")
      service2.setPort(9018)
      client.agentServiceRegister(service2)
      val service3 = new NewService
      service3.setName("service-8")
      service3.setId("service-instance-8-3")
      service3.setPort(9019)
      client.agentServiceRegister(service3)
      Thread.sleep(500)

      val services = client.getCatalogService("service-8", QueryParams.DEFAULT).getValue.asScala.toList
      services.size shouldBe 3

      val serviceURI1 = locator.pickFirstInstance(services)
      serviceURI1.getHost shouldBe localAddress
      serviceURI1.getPort shouldBe 9017

      val serviceURI2 = locator.pickFirstInstance(services)
      serviceURI2.getHost shouldBe localAddress
      serviceURI2.getPort shouldBe 9017

      client.agentServiceDeregister("service-instance-8-1")
      client.agentServiceDeregister("service-instance-8-2")
      client.agentServiceDeregister("service-instance-8-3")
    }
  }
}
