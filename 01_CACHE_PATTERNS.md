# Cache patterns

There are many caching patterns that one may want to consider when designing an application. 

In here we'll discuss in depth those that hazelcast allows an application to leverage.

These patterns are described at high level, in the blog [A Hitchhiker’s Guide to Caching Patterns](https://hazelcast.com/blog/a-hitchhikers-guide-to-caching-patterns/). 
We'll focus now on how to implement the four most basic patterns:

- **Read-Through**:  on a cache miss, the cache manager fetches the data from the underlying data store
- **Write-Through**: on a cache write, the cache manager stores synchronously the data to the underlying data store
- **Write-Behind**: on a cache write, the cache manager buffers the writes and asynchronously writes to the underlying data store.
- **Write-Coalescing**: on a cache write when the cache manager only writes the latest value of a set of changes to the same cache element.

We'll skip the three that don't leverage the automation and integration that Hazelcast provides: 

- **Cache-Aside**: The application code explicitly checks the cache first, and if there's a miss, it fetches data from the underlying store and populates the cache
- **Read-Around**: Reads bypass the cache for certain data (e.g. known stale data) and go directly to the data store.
- **Write-Around** Writes go only to the database, and the cache is updated lazily (e.g. only on subsequent reads); this is useful to avoid polluting the cache with rarely used data.

We'll discuss other more advanced patterns in the next post:

- **Negative-Caching**
- **Refresh-Ahead**
- **Near-Caching**

## Setup a relational database

To illustrate the three patterns in practice we'll use Postgres and the Hazelcast generic [MapLoader](https://docs.hazelcast.com/hazelcast/5.5/mapstore/configuring-a-generic-maploader) and [MapStore](https://docs.hazelcast.com/hazelcast/5.5/mapstore/configuring-a-generic-mapstore.

Postgres can be added to the compose file and started with the cluster using the [official image](https://hub.docker.com/_/postgres):

```yaml
  ## postgres
  pgsql:
    image: postgres
    container_name: pgsql
    restart: always
    shm_size: 128mb
    ports:
      - "5432:5432"
    environment:
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - ./data/pgsql:/var/lib/postgresql/data
    networks:
      - hznet

  adminer:
    image: adminer
    restart: always
    ports:
      - 8089:8080
    networks:
      - hznet
```

Add the variables `POSTGRES_USER`, set to `postgres` and `POSTGRES_PASSWORD` with a chosen password to the `.env` file.

It can be tested using Adminer's UI at `http://localhost:8089` using
```
System: PostgreSQL
Server: pgsql          ## name of the compose service
Username: postgres     ## default user
Password: ***********  ## ./pgsql.password
```

## The demo schema

The demo schema can be created using the SQL in `./demo-cache-pgsql-schema.sql` and consists of three tables: 
- `Products`, a list of orderable products
- `Order_Status`, the status of an order (`pending` when created and `completed` when fulfilled)
- `Orders`, the orders for a product, including the relevant timestamps, quantity and order status. 

To create the schema run the following command that uses `psql` to execute the commands to create the schema in the `postgres` database:

```bash
docker exec -i pgsql psql -U postgres -f - < ./resources/demo-cache-pgsql-schema.sql
 ```

or use the provided `compose-init.yml`:

```bash
docker compose -f compose-init.yml up --abort-on-container-exit
```

## Setting up the Java project

Before configuring Hazelcast, we need to create the Java classes representing the data in the newly created schema. These classes are `com.hazelcast.fcannizzohz.democache.model.[Order|Product|OrderStatus]`.

With the pom and project setup, the following builds the jar file with the dependencies:

```bash
mvn clean package
```

## Configuring Hazelcast JDBC connection and generic MapLoader/MapStore

[This](https://docs.hazelcast.com/hazelcast/5.5/data-connections/data-connections-configuration) helps configuring a shared JDBC connection, [this](https://docs.hazelcast.com/hazelcast/5.5/mapstore/configuring-a-generic-maploader) helps configuring the generic MapLoader and [this](https://docs.hazelcast.com/hazelcast/5.5/mapstore/configuring-a-generic-mapstore) helps configuring the MapStore. 

Note: Generic MapStore and MapLoader use the Hazelcast Jet streaming subsystem which must be enabled in `hazelcast.yaml`:

```yaml
hazelcast:
  jet:
    enabled: true
...
```

For our demonstration code we use this definition for the `GenericMapStore` (similar definitions apply for `Orders` and `OrderStatus`):

```yaml
  ...
  data-connection:
    pgsql-connection:
      type: JDBC
      properties:
        jdbcUrl: jdbc:postgresql://pgsql:5432/postgres
        user: <your user>
        password: <your password>
      shared: true
      
  map:
    ...
    products:
      map-store:
        enabled: true
        class-name: com.hazelcast.mapstore.GenericMapLoader
        properties:
          data-connection-ref: pgsql-connection
          external-name: products
          id-column: id
          columns: id,name,price
          type-name: com.hazelcast.fcannizzohz.democache.model.Product
    ...
```

To prove that it works as expected, the test `com.hazelcast.fcannizzohz.democache.ProductIT#loadProductsIntoHazelcast()` shows that the map `"products"` us primed with the data from the database:

```java
    @Test
    void loadProductsIntoHazelcast() throws SQLException {
        List<Product> products = db.getProducts();
        assertFalse(products.isEmpty(), "DB returned no products");

        IMap<Integer, Product> productMap = hazelcast.getMap("products");
        assertEquals(products.size(), productMap.size(), "Mismatch in product count");

        for (Product p : products) {
            Product product = productMap.get(p.id());
            assertNotNull(product, "Missing product for key: " + p.id());
            assertEquals(p.name(), product.name(), "Mismatch in product name");
            assertEquals(p.price(), product.price(), "Mismatch in product price");
        }
    }

```

## Caching patterns with MapStore

### Read-Through

**Read-Through** mode is the pattern whereby the cache manager has the responsibility of loading the data from the remote system into the cache on a cache miss. In essence, with Hazeclast, having plugged in a `MapStore` automatically implement this pattern. As we've seen in the previous paragraph, entries in the `products` map are loaded when a key is requested and the entry isn't in the map.

To prove this further, let's observe what happens when we load a value that isn't there:

```java
    @Test
    void failWhenLoadingMissingProductsIntoHazelcast() throws SQLException {
        List<Product> products = db.getProducts();
        Product product = new Product(900, "I don't exist", new BigDecimal("100.00"));
        assertFalse(products.contains(product), "Missing product for key: " + product.id());

        IMap<Integer, Product> productMap = hazelcast.getMap("products");
        Product missing = productMap.get(product.id());
        assertNull(missing, "Missing product for key: " + product.id());
    }
```

Under normal operations, Read-Through works very well and frees the application from having to handle the logic of connecting to the downstream data 
source and parse the data.

But considerations must be made to take into account some edge cases, including

- configuring TTL and eviction policies
- what behaviour to adopt when downstream system isn't available (retries and backoff time)
- how to optimise access to downstream system and prevent bottlenecks / bursts
- load latencies and thread management
- data size and serialization costs
- cache population strategies
- consistency guarantees

### Configuring the behaviour of the Cache

With any map store, most of the caching behaviour can be configured.

#### Constraining the size of the cache im memory

Full configuration parameters are documented [here](https://docs.hazelcast.com/hazelcast/5.5/data-structures/managing-map-memory). The main configuration parameters are:

|map property | default      | description   |
|----|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
|max-idle-seconds| 0 (disabled) | This value is relative to the time of a map’s last write. For example a time to live (TTL) of 60 seconds means that an entry will be removed if it is not written to at least every 60 seconds. |
|time-to-live-seconds| 0 (disabled) | This value is relative to the time of the last access to an entry in the map. |
|eviction-policy| _object_ | An eviction policy limits the size of a map. If the size of the map grows larger than the limit, the eviction policy defines which entries to remove from the map to reduce its size |

The optimal values of each of these parameters depends on the access pattern to the data in the cache. For example OrderStatus and Products are slow changing therefore can be configured with larger `time-to-live-seconds` and `max-idle-second` and less stringent eviction policies. 

Orders, on the other end, may have smaller values and more stringent eviction policies since they are fast changing. 

#### Advanced configuration of the cache

Caches and MapStores can be configured in more details following instructions in the [Hazelcast documentation](https://docs.hazelcast.com/hazelcast/5.5/mapstore/configuration-guide)

### Linked patterns

- **Negative Caching** will be discussed elsewhere.
- **Read Around**: there may be situations where the application is interested in bits of data that aren't efficient to cache, for example, large objects that are accessed sporadically. In this case, it may make sense to directly go to the data source and entirely bypass the cache.  

### Write-Through and Write-Behind

These two patterns are relative to how the data written in cache is propagated via the MapStore to the underlying data store. 

Write-Through implies the map store to write to the underlying data strore synchronously. Every time a data is written in cache it's immediately stored. This is the default behaviour and doesn't require any configuration. 

Since the operation is synchronous, the cache write is slower from the client point of view, because Hazelcast needs to complete the sychronous operation on the data store.

If performance is key, then Write-Behind is better. To achieve this, the MapStore can be configured with 

| Map Store Property    | Default         | Description   |
|-----------------------| --------------- |---|
| `write-delay-seconds` | `0` (disabled)  | The number of seconds to wait before writing modified entries to the underlying data store. When greater than 0, write-behind caching is enabled.   |
| `write-batch-size`    | `-` (unlimited) | The maximum number of entries to include in a single batch write operation. This setting optimises round-trips to the data store by grouping writes. |

The optimal configuration for these properties depends on the workload characteristics and performance goals:
- `write-delay-seconds` controls the trade-off between latency and throughput. Set to `0` for synchronous Write-Trough semantics. Use a positive value for asynchronous Write-Behind behaviour to reduce load on the backend and improve throughput, especially for bursty workloads.
- `write-batch-size` should be tuned based on the backend system's ability to handle batch writes. If the store supports batch inserts or updates efficiently, increasing this value can significantly reduce write bottlenecks and improve performance.

### Write-Coalescing

Write coalescing is beneficial when frequent updates to the same cache values occur in a short timeframe. This pattern can be activated by setting

| Map Store Property    | Default         | Description   |
|-----------------------| --------------- |---|
| `write-coalescing`    | `false`         | If enabled, only the most recent update to a given key is written during the write-delay window, discarding intermediate updates.                   |

Enabling it reduces redundant writes but may risk losing intermediate states. It should be adopted unless intermediate versions of the same cache values must be preserved (e.g. for audit or incremental change tracking).

### Another code example

The tests in [`com.hazelcast.fcannizzohz.democache.BasicCachePatternsTest`](./src/test/java/com/hazelcast/fcannizzohz/democache/BasicCachePatternsTest.java) show a simple configuration of the implementation of the four patterns. The test uses a mocked MapStore which is configured using the parameters described above.

### Final remarks

Hazelcast offers a wide set of configuration parameters all available via the official documentation.