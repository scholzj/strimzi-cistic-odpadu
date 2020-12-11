# Strimzi Čistič Odpadů (Drain Cleaner)

**NOTE: This is not official Strimzi project!**

Strimzi Drain Cleaner is an utility which helps with moving the Kafka pods deployed by [Strimzi](https://strimzi.io/) from nodes which are being drained.
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
* Drain the node with some Kafka or Zookeeper pods

## How does it work?

Strimzi Drain Cleaner is watching your Kubernetes cluster and when it finds out that a node is unschedulable, it will check if any Strimzi Kafka and Zookeeper pods are running on it.
In case they are, it will annotate them with the `strimzi.io/manual-rolling-update` annotation which will tell Strimzi Cluster Operator that this pod needs to be restarted.
Strimzi will roll it in the next reconciliation using its algorithms which make sure the cluster is available.
**This is supported from Strimzi 0.21.0.**

There is no easy way to find out that a node is being drained.
Using the `unschedulable` field is not the best solution because it would move pods any time when the node is marked as unschedulable.
This happens for example when you just cordon the node without draining it.
So be careful about this.

[![Strimzi Drain Cleaner demo](https://img.youtube.com/vi/nLC6KPALkE4/0.jpg)](https://www.youtube.com/watch?v=nLC6KPALkE4)

## Deployment

You can use the YAML files from the `deploy` directory to deploy Strimzi Drain Cleaner into your Kubernetes cluster.
First, edit the ClusterRoleBinding file and change the service account namespace to the namespace into which you plan to deploy it.
Then, apply all the files:

```
kubectl apply -f ./deploy
```

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
