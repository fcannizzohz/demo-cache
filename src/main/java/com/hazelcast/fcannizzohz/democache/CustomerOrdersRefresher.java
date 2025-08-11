package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.fcannizzohz.democache.model.Order;
import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.Predicates;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Function;

import static com.hazelcast.fcannizzohz.democache.TopActiveCustomersPipeline.DEFAULT_CUSTOMER_COUNT_MAP_NAME;

public class CustomerOrdersRefresher
        implements Function<Integer, Integer>, Serializable {

    private final HazelcastInstance hz;
    private final int pastHoursWindow;

    public CustomerOrdersRefresher() {
        this(24);
    }

    public CustomerOrdersRefresher(int pastHoursWindow) {
        this(pastHoursWindow, Hazelcast.bootstrappedInstance());
    }

    public CustomerOrdersRefresher(int pastHoursWindow, HazelcastInstance hazelcastInstance) {
        this.hz = hazelcastInstance;
        this.pastHoursWindow = pastHoursWindow;
    }

    @Override
    public Integer apply(Integer customerId) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofHours(pastHoursWindow));
        Predicate<Object, Object> customerOrders = Predicates.equal("customerId", customerId);
        Predicate<Object, Object> lastOrders = Predicates.greaterEqual("updatedAt", cutoff);

        IMap<String, Order> ordersMap = hz.getMap("orders");
        Collection<Order> refreshed = ordersMap.values(Predicates.and(customerOrders, lastOrders));

        return refreshed.size();
    }
}
