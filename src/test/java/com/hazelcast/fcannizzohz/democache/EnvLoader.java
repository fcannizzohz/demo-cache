package com.hazelcast.fcannizzohz.democache;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class EnvLoader {

    public static Map<String, String> load(String path) throws IOException {
        Map<String, String> env = new HashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(path));

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;

            String[] parts = trimmed.split("=", 2);
            if (parts.length == 2) {
                env.put(parts[0].trim(), parts[1].trim());
            }
        }
        return env;
    }
}
