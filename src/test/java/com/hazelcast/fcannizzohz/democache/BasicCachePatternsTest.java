package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.client.test.TestHazelcastFactory;
import com.hazelcast.config.*;
import com.hazelcast.core.*;
import com.hazelcast.map.IMap;
import com.hazelcast.map.MapStore;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.Map;

import static com.hazelcast.test.HazelcastTestSupport.randomName;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * This test suite demonstrates the behavior of four core Hazelcast caching patterns:
 *
 * 1. Read-Through:
 *    - When a requested key is not found in the cache (IMap), Hazelcast calls `MapLoader.load(key)`
 *      to retrieve the value from the backing store and caches it automatically.
 *
 * 2. Write-Through:
 *    - When a value is written to the cache via `put()`, Hazelcast immediately writes it
 *      through to the backing store by calling `MapStore.store(key, value)` synchronously.
 *
 * 3. Write-Behind:
 *    - Similar to write-through, but writes to the backing store are buffered and executed
 *      asynchronously after a configured delay (`write-delay-seconds`).
 *
 * 4. Write-Coalescing:
 *    - An optimisation for write-behind that ensures only the most recent update for a key
 *      is persisted if multiple writes happen within the delay window.
 *      Intermediate values are discarded.
 */
class BasicCachePatternsTest {

    private MapStore<String, String> store;
    private TestHazelcastFactory factory;

    @BeforeEach
    void setUp() {
        factory = new TestHazelcastFactory();
        store = mock(MapStore.class);
    }

    @AfterEach
    void tearDown() {
        if (factory != null) {
            factory.shutdownAll();
        }
    }

    /**
     * Test for Read-Through caching pattern:
     * Verifies that a cache miss triggers `MapLoader.load()` to fetch the value from the backing store.
     */
    @Test
    void testReadThrough() {
        when(store.load("key1")).thenReturn("loadedValue");

        IMap<String, String> map = configureMap("readThroughMap", store, 0, false);
        String value = map.get("key1");

        assertEquals("loadedValue", value);
        verify(store).load("key1");
    }

    /**
     * Test for Write-Through caching pattern:
     * Verifies that `map.put()` results in an immediate call to `MapStore.store()`.
     */
    @Test
    void testWriteThrough() {
        IMap<String, String> map = configureMap("writeThroughMap", store, 0, false);
        map.put("key2", "value2");

        verify(store, timeout(1000)).store("key2", "value2");
    }

    /**
     * Test for Write-Behind caching pattern:
     * Verifies that writes are buffered and `MapStore.store()` is called only after a delay.
     */
    @Test
    void testWriteBehind() throws InterruptedException {
        IMap<String, String> map = configureMap("writeBehindMap", store, 1, false);
        map.put("key3", "value3");

        // Should not be called immediately
        verify(store, after(500).never()).store(eq("key3"), any());
        // Should be called after the delay
        verify(store, timeout(1500)).store("key3", "value3");
    }

    /**
     * Test for Write-Coalescing caching pattern:
     * Verifies that only the last write for a given key is persisted,
     * with previous writes skipped during the write-behind window.
     */
    @Test
    void testWriteCoalescing() throws InterruptedException {
        IMap<String, String> map = configureMap("writeCoalescingMap", store, 1, true);
        map.put("key4", "v1");
        map.put("key4", "v2");
        map.put("key4", "v3");

        verify(store, after(500).never()).store(eq("key4"), any());
        verify(store, timeout(1500).times(1)).store("key4", "v3");
    }

    /**
     * Creates a Hazelcast map configured with the specified MapStore settings.
     *
     * @param mapName           the name of the map
     * @param store             the mock MapStore
     * @param writeDelaySeconds delay in seconds for write-behind (0 for write-through)
     * @param coalescing        whether write-coalescing is enabled
     * @return configured Hazelcast IMap instance
     */
    private IMap<String, String> configureMap(String mapName, MapStore<String, String> store,
                                              int writeDelaySeconds, boolean coalescing) {
        MapStoreConfig storeConfig = new MapStoreConfig()
                .setEnabled(true)
                .setImplementation(store)
                .setWriteDelaySeconds(writeDelaySeconds)
                .setWriteBatchSize(10)
                .setWriteCoalescing(coalescing);

        MapConfig mapConfig = new MapConfig(mapName)
                .setMapStoreConfig(storeConfig);

        Map<String, String> env;
        try {
            env = EnvLoader.load(".env");
        } catch (IOException e) {
            throw new RuntimeException("Unable to load env file.", e);
        }

        Config config = new Config()
                .addMapConfig(mapConfig)
                .setClusterName(randomName())
                .setLicenseKey(env.get("HZ_LICENSEKEY"));

        HazelcastInstance hz = Hazelcast.newHazelcastInstance(config);
        return hz.getMap(mapName);
    }
}
