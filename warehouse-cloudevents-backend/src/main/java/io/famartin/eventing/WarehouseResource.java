package io.famartin.eventing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.famartin.warehouse.common.EventsService;
import io.famartin.warehouse.common.OrderRecord;
import io.famartin.warehouse.common.StockRecord;
import io.famartin.warehouse.common.WarehouseEventRecord;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;

@ApplicationScoped
@Path("/api")
public class WarehouseResource {
 
    @Inject
    CloudEventsReceiver cloudEvents;

    @Inject
    StocksService stocks;
    
    @Inject
    KubernetesClient kubernetesClient;
    
    @Inject
    EventsService eventsService;

    private ObjectMapper mapper = new ObjectMapper();

    @GET
    @Path("/status")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> status() {
        return Multi.createBy().merging()
        .streams(
            cloudEvents.genericEventsMulti()
                .filter(e -> e.getString("type", "").equals("stock"))
                .map(e -> {
                    WarehouseEventRecord r = new WarehouseEventRecord();
                    r.setEventId(UUID.randomUUID().toString());
                    r.setEventType("STOCK");
                    r.setItemId(e.getJsonObject("event").getString("itemId"));
                    r.setQuantity(e.getJsonObject("event").getInteger("stock"));
                    r.setProcessedBy(e.getString("from"));
                    r.setTimestamp(e.getString("timestamp"));
                    r.setMessage("Stock updated");
                    return r;
                })
                .map(this::uncheckedSerialization),
            cloudEvents.processedOrdersMulti()
                .map(o -> {
                    WarehouseEventRecord r = new WarehouseEventRecord();
                    r.setEventId(o.getOrderId());
                    r.setEventType("ORDER");
                    r.setItemId(o.getItemId());
                    r.setQuantity(o.getQuantity());
                    r.setProcessedBy(o.getProcessedBy());
                    r.setTimestamp(o.getProcessingTimestamp());
                    r.setMessage(o.getApproved() ? "Approved" : "Rejected. "+o.getReason());
                    return r;
                })
                .map(this::uncheckedSerialization),
            cloudEvents.getPingStream()
        );
    }

    private String uncheckedSerialization(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GET
    @Path("/stocks")
    @Produces(MediaType.APPLICATION_JSON)
    public List<StockRecord> currentStocks() {
        return stocks.status();
    }

    @POST
    @Path("/stocks")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Uni<StockRecord> addStock(StockRecord request) {
        return stocks.addStock(UUID.randomUUID().toString(), request.getItemId(), request.getQuantity());
    }

    @GET
    @Path("/cloudmeta")
    @Produces(MediaType.APPLICATION_JSON)
    public JsonObject getCloudMetadata() {
        // String nodeName = System.getenv("CURRENT_NODE_NAME");
        Node node = kubernetesClient.nodes().list().getItems().get(0);
        String providerId = node.getSpec().getProviderID();
        if (providerId != null) {
            providerId = providerId.split(":")[0];
            String zone = node.getMetadata().getLabels().get("failure-domain.beta.kubernetes.io/zone");
            if (zone != null) {
                zone = zone.toLowerCase();
            }
            return new JsonObject().put("cloud", providerId).put("zone", zone).put("pod", "warehouse-backend-cloudevents");
        } else {
            String zone = node.getMetadata().getAnnotations().get("warehouse.zone");
            return new JsonObject().put("cloud", "Baremetal").put("zone", zone).put("pod", "warehouse-backend-cloudevents");
        }
    }

    @Path("/orders")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response request(@NotNull OrderRecord order) {
        order.setOrderId(UUID.randomUUID().toString());
        if(isValid(order)) {
            eventsService.sendNewOrderEvent(order);
            eventsService.sendEvent(String.format("Order %s enqueued", order.getOrderId()));
            return Response.ok(order).build();
        } else {
            eventsService.sendEvent(String.format("Invalid order %s", order.toString()));
            return Response.status(Status.BAD_REQUEST).build();
        }
    }

    private boolean isValid(OrderRecord order) {
        try {
            return order != null && order.getOrderId()!=null
            && order.getItemId()!=null && order.getQuantity()!=null && order.getQuantity()>0;
        } catch (RuntimeException e) {
            return false;
        }
    }

}