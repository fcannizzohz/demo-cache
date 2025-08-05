# Advanced Caching Patterns

The benefits of a `GenericMapStore` are mainly to reduce the custom code to build and maintain when mapping between the underlying
data store and the in memory caches is simple.

For advanced and more sophisticated uses, a custom `MapStore` allows the necessary degree of flexibility.

Custom MapStores are [well documented](https://docs.hazelcast.com/hazelcast/5.5/mapstore/working-with-external-data) and in this post
we'll look at building one to illustrate two advanced cache patterns:

 - **refresh-ahead**: also called _proactive refresh_ or _soft expiration_, is a caching strategy where the system preemptively refreshes entries before they expire, usually asynchronously, to ensure that frequently accessed keys stay “hot” in the cache with minimal latency. 
 - **negative-caching**: this pattern consists of caching the fact that a requested item does not exist in the backend. Instead of retrying expensive lookups for missing data (e.g. user not found, no orders), you store a marker in the cache to indicate absence.

## Refresh-ahead

Refresh-ahead complements TTL based expiration, where stale entries are evicted and then reloaded on cache miss, by preemptively load cache entries to prevent cache misses. It's particoularly useful when load time is large and to prevent spikes in the backend system when multiple entries expire simultaneously.

It can be quite complex to implement but with Hazelcast's API some of this complexity is removed. 

Furthermore there are multiple strategies that could be implemented all depending on use cases.

The main strategies are:

 - Procedural (or time-based, proactive): a process periodically scans the cache and refreshes the top-N hot keys, for example refresh the orders of the top 100 users just before their TTL expires. To identify "hot" entries, one can think of different mechanisms, for example, keeping a priority queue with the top entries to refresh or adopting a larning algorithm that statistically learns what entries are likely to be accessed.
  - Event-Driven (reactive): trigger a refresh when an event occurs (eg a cache miss, access to a cache, ...), for example on cache miss for a customer profile, pre-fetch their orders asynchronously

For illustration purposes we'll implement a reactive strategy to loads the last 10 orders of a customer every time a customer profile is loaded in cache. 