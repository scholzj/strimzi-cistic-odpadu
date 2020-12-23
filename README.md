# Strimzi Čistič Odpadů (Drain Cleaner)

**NOTE: This is not official Strimzi project!**

Strimzi Drain Cleaner is utility which helps with moving the Kafka pods deployed by [Strimzi](https://strimzi.io/) from nodes which are being drained.
It is useful if you want the Strimzi operator to move the pods instead of Kubernetes itself.
The advantage of this approach is that the Strimzi operator makes sure that no pods become under-replicated during the node draining.
To use it:

* Deploy Kafka using Strimzi and configure the PodDisruptionBudgets for Kafka and Zookeeper to have `maxUnavailable` set to `0`.
This will block Kubernetes from moving the pods on their own.
  
```yaml
apiVersion: kafka.strimzi.io/v1beta1
kind: Kafka
metadata:
  name: my-cluster
spec:
  kafka:
    version: 2.6.0
    replicas: 3
    listeners:
      - name: plain
        port: 9092
        type: internal
        tls: false
      - name: tls
        port: 9093
        type: internal
        tls: true
    config:
      offsets.topic.replication.factor: 3
      transaction.state.log.replication.factor: 3
      transaction.state.log.min.isr: 2
      log.message.format.version: "2.6"
      inter.broker.protocol.version: "2.6"
    storage:
      type: jbod
      volumes:
      - id: 0
        type: persistent-claim
        size: 100Gi
        deleteClaim: false
    template:
      podDisruptionBudget:
        maxUnavailable: 0
  zookeeper:
    replicas: 3
    storage:
      type: persistent-claim
      size: 100Gi
      deleteClaim: false
    template:
      podDisruptionBudget:
        maxUnavailable: 0
  entityOperator:
    topicOperator: {}
    userOperator: {}
```

* Deploy the Strimzi Drain Cleaner
* Drain the node with some Kafka or Zookeeper pods using the `kubectl drain` command

_Note: If you change the service name or namespace, you have to update the Webhook configuration, and the certificates to match the new address._

## How does it work?

Strimzi Drain Cleaner using Kubernetes Admission Control features and Validating Webhooks to find out when something tries to evict the Kafka or Zookeeper pods are.
It annotates them with the `strimzi.io/manual-rolling-update` annotation which will tell Strimzi Cluster Operator that this pod needs to be restarted.
Strimzi will roll it in the next reconciliation using its algorithms which make sure the cluster is available.
**This is supported from Strimzi 0.21.0.**

## Deployment

You can use the YAML files from the `deploy` directory to deploy Strimzi Drain Cleaner into your Kubernetes cluster.
First, edit the ClusterRoleBinding file and change the service account namespace to the namespace into which you plan to deploy it.
Then, apply all the files:

```
kubectl apply -f ./deploy
```

If you want to use this only to Kafka and not to ZooKeeper, you can edit the Deployment and remove the `--zookeeper` option.

_Note: If you change the service name or namespace, you have to update the Webhook configuration, and the certificates to match the new address._

## Build 

This project uses [Quarkus, the Supersonic Subatomic Java Framework](https://quarkus.io/).

If you want to learn more about Quarkus, please visit its website: https://quarkus.io/ .

### Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

### Packaging and running the application

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `strimzi-cistic-odpadu-1.0.0-SNAPSHOT-runner.jar` file in the `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application is now runnable using `java -jar target/strimzi-cistic-odpadu-1.0.0-SNAPSHOT-runner.jar`.

### Creating a native executable

_Note: The native packaging does not work right now as it cannot properly deserialize the AdmissionReview request._

You can create a native executable using: 
```shell script
./mvnw package -Pnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using: 
```shell script
./mvnw package -Pnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/strimzi-cistic-odpadu-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/maven-tooling.html.
