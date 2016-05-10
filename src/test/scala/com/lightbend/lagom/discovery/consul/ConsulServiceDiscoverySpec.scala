package com.lightbend.lagom.discovery.consul

import java.net.URI
import java.util.Optional
import java.util.concurrent.TimeUnit

import com.ecwid.consul.v1.ConsulClient
import com.ecwid.consul.v1.agent.model.NewService
import org.scalatest.Matchers
import org.scalatest.WordSpecLike

class ConsulServiceDiscoverySpec extends WordSpecLike with Matchers {
  val testTimeoutInSeconds = 5

  def withServiceDiscovery(testCode: ConsulServiceLocator => ConsulClient => Any): Unit = {
    import scala.concurrent.ExecutionContext.Implicits._
    val client = new ConsulClient("localhost")
    val locator = new ConsulServiceLocator
    testCode(locator)(client)
  }

  "A Consul-based service locator" should {
    "allow lookup of a registered service" in withServiceDiscovery { locator => client =>
      val service = new NewService
      service.setName("service-1")
      service.setPort(9001)
      client.agentServiceRegister(service)

      val registeredUrl = locator.locate("service-1").toCompletableFuture.get(
        testTimeoutInSeconds, TimeUnit.SECONDS
      )
      val expectedUrl = Optional.of(new URI("http://:9001"))
      expectedUrl shouldBe registeredUrl
    }

    "allow lookup of a service even if it has been registered twice" in withServiceDiscovery { locator => client =>
      val service1 = new NewService
      service1.setName("service-2")
      service1.setPort(9002)
      client.agentServiceRegister(service1)
      val service2 = new NewService
      service2.setName("service-2")
      service2.setPort(9002)
      client.agentServiceRegister(service2)

      Thread.sleep(500)
      val registeredUrl = locator.locate("service-2").toCompletableFuture.get(
        testTimeoutInSeconds, TimeUnit.SECONDS
      )
      val expectedUrl = Optional.of(new URI("http://:9002"))
      expectedUrl shouldBe registeredUrl
    }

    "return CompletableFuture[Empty] for lookup of services that aren't registered" in withServiceDiscovery { locator => client =>
      val registeredUrl = locator.locate("non-existing-service").toCompletableFuture.get(
        testTimeoutInSeconds, TimeUnit.SECONDS
      )
      registeredUrl shouldBe Optional.empty[URI]()
    }
  }
}
