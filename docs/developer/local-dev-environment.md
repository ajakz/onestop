## Setting Up Local Dev Environment

### Requirements

1. Java 8+
1. Docker
1. [kubectl](https://kubernetes.io/docs/tasks/tools/install-kubectl/), configured to point to any...
1. Kubernetes cluster (e.g. [minikube](https://kubernetes.io/docs/tasks/tools/install-minikube/))
1. [skaffold](https://github.com/GoogleContainerTools/skaffold#installation)
    - Note: if you already have docker, kubectl, and minikube installed, you only need to do the first step in the linked installation guide
    - Note: skaffold is also available via homebrew

### Local Development 
The system uses Skaffold and Jib as a Gradle plugin to help with continuous development and deployment of Kubernetes 
applications.

How it works:

1. Creates Kubernetes configuration files for the apps
1. Deploys applications to local cluster
1. Monitors source code and automatically re-deploys when needed
1. Steams logs from your deployed pods to your local terminal

### Recommended Development Cycle using Jib for gradle and skaffold
This means that Jib will monitors and catchs any changes made to the source code and trigger a build. 
This saves a separate docker image and redeploy the application. 
```bash
skaffold dev
```
Which will:
1. Locally build docker images for each subproject
1. Deploy the k8s objects based on those images, as well as dependencies like zookeeper and kafka, to your k8s cluster
1. Set up a watch on the docker image source files which will rebuild and redeploy their corresponding images when they change

At this point, modifying a source file in a subproject will:
1. Assemble its jars
1. Build its docker image
1. Deploy its objects to the cluster

#### One-off Variant

If you would rather not have gradle and skaffold watching for every change, you can skip the `-t` option and use
`skaffold run` to deploy the system compiled outputs in the cluster once.

### Accessing Kubernetes Services

The registry api is exposed through ambassador via a LoadBalancer service.
When using docker desktop, you can access them at:

```
http://localhost/registry       # psi-registry
```

From there you can do things like upload some metadata to test the system:

```bash
curl -X PUT\
     -H "Content-Type: application/xml" \
     http://localhost/registry/metadata/collection \
     --data-binary @registry/src/test/resources/dscovr_fc1.xml
```

### Persistent Storage

The zookeeper, kafka, and registry deployments are configured to store there data in volumes allocated by
persistent volume claims. These claims remain when pods are removed and even when the deployments are deleted.
You can see the claims and their corresponding volumes them with `kubectl`, like this:

```bash
kubectl get pvc,pv
NAME                                            STATUS    VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS   AGE
persistentvolumeclaim/data-kafka-sts-0          Bound     pvc-e602da5b-5e25-11e8-9f48-080027a6b205   1Gi        RWO            standard       9d
persistentvolumeclaim/data-registry-0           Bound     pvc-dc8c32dc-65c0-11e8-8652-080027a6b205   1Gi        RWO            standard       3h
persistentvolumeclaim/data-zookeeper-sts-0      Bound     pvc-3860e2c5-5de4-11e8-9f48-080027a6b205   1Gi        RWO            standard       10d

NAME                                                        CAPACITY   ACCESS MODES   RECLAIM POLICY   STATUS    CLAIM                             STORAGECLASS   REASON    AGE
persistentvolume/pvc-3860e2c5-5de4-11e8-9f48-080027a6b205   1Gi        RWO            Delete           Bound     default/data-zookeeper-sts-0      standard                 10d
persistentvolume/pvc-dc8c32dc-65c0-11e8-8652-080027a6b205   1Gi        RWO            Delete           Bound     default/data-registry-0           standard                 3h
persistentvolume/pvc-e602da5b-5e25-11e8-9f48-080027a6b205   1Gi        RWO            Delete           Bound     default/data-kafka-sts-0          standard                 9d
```

To remove the persistent data, e.g. to wipe everything out and start over, you just need to delete the persistent volume claims:

```bash
kubectl delete pvc data-registry-0
kubectl delete pvc data-kafka-sts-0
kubectl delete pvc data-zookeeper-sts-0
```

Or, to delete all volume claims in the current namespace:

```bash
kubectl delete pvc --all
```
### optional Development Cycle
From the root of this repo, you can run gradle task: 
for the entire project  
```bash
./gradlew -t test bootJar shadowJar
```
for each subproject
```bash
./gradlew -t test `applicationName`:bootJar shadowJar
```
Which will:
1. Run the unit tests of each subproject
1. Assemble the executable jar 
1. Set up a watch on the source code of each subproject which will rerun compilation and tests when they change