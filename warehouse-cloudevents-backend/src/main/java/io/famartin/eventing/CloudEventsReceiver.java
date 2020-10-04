package io.famartin.eventing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;

import javax.enterprise.context.ApplicationScoped;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.jboss.logging.Logger;

import io.cloudevents.CloudEvent;
import io.famartin.warehouse.common.CloudEventsBrokerClient;
import io.famartin.warehouse.common.OrderRecord;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
@Path("/cloudevents")
public class CloudEventsReceiver {

    private static final Logger log = Logger.getLogger(CloudEventsReceiver.class);

    private BroadcastProcessor<OrderRecord> processedOrders = BroadcastProcessor.create();
    private BroadcastProcessor<JsonObject> genericEvents = BroadcastProcessor.create();

    private ObjectMapper mapper = new ObjectMapper();

    @POST
    public void cloudeventsHandler(CloudEvent event) {
        log.info("Received cloud event of type "+event.getType());
        log.info(new String(event.getData()));
        if (event.getType().equals(CloudEventsBrokerClient.WAREHOUSE_PROCESSED_ORDER_EVENT)) {
            processedOrders.onNext(toOrderRecord(event));
        } else if (event.getType().equals(CloudEventsBrokerClient.WAREHOUSE_GENERIC_EVENT)) {
            genericEvents.onNext(new JsonObject(Buffer.buffer(event.getData())));
        }
    }

    private OrderRecord toOrderRecord(CloudEvent ce) {
        try {
            return mapper.readValue(ce.getData(), OrderRecord.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Multi<OrderRecord> processedOrdersMulti() {
        return processedOrders;
    }

    public Multi<JsonObject> genericEventsMulti() {
        return genericEvents;
    }

    public Multi<String> getPingStream() {
        return Multi.createFrom().ticks().every(Duration.ofSeconds(10))
                .onItem().transform(x -> "{}");
    }

}