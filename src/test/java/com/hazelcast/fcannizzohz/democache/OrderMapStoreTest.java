package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.config.DataConnectionConfig;
import com.hazelcast.config.MapStoreConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.fcannizzohz.democache.model.Order;
import com.hazelcast.fcannizzohz.democache.model.OrderMapStore;
import com.hazelcast.fcannizzohz.democache.model.OrderSentinels;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class OrderMapStoreTest {
    private TestHazelcastFactory factory;
    private HazelcastInstance instance;
    private List<UUID> orderIds;

    @BeforeEach
    void setUp() throws Exception {
        factory = new TestHazelcastFactory();

        Config config = TestConfig.newTestConfig();
        DataConnectionConfig h2Config = new DataConnectionConfig();
        h2Config.setName("h2-test").setType("JDBC")
                .setProperty("jdbcUrl", "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
                .setProperty("user", "user1")
                .setProperty("password", "password1");
        config.setDataConnectionConfigs(Map.of("h2-test", h2Config));
        MapStoreConfig mapStoreConfig  = new MapStoreConfig();
        mapStoreConfig.setEnabled(true)
                      .setClassName(OrderMapStore.class.getName())
                .setProperty("data-connection-ref", "h2-test");
        config.getMapConfig("orders")
              .setMapStoreConfig(mapStoreConfig);

        instance = factory.newHazelcastInstance(config);

        orderIds = setupData(h2Config);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.shutdownAll();
        }
    }

    private IMap<String, Order> ordersMap() {
        return instance.getMap("orders");
    }

    @Test
    void testOrderNotFound()
    {
        Order notFound = ordersMap().get(UUID.randomUUID().toString());
        assertEquals(OrderSentinels.NOT_FOUND, notFound);
        Order found = ordersMap().get(orderIds.getFirst().toString());
        assertNotEquals(OrderSentinels.NOT_FOUND, found);
    }


    private List<UUID> setupData(DataConnectionConfig h2Config) throws Exception {
        String jdbcUrl = h2Config.getProperty("jdbcUrl");
        String user = h2Config.getProperty("user");
        String password = h2Config.getProperty("password");

        List<UUID> orderIds = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password);
             Statement stmt = conn.createStatement()) {

            // Create the orders table
            stmt.executeUpdate("""
                CREATE TABLE orders (
                    id UUID PRIMARY KEY,
                    product_id INTEGER NOT NULL,
                    customer_id INTEGER NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    quantity INTEGER NOT NULL,
                    description TEXT NOT NULL,
                    total_price NUMERIC(10,2) NOT NULL,
                    status_id INTEGER NOT NULL
                )
            """);

            // Insert 5 test orders
            try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO orders (id, product_id, customer_id, created_at, updated_at, quantity, description, total_price, status_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
                for (int i = 1; i <= 5; i++) {
                    UUID uuid = UUID.randomUUID();
                    orderIds.add(uuid);
                    ps.setObject(1, uuid);
                    ps.setInt(2, 100 + i); // product_id
                    ps.setInt(3, 200 + i); // customer_id
                    ps.setTimestamp(4, Timestamp.from(Instant.now().minusSeconds(i * 3600L))); // created_at
                    ps.setTimestamp(5, Timestamp.from(Instant.now())); // updated_at
                    ps.setInt(6, i * 2); // quantity
                    ps.setString(7, "Test order " + i);
                    ps.setBigDecimal(8, BigDecimal.valueOf(i * 19.99)); // total_price
                    ps.setInt(9, 1); // status_id
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            System.out.println("H2 test setup completed: orders table created with 5 rows.");


        }
        return orderIds;
    }
}
