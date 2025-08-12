package com.hazelcast.fcannizzohz.democache.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class OrderSentinels {
    public static final Order NOT_FOUND = new Order(
            new UUID(0, 0),
            -1,
            -1,
            Instant.EPOCH,
            Instant.EPOCH,
            0,
            "NOT_FOUND",
            BigDecimal.ZERO,
            -1
    );
}
