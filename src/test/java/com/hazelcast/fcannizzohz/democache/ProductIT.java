package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.fcannizzohz.democache.model.Product;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductIT {

    private HazelcastInstance hazelcast;
    private Connection connection;
    private DB db;

    @BeforeAll
    void setup() throws SQLException {
        // Start Hazelcast client
        hazelcast = HazelcastClient.newHazelcastClient();
        db = DB.newDB();
    }

    @AfterAll
    void cleanup() throws SQLException {
        if (hazelcast != null) {
            hazelcast.shutdown();
        }
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }

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
}

