# Cache patterns

Cache patterns implemented with Hazelcast are described, at high level, in the blog [A Hitchhiker’s Guide to Caching Patterns](https://hazelcast.com/blog/a-hitchhikers-guide-to-caching-patterns/). We'll focus now on how to implement the three most basic patterns:

- **Read-Through**:  on a cache miss, the cache manager fetches the data from the underlying data store
- **Write-Through**: on a cache write, the cache manager stores synchronously the data to the underlying data store
- **Write-Behind**: on a cache write, the cache manager buffers the writes and asynchronously writes to the underlying data store.

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
        user: postgres
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