## Commands

//infra

./scripts/install_kind
make kind-start
make init-cluster
make install-serving
make install-strimzi kafka-deployment
make install-eventing
make eventing-kafka-broker
make eventing-kafka-source



//app

deploy warehouse service
deploy ksvc orders-service
deploy kafka source orders topic to orders-service


test kafka 
kubectl -n warehouse run kafka-producer -ti --image=strimzi/kafka:0.13.0-kafka-2.3.0 --rm=true --restart=Never -- bin/kafka-console-producer.sh --broker-list warehouse-kafka-kafka-bootstrap:9092 --property "parse.key=true" --property "key.separator=:" --topic orders-topic
> test-key:{"itemId":"aaaaconsole", quantity: 4}