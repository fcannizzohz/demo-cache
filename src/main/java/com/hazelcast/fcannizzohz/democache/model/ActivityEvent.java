package com.hazelcast.fcannizzohz.democache.model;

import java.io.Serializable;
import java.time.Instant;

public record ActivityEvent(Integer customerId, Instant timestamp) implements Serializable {}

