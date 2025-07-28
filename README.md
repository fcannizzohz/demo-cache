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

Since we use Hazelcast Enterprise, you need a license. You can get a trial enterprise license from here: link

The current docker compose assumes that the license is stored in an environment variable, so to spin up the stack with the license (assuming it's not already configured in your shell environment):

```bash
export HZ_LICENSEKEY=<your-license>
docker compose up
```

You should see the following

```bash
% docker ps
```
| CONTAINER ID |IMAGE|COMMAND|CREATED|STATUS| PORTS                                      | NAMES                          |
|--------------|------------------------------------|------------------------|----------------|--------------|--------------------------------------------|--------------------------------|
| f4a65692b135 | mongo:6.0                          | "docker-entrypoint.s…" | 45 seconds ago | Up 45 seconds | 0.0.0.0:27017->27017/tcp                   | mongo                          |
| a8d2e00db316 | hazelcast/management-center:latest | "bash ./bin/mc-start…" | 45 seconds ago | Up 45 seconds | 8081/tcp, 0.0.0.0:8080->8080/tcp, 8443/tcp | demo-cache-management-center-1 |

