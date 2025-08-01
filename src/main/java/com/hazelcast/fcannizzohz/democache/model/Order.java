package com.hazelcast.fcannizzohz.democache.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Order(UUID id,
                    Integer productId,
                    Instant createdAt,
                    Instant updatedAt,
                    int quantity,
                    String description,
                    BigDecimal total_price,
                    Integer statusId) {
}
