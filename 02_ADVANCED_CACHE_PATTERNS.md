# Advanced Caching Patterns

The benefits of a `GenericMapStore` are mainly to reduce the custom code to build and maintain when mapping between the underlying
data store and the in memory caches is simple.

For advanced and more sophisticated uses, a custom `MapStore` allows the necessary degree of flexibility.

Custom MapStores are [well documented](https://docs.hazelcast.com/hazelcast/5.5/mapstore/working-with-external-data) and in this post
we'll look at building one to illustrate two advanced cache patterns:

 - **refresh-ahead**: also called _proactive refresh_ or _soft expiration_, is a caching strategy where the system preemptively refreshes entries before they expire, usually asynchronously, to ensure that frequently accessed keys stay “hot” in the cache with minimal latency. 
 - **negative-caching**: this pattern consists of caching the fact that a requested item does not exist in the backend. Instead of retrying expensive lookups for missing data (e.g. user not found, no orders), you store a marker in the cache to indicate absence.

## Refresh-ahead

Refresh-ahead complements TTL based expiration, where stale entries are evicted and then reloaded on cache miss, by preemptively load cache entries to prevent cache misses. It's particularly useful when load time is large and to prevent spikes in the backend system when multiple entries expire simultaneously.

It can be quite complex to implement but with Hazelcast's API some of this complexity is removed. 

Furthermore there are multiple strategies that could be implemented all depending on use cases.

The main strategies are:

 - **Procedural (or time-based, proactive)**: a process periodically scans the cache and refreshes the top-N hot keys, for example refresh the orders of the top 100 users just before their TTL expires. To identify "hot" entries, one can think of different mechanisms, for example, keeping a priority queue with the top entries to refresh or adopting a larning algorithm that statistically learns what entries are likely to be accessed.
 - **Event-Driven (reactive)**: trigger a refresh when an event occurs (eg a cache miss, access to a cache, ...), for example on cache miss for a customer profile, pre-fetch their orders asynchronously

### Procedural strategy

To implement a proactive strategy we'll define for illustration purpose only the top customers as those with the largest number of interactions with the system. For those customers, we'll refresh ahead their last 5 orders.

There are different techniques one may adopt to implement this behaviour. For simplicity, here, we assume we have an event stream representing customer interaction: `ActivityEvent(Integer customerId, Instant timestamp)` which are stored in a cache.

Assuming the TTL for `Orders` map is 10 minutes, we'll identify the top-10 customers by creating a pipeline aggregating the customers in a sliding window of 9 minutes, every minute, and counting the interactions for each customer, then ordering and filtering the top 10:

```java
public static Pipeline buildPipeline(int topN, long windowSize, long slideBy, long lag) {
    StreamSource<Map.Entry<Integer, ActivityEvent>> source = Sources.mapJournal(DEFAULT_ACTIVITY_MAP_NAME,
            JournalInitialPosition.START_FROM_CURRENT);

    WindowDefinition windowDef = sliding(windowSize, slideBy);
    ToLongFunctionEx<Map.Entry<Integer, ActivityEvent>> toLongInstant =
            activityEventEntry -> activityEventEntry.getValue().timestamp().toEpochMilli();

    Pipeline p = Pipeline.create();

    p.readFrom(source).withTimestamps(toLongInstant, lag)  // allow lag
     // group by customerId
     .groupingKey((FunctionEx<Map.Entry<Integer, ActivityEvent>, Integer>) e -> e.getValue().customerId())
     // define the window
     .window(windowDef)
     // aggregate by counting
     .aggregate(AggregateOperations.counting())
     // map to customerId->count
     .map(customerCount -> Tuple2.tuple2(customerCount.key(), customerCount.result()))
     // single group
     .groupingKey(t -> "top-N")
     // select top N
     .rollingAggregate(AggregateOperations.topN(topN, ComparatorEx.comparingLong(Tuple2<Integer, Long>::f1)))
     // map back to customerId->count
     .flatMap(result -> Traversers.traverseIterable(result.getValue()))
     // sink to map
     .writeTo(Sinks.map(DEFAULT_CUSTOMER_COUNT_MAP_NAME,
             // customerId
             Tuple2::f0,
             // count
             Tuple2::f1
     ));
    return p;
}
```

At the same time, an order refresher process can be dispatched via distributed executor on the cluster. This process, 
fully implemented in `com.hazelcast.fcannizzohz.democache.TopCustomerRefresher` looks like:

```java
    public Integer call() {
        IMap<Integer, Long> customerCountMap = hz.getMap(DEFAULT_CUSTOMER_COUNT_MAP_NAME);

        // 1. Get all customer counts
        Map<Integer, Long> counts = customerCountMap.getAll(customerCountMap.keySet());

        // 2. Identify top 10 customers by count
        List<Integer> topCustomers = counts.entrySet().stream()
                                           .sorted(Map.Entry.<Integer, Long>comparingByValue().reversed())
                                           .limit(topN)
                                           .map(Map.Entry::getKey)
                                           .toList();

        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(hoursWindow));
        Predicate<Object, Object> customerOrders = Predicates.in("customerId", topCustomers.toArray(new Comparable[0]));
        Predicate<Object, Object> lastOrders = Predicates.greaterEqual("updatedAt", cutoff);

        IMap<String, Order> ordersMap = hz.getMap("orders");
        Collection<Order> refreshed = ordersMap.values(Predicates.and(customerOrders, lastOrders));

        return refreshed.size();
    }}


```

The test `com.hazelcast.fcannizzohz.democache.TopActiveCustomersPipelineTest#testPipeline()` shows how this pipeline works when executed.

