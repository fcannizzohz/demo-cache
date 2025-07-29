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

## Sections

- [Setup](./00_SETUP.md)