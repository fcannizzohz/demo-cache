package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.Config;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.fcannizzohz.democache.model.ActivityEvent;
import com.hazelcast.jet.JetService;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.jet.pipeline.test.GeneratorFunction;
import com.hazelcast.jet.pipeline.test.TestSources;
import com.hazelcast.map.IMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;

import static com.hazelcast.fcannizzohz.democache.TopActiveCustomersPipeline.DEFAULT_ACTIVITY_MAP_NAME;
import static com.hazelcast.fcannizzohz.democache.TopActiveCustomersPipeline.DEFAULT_CUSTOMER_COUNT_MAP_NAME;
import static java.lang.Thread.sleep;

class TopActiveCustomersPipelineTest {

    private TestHazelcastFactory factory;
    private HazelcastInstance instance;

    @BeforeEach
    void setUp() {
        factory = new TestHazelcastFactory();

        Config config = TestConfig.newTestConfig();
        config.getMapConfig(DEFAULT_ACTIVITY_MAP_NAME)
              .getEventJournalConfig()
              .setEnabled(true)
              .setCapacity(1000); // ensure journal capacity

        instance = factory.newHazelcastInstance(config);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.shutdownAll();
        }
    }

    @Test
    void testPipeline() throws Exception {
        JetService jet = instance.getJet();

        Pipeline pipeline = TopActiveCustomersPipeline.buildPipeline(10, 1000, 100, 0);
        Job job = jet.newJob(pipeline);
        IMap<String, ActivityEvent> activityMap = instance.getMap(DEFAULT_ACTIVITY_MAP_NAME);
        for(int i = 0; i < 100; i++) {
            Integer customerId = ThreadLocalRandom.current().nextInt(10);
            sleep(ThreadLocalRandom.current().nextInt(10));
            activityMap.put(UUID.randomUUID().toString(), new ActivityEvent(customerId, Instant.now()));
        }
        processFor(job, Duration.ofMillis(1500)); // wait for job to process

        IMap<Integer, Long> topCustomersMap = instance.getMap(DEFAULT_CUSTOMER_COUNT_MAP_NAME);
        for (Map.Entry<Integer, Long> entry : topCustomersMap.entrySet()) {
            System.out.println(entry);
        }
    }


    private void processFor(Job job, Duration duration) {
        try {
            sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        job.cancel();
        try {
            job.join();
        } catch (CancellationException e) {
            // ignore
        }

    }

}