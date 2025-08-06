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

import static com.hazelcast.fcannizzohz.democache.TopActiveCustomersPipeline.DEFAULT_CUSTOMER_COUNT_MAP_NAME;

public class TopCustomerRefresher implements Callable<Integer>, Serializable {

    private final HazelcastInstance hz;
    private final int topN;
    private final int hoursWindow;

    public TopCustomerRefresher() {
        this(10, 24, Hazelcast.bootstrappedInstance());
    }

    public TopCustomerRefresher(int topN, int hoursWindow) {
        this(topN, hoursWindow, Hazelcast.bootstrappedInstance());
    }

    public TopCustomerRefresher(int topN, int hoursWindow, HazelcastInstance hazelcastInstance) {
        this.hz = hazelcastInstance;
        this.topN = topN;
        this.hoursWindow = hoursWindow;
    }

    @Override
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
