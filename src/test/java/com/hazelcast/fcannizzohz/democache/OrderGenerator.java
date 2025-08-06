package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.fcannizzohz.democache.model.Order;
import com.hazelcast.map.IMap;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class OrderGenerator {

    /**
     *
     * @param maxCustomers max number of customers
     * @param maxOrdersPerCustomer max number of orders per customers
     * @param ordersHoursWindow a window in hours specifying the span of updateAt for each order
     * @param ordersMap the map to populate with newly generated orders
     */
    public static void populateWithOrders(int maxCustomers, int maxOrdersPerCustomer, long ordersHoursWindow, IMap<String, Order> ordersMap) {
        Instant now = Instant.now();
        long secondsInHoursWindow = ordersHoursWindow * 3600;

        for (int customerId = 1; customerId <= maxCustomers; customerId++) {
            for (int i = 0; i < maxOrdersPerCustomer; i++) {
                Instant randomTime = now.minusSeconds(ThreadLocalRandom.current().nextLong(ordersHoursWindow));
                UUID orderId = UUID.randomUUID();
                int productId = ThreadLocalRandom.current().nextInt(1, 10); // 10 products
                int quantity = ThreadLocalRandom.current().nextInt(1, 5);
                BigDecimal price = BigDecimal.valueOf(10 + ThreadLocalRandom.current().nextDouble(90))
                                             .setScale(2, BigDecimal.ROUND_HALF_UP);
                BigDecimal total = price.multiply(BigDecimal.valueOf(quantity));
                int statusId = ThreadLocalRandom.current().nextInt(1, 4); // status 1-3

                ordersMap.put(orderId.toString(), new Order(
                        orderId,
                        productId,
                        customerId,
                        randomTime.minusSeconds(3600),  // createdAt 1h before updatedAt
                        randomTime,
                        quantity,
                        "Auto-generated order",
                        total,
                        statusId
                ));
            }
        }
    }
}
