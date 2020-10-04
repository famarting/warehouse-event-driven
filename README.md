# Warehouse example application with Knative Eventing

This repository contains an example microservices application for orders processing in a warehouse, with the purpose of trying and showing different technologies. This is a high level diagram showcasing how the application works

![app diagram](/img/diagram.png)

## How it's implemented?

The main feature of this application is it's event driven nature, implemented in a way that makes all the microservices composing the application agnostic to the messaging layer they are working with. Technologies such as Knative Eventing, Apache Kafka, Quarkus, among others are being used.

[Knative Eventing](https://knative.dev/docs/eventing) provides the platform to build decoupled systems on top of it. All the microservices in this application only speak [CloudEvents](https://cloudevents.io/) via http, Knative Eventing handles the routing of messages sent to it's messaging layer.

[Apache Kafka](https://kafka.apache.org/), deployed in Kubernetes using [Strimzi](https://strimzi.io/), is used by Knative Eventing as the broker that backs the messaging layer.

[Quarkus](https://quarkus.io/) is used to implement all the microservices in this application, as stated all microservices only speak CloudEvents via http so development of the microservices aims to be very simple. CloudEvents SDK is being used to implement CloudEvents aware aplications that send (to Knative Eventing Broker, backed by Apache Kafka) or receive (being invoked by Knative) CloudEvents messages. CloudEvents is used because of Knative and for messaging based communication patterns, for point to point communication (orders-processor to stocks-service in the example) Microprofile Rest Client is being used.

## How to run it?

You need a Kubernetes or Openshift cluster up and running.
If you don't have one you can use Kind.
You can install Kind running the script:
```
./scripts/install_kind.sh
```
Then create a Kind cluster:
```
make kind-start
```

Then to install the required infrastructure to run the application:
```
make setup-knative
```
Then wait until the kafka cluster is deployed, you can check the status with
```
kubectl get pod -n kafka
```
Once the kafka cluster is running please execute:
```
make eventing-kafka-broker
```

Once all the requirements are satisfied you can deploy the application:
```
kubectl apply -n warehouse -f kubefiles/orders-processor
kubectl apply -n warehouse -f kubefiles/stocks-service
kubectl apply -n warehouse -f kubefiles/warehouse-service
```

Then if you are using a Kind cluster run:
```
kubectl apply -n warehouse -f kubefiles/warehouse-service/optional/kind_warehouse_ingress.yaml
```
With this you'll be able to see the warehouse web in 
```
http://localhost/
```