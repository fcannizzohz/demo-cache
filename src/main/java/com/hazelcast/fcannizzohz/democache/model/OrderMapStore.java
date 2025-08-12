package com.hazelcast.fcannizzohz.democache.model;

import com.hazelcast.config.DataConnectionConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapLoaderLifecycleSupport;
import com.hazelcast.map.MapStore;
import com.hazelcast.shaded.com.zaxxer.hikari.HikariConfig;
import com.hazelcast.shaded.com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class OrderMapStore implements MapLoaderLifecycleSupport, MapStore<String, Order> {
    private HikariDataSource dataSource;

    @Override
    public void store(String s, Order order) {

    }

    @Override
    public void storeAll(Map<String, Order> map) {

    }

    @Override
    public void delete(String s) {

    }

    @Override
    public void deleteAll(Collection<String> collection) {

    }

    @Override
    public Order load(String key) {
        HikariDataSource ds = dataSource;
        return loadOrder(key, ds);
    }

    static Order loadOrder(String key, HikariDataSource ds) {
        String sql = "SELECT * FROM orders WHERE id = ?";
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load order " + key + ": " + e.getMessage());
        }
        return OrderSentinels.NOT_FOUND;
    }

    @Override
    public Map<String, Order> loadAll(Collection<String> keys) {
        if (keys.isEmpty()) return Collections.emptyMap();

        Map<String, Order> result = new HashMap<>();
        String placeholders = String.join(",", Collections.nCopies(keys.size(), "?"));
        String sql = "SELECT * FROM orders WHERE id IN (" + placeholders + ")";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int index = 1;
            for (String key : keys) {
                ps.setObject(index++, key);
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order order = mapRow(rs);
                    result.put(order.id().toString(), order);
                }
            }

        } catch (SQLException e) {
            System.err.println("Failed to load orders: " + e.getMessage());
        }
        return result;
    }

    @Override
    public Iterable<String> loadAllKeys() {
        return null;
    }

    @Override
    public void init(HazelcastInstance hazelcastInstance, Properties properties, String mapName) {
        this.dataSource = newHikariDataSource(hazelcastInstance, properties);
    }

    static HikariDataSource newHikariDataSource(HazelcastInstance hazelcastInstance, Properties properties) {
        String dataConnectionRef = properties.getProperty("data-connection-ref");
        DataConnectionConfig dataConnectionConfig = hazelcastInstance.getConfig().getDataConnectionConfig(dataConnectionRef);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dataConnectionConfig.getProperty("jdbcUrl"));
        config.setUsername(dataConnectionConfig.getProperty("user"));
        config.setPassword(dataConnectionConfig.getProperty("password"));
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        return new HikariDataSource(config);
    }

    @Override
    public void destroy() {
        this.dataSource.close();
    }

    static Order mapRow(ResultSet rs) throws SQLException {
        return new Order(UUID.fromString(rs.getString("id")), rs.getInt("product_id"), rs.getInt("customer_id"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getInt("quantity"),
                rs.getString("description"), rs.getBigDecimal("total_price"), rs.getInt("status_id"));
    }
}
