# curl -v "http://localhost:80/broker/warehouse/default" \
#   -X POST \
#   -H "Ce-Id: say-hello" \
#   -H "Ce-Specversion: 1.0" \
#   -H "Ce-Type: greeting" \
#   -H "Ce-Source: not-sendoff" \
#   -H "Content-Type: application/json" \
#   -d '{"msg":"Hello Knative!"}'

curl -v "http://localhost:8082/" \
  -X POST \
  -H "Ce-Id: say-hello" \
  -H "Ce-Specversion: 1.0" \
  -H "Ce-Type: warehouse.processed-order.v1" \
  -H "Ce-Source: not-sendoff" \
  -H "Content-Type: application/json" \
  -d '{"orderId":"aaa", "itemId":"1111", "quantity":4, "approved":true}'