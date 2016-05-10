#  Lagom Service Locator for Consul

**DISCLAIMER: This is work in progress. This code has never been used in anger. Use it as a starting point and adapt it as-needed. I'd be happy to take pull requests.**

This project implements the [Lagom](http://lightbend.com/lagom) `ServiceLocator` interface for [Consul](https://www.consul.io) and provides a Consul-based service registry for registering and unregistering service from within the services.

## Register service locator in Lagom

To use it the first step is to register the service locator in Lagom by using Guice, see `ConsulServiceLocatorModule`. It is enabled in the `reference.conf ` file:
```
# Enables the `ConsulServiceLocatorModule` to register the `ConsulServiceLocator`.
# The `ConsulServiceLocator` implements Lagom's ServiceLocator
play.modules.enabled += "com.lightbend.lagom.discovery.consul.ConsulServiceLocatorModule"
```

This service locator is only enabled during `Prod` mode, during `Dev` mode the regular development service locator is used.

## Configuration

An `application.conf` file needs to be created in `src/main/resources` with the following contents:

```
lagom {
  discovery { 
    consul {
      hostname = "localhost"
      scheme = "http"
    }
  }
}
```

## Register services in Consul

The second step is to register each of your services in Consul. This can be done either directly using the [Consul Java API](https://github.com/Ecwid/consul-api) library. Here is some example code of how to use it in a service:

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

