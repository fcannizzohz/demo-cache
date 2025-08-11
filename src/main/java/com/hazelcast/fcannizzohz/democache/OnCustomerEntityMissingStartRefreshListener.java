package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.fcannizzohz.democache.model.CustomerProfile;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryLoadedListener;
import com.hazelcast.map.listener.MapListener;

import java.util.concurrent.atomic.AtomicLong;

public class OnCustomerEntityMissingStartRefreshListener implements EntryLoadedListener<Integer, CustomerProfile>,
                                                                    EntryAddedListener<Integer, CustomerProfile>,
                                                                    MapListener {

    private final CustomerOrdersRefresher refresher;
    private final AtomicLong refreshCount = new AtomicLong(0);

    public OnCustomerEntityMissingStartRefreshListener(Integer pastHoursWindow) {
        this(pastHoursWindow, Hazelcast.bootstrappedInstance());
    }

    public OnCustomerEntityMissingStartRefreshListener(Integer pastHoursWindow,  HazelcastInstance hazelcastInstance) {
        refresher = new CustomerOrdersRefresher(pastHoursWindow, hazelcastInstance);
    }

    @Override
    public void entryAdded(EntryEvent<Integer, CustomerProfile> entryEvent) {
        refreshCustomerOrders(entryEvent);
    }

    @Override
    public void entryLoaded(EntryEvent<Integer, CustomerProfile> entryEvent) {
        refreshCustomerOrders(entryEvent);
    }

    protected void refreshCustomerOrders(EntryEvent<Integer, CustomerProfile> entryEvent) {
        Integer count = refresher.apply(entryEvent.getKey());
        refreshCount.addAndGet(count);
    }

    public Integer getRefreshCount() {
        return refreshCount.intValue();
    }
}
