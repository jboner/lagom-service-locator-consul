#  Lagom Service Locator for Consul

**DISCLAIMER: This is work in progress. This code has never been used in anger. Use it as a starting point and adapt it as-needed. I'm happy to take pull requests.**

This project implements the [Lagom](http://lightbend.com/lagom) `ServiceLocator` interface for [Consul](https://www.consul.io) and provides a Consul-based service registry for registering and unregistering service from within the services.

## Register service locator in Lagom

To use it the first step is to register the service locator in Lagom by using Guice, see `ConsulServiceLocatorModule`. It is enabled in the `reference.conf` file:
```
# Enables the ConsulServiceLocatorModule to register the ConsulServiceLocator.
# The ConsulServiceLocator implements Lagom's ServiceLocator
play.modules.enabled += "com.lightbend.lagom.discovery.consul.ConsulServiceLocatorModule"
```

This service locator is only enabled during `Prod` mode, during `Dev` mode the regular development service locator is used.
When you are using this library then you should *not* use the `sbt-conductr` sbt plugin. 

## Routing to service instances

The `ConsulServiceLocator` has support for three simple routing policies: 
* `first`: picks the first service instance in a sorted list—sorted by IP-address and port
* `random`: picks a random service instance
* `round-robin`: performs a round-robin routing between the currently available service instances

## Configuration

An `application.conf` file needs to be created in `src/main/resources` with the following contents:

```
lagom {
  discovery {
    consul {
      agent-hostname = "127.0.0.1"   # hostname or IP-address for the Consul agent
      agent-port     = 8500          # port for the Consul agent
      uri-scheme     = "http"        # for example: http or https
      routing-policy = "round-robin" # valid routing policies: first, random, round-robin
    }
  }
}
```

## Register services in Consul

The second step is to register each of your services in Consul. This can be done using the [Consul Java API](https://github.com/Ecwid/consul-api) library or through Consul's [HTTP API](https://www.consul.io/intro/getting-started/services.html). Here is some example code of how to use it in a service:

```java
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.agent.model.NewService;

/**
 * This shows a very simplified method of registering an instance with the service discovery. Each individual
 * instance in your distributed set of applications would create an instance of something similar to ExampleServer,
 * start it when the application comes up and close it when the application shuts down.
 */
public class ExampleService {
    final ConsulClient client;
    final NewService service;

    public ExampleService(
            String serviceName,
            String serviceId,
            String serviceAddress,
            int servicePort,
            String consulHostname) throws Exception {
        service = new NewService();
        service.setId(serviceId);
        service.setName(serviceName);
        service.setPort(servicePort);
        service.setAddress(serviceAddress);

        client = new ConsulClient(consulHostname);
        client.agentServiceRegister(service);
    }

    public void stop() {
        client.agentServiceDeregister(service.getId());
    }

    public static void main(String[] args) throws Exception {
        String consulHostname= "localhost";
        String serviceName = "testService";
        String serviceId = "uniqueId";
        String serviceAddress = "localhost";
        int servicePort = 9000;

        ExampleService service = new ExampleService(
                serviceName, serviceId, serviceAddress, servicePort, consulHostname);

        service.stop();
    }
}
```

## How to run the tests

You need a Consul agent running on your local machine (on its default port) in order to run the tests. 

If you are on Mac then you can install Consul through Homebrew using `brew install consul`. Once it is installed you can start up an agent in dev mode by invoking `consul agent -dev -data-dir ~/tmp` which will make an agent available on 127.0.0.1:8500.

Once Consul is running you can run the tests by invoking `sbt test`.
