#!/bin/bash
# -H "Content-Type: application/json"
# curl -i -d '{"itemId":"123456", "quantity":5}' -X POST $(kubectl get ksvc -n warehouse orders-service --template='{{ .status.url }}'):9080/
curl -i -d '{"itemId":"123456", "quantity":5}' -H 'Content-Type:' -X POST http://localhost:8080/process