# Demo Cache

One of Hazelcast’s primary use cases is to work as a caching layer to accelerate access to data stored in one (or more) storage systems.

In this series of micro-blogs we’ll look at all the capabilities offered by hazelcast as a cache, specifically:

 - implement basic caching patterns
 - custom loaders to customize load/store behaviour
 - use CDC to keep cache in sync
 - optimize access to read data using near cache
 - optimize operations using persistence
 - advanced CQRS to benefit from near cache
 - using CP for strong consistency
 - cache ops: monitoring and alerting 

We’ll look at how this can be implemented in code.

We’ll use MongoDB as the storage/db layer of choice

## Setup

The setup for this project consists of an Hazelcast cluster made of a single member (for now) with Management Centre
and a MongoDB instance. The stack is started locally via docker compose.

Later on, for more advanced usages we'll extend the setup with more nodes

Since we use Hazelcast Enterprise, you need a license. You can get a trial enterprise license from [here](https://hazelcast.com/get-started/).

The current docker compose assumes that the license is stored in an environment variable, so to spin up the stack with the license (assuming it's not already configured in your shell environment):

```bash
export HZ_LICENSEKEY=<your-license>
docker compose up
```

You should see the following

```bash
docker ps
```
| CONTAINER ID | IMAGE                                   | COMMAND                |CREATED|STATUS| PORTS                                      | NAMES |
|--------------|-----------------------------------------|------------------------|----------------|--------------|--------------------------------------------|-------|
| f4a65692b135 | mongodb/mongodb-community-server:latest | "python3 /usr/local/…" | 45 seconds ago | Up 45 seconds | 0.0.0.0:27017->27017/tcp                   | mongo |
| a8d2e00db316 | hazelcast/management-center:latest      | "bash ./bin/mc-start…" | 45 seconds ago | Up 45 seconds | 8081/tcp, 0.0.0.0:8080->8080/tcp, 8443/tcp | mc    |
| eabe4909eaa0 | hazelcast/hazelcast-enterprise:latest   | "hz start"             | 45 seconds ago | Up 45 seconds | 0.0.0.0:5701->5701/tcp                     | hz1   |

### Testing the setup

To manually test the cluster Hazelcast provides a shell client as documented [here](https://docs.hazelcast.com/clc/5.5.0/install-clc), alternatively, it can be tested using the embedded client in the docker image:

```bash
docker run --rm -it \
        --entrypoint /opt/hazelcast/bin/hz-cli \
        --network demo-cache_hznet hazelcast/hazelcast-enterprise:latest \
        cluster --targets=dev@hazelcast1:5701
```

which shows:

```
State: ACTIVE
Version: 5.5.6
Size: 1

ADDRESS                  UUID               
[172.18.0.4]:5701        cbbc8c23-9fd7-4d36-a468-c3fa133fbbd3
```

[Management Center](https://hazelcast.com/products/management-center/) is available at `http://localhost:8080`. Since we have started with the default settings, the cluster name is `dev`. The cluster should be immediately available. Use of Management Center is documented [here](https://docs.hazelcast.com/management-center/5.8/getting-started/overview).

To test mongodb, you can run `mongosh` as documented [here](https://www.mongodb.com/resources/products/compatibilities/docker)

```bash
% docker run --name mongosh \
    --network demo-cache_hznet \
    mongodb/mongodb-community-server:latest mongosh \
    mongodb://mongodb --eval "show dbs"
```

which shows an output similar to

```
admin   40.00 KiB
config  12.00 KiB
local   72.00 KiB
```
