package io.famartin.warehouse.common;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;
import io.cloudevents.http.restful.ws.CloudEventsProvider;
import io.vertx.core.json.JsonObject;

/**
 * EventsService
 */
@ApplicationScoped
public class EventsService {

    private static final Logger log = Logger.getLogger(EventsService.class);

    @Inject
    @ConfigProperty(name = "quarkus.application.name", defaultValue = "")
    String appName;

    String podName = System.getenv("POD_NAME");

    public String getServiceName() {
        return Optional.ofNullable(podName)
            .orElseGet(() -> appName + "-" + UUID.randomUUID().toString());
    }

    // @Inject
    // @Channel("events")
    // Emitter<JsonObject> events;

    @Inject
    @Named("cloudevents")
    WebTarget webTarget;

    public void sendEvent(String event) {
        JsonObject msg = new JsonObject();
        msg.put("timestamp", Instant.now().toString());
        msg.put("from", getServiceName());
        msg.put("event", event);

        sendCloudEvent(CloudEventsBrokerClient.WAREHOUSE_GENERIC_EVENT, msg);
    }

    public void sendStockEvent(String itemId, Integer stock) {
        JsonObject msg = new JsonObject();
        msg.put("timestamp", Instant.now().toString());
        msg.put("from", getServiceName());
        msg.put("type", "stock");
        msg.put("event", new JsonObject().put("itemId", itemId).put("stock", stock));

        sendCloudEvent(CloudEventsBrokerClient.WAREHOUSE_GENERIC_EVENT, msg);
    }

    public void sendNewOrderEvent(OrderRecord order) {
        sendCloudEvent(CloudEventsBrokerClient.WAREHOUSE_ORDER_EVENT, JsonObject.mapFrom(order));
    }

    private void sendCloudEvent(String type, JsonObject data) {
        CloudEvent ce = CloudEventBuilder.v1()
            .withId(UUID.randomUUID().toString())
            .withType(type)
            .withSource(URI.create("http://warehouse.io"))
            .withData("application/json", data.toBuffer().getBytes())
            .build();

        Response res = webTarget
            .request()
            .buildPost(Entity.entity(ce, CloudEventsProvider.CLOUDEVENT_TYPE))
            .invoke();

        log.info("CloudEvent sent, status "+res.getStatus());
    }

}