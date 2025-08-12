package com.hazelcast.fcannizzohz.democache.model;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.EntryStore;
import com.hazelcast.map.MapLoaderLifecycleSupport;
import com.hazelcast.shaded.com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Properties;

import static com.hazelcast.fcannizzohz.democache.model.OrderMapStore.loadOrder;
import static com.hazelcast.fcannizzohz.democache.model.OrderMapStore.mapRow;
import static com.hazelcast.fcannizzohz.democache.model.OrderMapStore.newHikariDataSource;

public class OrderEntryStore implements MapLoaderLifecycleSupport, EntryStore<String, Order> {

    private HikariDataSource dataSource;

    @Override
    public void store(String s, MetadataAwareValue<Order> orderMetadataAwareValue) {

    }

    @Override
    public void storeAll(Map<String, MetadataAwareValue<Order>> map) {

    }

    @Override
    public void delete(String s) {

    }

    @Override
    public void deleteAll(Collection<String> collection) {

    }

    @Override
    public MetadataAwareValue<Order> load(String key) {
        Order order = loadOrder(key, this.dataSource);
        if(OrderSentinels.NOT_FOUND.equals(order)) {
            return new MetadataAwareValue<>(OrderSentinels.NOT_FOUND, 1000);
        }
        return new MetadataAwareValue<>(order);
    }

    @Override
    public Map<String, MetadataAwareValue<Order>> loadAll(Collection<String> collection) {
        return Map.of();
    }

    @Override
    public Iterable<String> loadAllKeys() {
        return null;
    }

    @Override
    public void init(HazelcastInstance hazelcastInstance, Properties properties, String s) {
        this.dataSource = newHikariDataSource(hazelcastInstance, properties);
    }

    @Override
    public void destroy() {
        this.dataSource.close();
    }
}
