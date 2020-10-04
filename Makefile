TOPDIR=$(dir $(lastword $(MAKEFILE_LIST)))
include $(TOPDIR)/Makefile.env.mk

SERVICES = orders-processor-function warehouse-cloudevents-backend stocks-service-cloudevents

container: common $(SERVICES) 

common:
	$(MAKE) -C warehouse-common mvn_install

$(SERVICES): 
	$(MAKE) -C $@ $(MAKECMDGOALS)

.PHONY: container $(SERVICES)

KIND_CLUSTER_NAME ?= "warehouse-event-driven"
ifeq (1, $(shell command -v kind | wc -l))
KIND_CMD = kind
else
ifeq (1, $(shell command -v ./kind | wc -l))
KIND_CMD = ./kind
else
$(error "No kind binary found")
endif
endif

kind-start:
ifeq (1, $(shell ${KIND_CMD} get clusters | grep ${KIND_CLUSTER_NAME} | wc -l))
	@echo "Cluster already exists" 
else
	@echo "Creating Cluster"
	${KIND_CMD} create cluster --name ${KIND_CLUSTER_NAME} --image=kindest/node:v1.17.5 --config=./scripts/kind-config.yaml
	# setup nginx ingress
	kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/master/deploy/static/provider/kind/deploy.yaml
	kubectl patch deployment ingress-nginx-controller -n ingress-nginx --type=json -p '[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--enable-ssl-passthrough"}]'

endif

kind-delete:
	${KIND_CMD} delete cluster --name ${KIND_CLUSTER_NAME}

setup-knative: init-cluster install-serving install-eventing install-strimzi kafka-deployment

init-cluster:
	kubectl create namespace warehouse

install-serving:
	kubectl apply -f https://github.com/knative/serving/releases/download/v0.18.0/serving-crds.yaml
	kubectl apply -f https://github.com/knative/serving/releases/download/v0.18.0/serving-core.yaml
	kubectl apply -f https://github.com/knative/net-kourier/releases/download/v0.18.0/kourier.yaml
	sleep 5
	kubectl apply -f ./scripts/kourier-tweak.yaml
	kubectl patch configmap/config-network \
  		-n knative-serving \
  		--type merge \
  		-p '{"data":{"ingress.class":"kourier.ingress.networking.knative.dev"}}'
	kubectl patch configmap/config-domain \
  		--namespace knative-serving \
  		--type merge \
  		--patch '{"data":{"127.0.0.1.nip.io":""}}'

install-eventing:
	kubectl apply --filename https://github.com/knative/eventing/releases/download/v0.18.0/eventing-crds.yaml
	kubectl apply --filename https://github.com/knative/eventing/releases/download/v0.18.0/eventing-core.yaml
	sleep 5

install-strimzi:
	kubectl create namespace kafka
	curl -L "https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.18.0/strimzi-cluster-operator-0.18.0.yaml" \
		| sed 's/namespace: .*/namespace: kafka/' \
		| kubectl apply -n kafka -f -
	sleep 5

kafka-deployment:
	kubectl apply -n kafka -f kubefiles/eventing/kafka.yaml
	echo "Please, wait until kafka broker is running and run \"make eventing-kafka-broker\""
	kubectl get pod -n kafka

eventing-kafka-broker:
	curl -L "https://github.com/knative-sandbox/eventing-kafka-broker/releases/download/v0.18.0/eventing-kafka-controller.yaml" \
		| sed 's/my-cluster-kafka-bootstrap.kafka:9092/eventing-broker-kafka-bootstrap.kafka:9092/' \
 		| kubectl apply --filename -
	kubectl apply --filename https://github.com/knative-sandbox/eventing-kafka-broker/releases/download/v0.18.0/eventing-kafka-broker.yaml
	sleep 5
	kubectl apply -f kubefiles/eventing/knative-kafka-broker.yaml
	kubectl get broker -n warehouse