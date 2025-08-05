package com.hazelcast.fcannizzohz.democache;

import com.hazelcast.config.Config;

import java.io.IOException;
import java.util.Map;

import static com.hazelcast.test.HazelcastTestSupport.randomName;

public class TestConfig {
    public static Config newTestConfig() {
        Map<String, String> env;
        try {
            env = EnvLoader.load(".env");
        } catch (IOException e) {
            throw new RuntimeException("Unable to load env file.", e);
        }

        return new Config().setClusterName(randomName()).setLicenseKey(env.get("HZ_LICENSEKEY"));

    }
}
