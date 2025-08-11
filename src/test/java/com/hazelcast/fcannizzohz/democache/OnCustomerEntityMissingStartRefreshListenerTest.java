package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.fcannizzohz.democache.model.CustomerProfile;
import com.hazelcast.fcannizzohz.democache.model.Order;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.lang.Thread.sleep;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OnCustomerEntityMissingStartRefreshListenerTest {
    private TestHazelcastFactory factory;
    private HazelcastInstance instance;
    private OnCustomerEntityMissingStartRefreshListener listener;

    @BeforeEach
    void setUp() {
        factory = new TestHazelcastFactory();

        Config config = TestConfig.newTestConfig();
        instance = factory.newHazelcastInstance(config);
        listener = new OnCustomerEntityMissingStartRefreshListener(24 * 60 * 60, instance);
        instance.getMap("customers").addEntryListener(listener, true);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.shutdownAll();
        }
    }

    @Test
    void refreshOrdersForCustomerAdded()
            throws InterruptedException {
        OrderGenerator.populateWithOrders(5, 10, 24 * 2, ordersMap());

        CustomerProfile profile = new CustomerProfile(1, "testName", "test@email.com");
        customerProfilesMap().put(1, profile);

        sleep(2000);

        assertTrue(listener.getRefreshCount() > 0);
    }

    private IMap<String, Order> ordersMap() {
        return instance.getMap("orders");
    }

    private IMap<Integer, CustomerProfile> customerProfilesMap() {
        return instance.getMap("customers");
    }

}
