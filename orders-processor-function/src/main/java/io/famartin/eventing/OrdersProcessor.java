package io.famartin.eventing;

import java.time.Duration;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;

import javax.inject.Inject;

import org.jboss.logging.Logger;

import io.famartin.warehouse.common.EventsService;
import io.famartin.warehouse.common.OrderRecord;
import io.quarkus.funqy.Funq;
import io.quarkus.funqy.knative.events.CloudEventMapping;

public class OrdersProcessor {
    private static final Logger log = Logger.getLogger(OrdersProcessor.class);

    private Random random = new Random();

    @Inject
    EventsService events;

    @Inject
    StocksService stocks;

    @Funq
    @CloudEventMapping(trigger = "dev.knative.kafka.event", responseSource = "orders-service", responseType = "v1.warehouse.processed-orders")
    public OrderRecord process(OrderRecord order) {
        if (order.getOrderId() == null ) {
            order.setOrderId(UUID.randomUUID().toString());
        }
        log.info("Processing order "+order.getOrderId());
        events.sendEvent("Processing order "+order.getOrderId());
        return stocks.requestStock(order.getOrderId(), order.getItemId(), order.getQuantity())
            .map(result -> {
                longRunningOperation();
                order.setProcessingTimestamp(Instant.now().toString());
                order.setProcessedBy(events.getServiceName());
                if (result.getError() != null) {
                    order.setError(result.getError());
                    order.setApproved(false);
                } else if (result.getApproved()) {
                    order.setApproved(true);
                } else {
                    order.setApproved(false);
                    order.setReason(result.getMessage());
                }
                events.sendEvent("Order " + order.getOrderId() + " processed");
                return order;
            })
            .await().atMost(Duration.ofSeconds(5));
    }

    private void longRunningOperation() {
        try {
            Thread.sleep(random.nextInt(3000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
