package com.hazelcast.fcannizzohz.democache.model;

import java.time.Instant;
import java.util.UUID;

public record Order(UUID id,
                    int productId,
                    Instant createdAt,
                    Instant updatedAt,
                    int quantity,
                    String description,
                    int statusId) {
}
