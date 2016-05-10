package com.lightbend.lagom.discovery.consul;

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
