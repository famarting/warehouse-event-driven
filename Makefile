TOPDIR=$(dir $(lastword $(MAKEFILE_LIST)))
include $(TOPDIR)/Makefile.env.mk

SERVICES = orders-processor-function

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

setup: init-cluster install-eventing install-strimzi kafka-deployment kafka-channel

init-cluster:
	kubectl create namespace warehouse
	kubectl ns warehouse

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

install-strimzi:
	wget https://github.com/strimzi/strimzi-kafka-operator/releases/download/0.18.0/strimzi-cluster-operator-0.18.0.yaml
	sed -i 's/namespace: .*/namespace: warehouse/' strimzi-cluster-operator-0.18.0.yaml
	kubectl apply -f strimzi-cluster-operator-0.18.0.yaml -n warehouse

kafka-deployment:
	kubectl apply -f kubefiles/kafka.yaml

install-eventing:
	kubectl apply --filename https://github.com/knative/eventing/releases/download/v0.18.0/eventing-crds.yaml
	kubectl apply --filename https://github.com/knative/eventing/releases/download/v0.18.0/eventing-core.yaml

eventing-kafka-broker:
	curl -L "https://github.com/knative-sandbox/eventing-kafka-broker/releases/download/v0.18.0/eventing-kafka-controller.yaml" \
		| sed 's/my-cluster-kafka-bootstrap.kafka:9092/warehouse-kafka-kafka-bootstrap.warehouse:9092/' \
 		| kubectl apply --filename -
	kubectl apply --filename https://github.com/knative-sandbox/eventing-kafka-broker/releases/download/v0.18.0/eventing-kafka-broker.yaml
	kubectl apply -f kubefiles/eventing/infra/knative-kafka-broker.yaml
	kubectl get broker

eventing-kafka-source:
	kubectl apply -f https://github.com/knative/eventing-contrib/releases/download/v0.18.0/kafka-source.yaml

# do not use
# eventing-kafka-channel: eventing-kafka-source
# 	curl -L "https://github.com/knative/eventing-contrib/releases/download/v0.18.0/kafka-channel.yaml" \
#  		| sed 's/REPLACE_WITH_CLUSTER_URL/warehouse-kafka-kafka-bootstrap.warehouse:9092/' \
#  		| kubectl apply --filename -
# 	kubectl get pods -n knative-eventing
# 	sleep 30
# 	kubectl apply -f kubefiles/eventing/infra/knative-kafka-channel.yaml

# kafka-channel:
# 	curl -L "https://github.com/knative/eventing-contrib/releases/download/v0.18.0/kafka-channel.yaml" \
#  		| sed 's/REPLACE_WITH_CLUSTER_URL/warehouse-kafka-kafka-bootstrap.warehouse:9092/' \
#  		| kubectl apply --filename -
