package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.config.MapConfig;
import com.hazelcast.core.EntryView;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.fcannizzohz.democache.model.Order;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.*;

class TopCustomerRefresherTest {
    private TestHazelcastFactory factory;
    private HazelcastInstance instance;

    @BeforeEach
    void setUp() {
        factory = new TestHazelcastFactory();

        Config config = TestConfig.newTestConfig();
        config.getMapConfig("orders").setPerEntryStatsEnabled(true).setMaxIdleSeconds(3);

        instance = factory.newHazelcastInstance(config);
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
    void testRefresh() throws InterruptedException {
        int topN = 10;
        int hoursWindow = 24;
        // populate orders map
        OrderGenerator.populateWithOrders(topN * 5, 10, hoursWindow * 2, ordersMap());
        IMap<Integer, Long> topCustomers = instance.getMap(TopActiveCustomersPipeline.DEFAULT_CUSTOMER_COUNT_MAP_NAME);
        // populate top N customers map
        List<Integer> customerIds = ordersMap().values().stream().map(Order::customerId).toList();
        Stream<Integer> refreshCustomerIds = customerIds.stream().limit(topN);
        refreshCustomerIds.forEach(customerId -> topCustomers.put(customerId, ThreadLocalRandom.current().nextLong(20)));

        TopCustomerRefresher topCustomerRefresher = new TopCustomerRefresher(topN, 24, instance);
        sleep(2000);
        Integer refreshCount = topCustomerRefresher.call();
        System.out.println("refreshCount = " + refreshCount);
        sleep(1000);


    }

}