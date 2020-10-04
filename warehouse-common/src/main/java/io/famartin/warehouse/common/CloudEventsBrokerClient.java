package io.famartin.warehouse.common;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Named;
import javax.ws.rs.client.WebTarget;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;

@ApplicationScoped
public class CloudEventsBrokerClient {
    
    public static final String WAREHOUSE_ORDER_EVENT = "warehouse.order.v1";
    public static final String WAREHOUSE_PROCESSED_ORDER_EVENT = "warehouse.processed-order.v1";
    public static final String WAREHOUSE_GENERIC_EVENT = "warehouse.generic-event.v1";

    @ConfigProperty(name = "eventing.broker.url")
    String cloudeventsBrokerUrl;

    @Produces
    @Named("cloudevents")
    public WebTarget cloudEventsClient() {
        return ResteasyClientBuilder.newClient().target(cloudeventsBrokerUrl);
    }

}