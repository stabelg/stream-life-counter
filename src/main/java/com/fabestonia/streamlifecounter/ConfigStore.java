package com.fabestonia.streamlifecounter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.*;

@Component
public class ConfigStore {

    private static final File FILE = new File("stream-config.json");
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> cache = new LinkedHashMap<>();

    @PostConstruct
    public void load() {
        try {
            if (FILE.exists()) {
                Map<String, String> m = mapper.readValue(FILE, new TypeReference<>() {});
                if (m != null) cache.putAll(m);
            }
        } catch (Exception ignored) {}
    }

    public String get(String key)                  { return cache.get(key); }
    public String get(String key, String fallback) { return cache.getOrDefault(key, fallback); }

    public void set(String key, String value) {
        if (value == null || value.isBlank()) cache.remove(key);
        else cache.put(key, value);
        persist();
    }

    public Map<String, String> all() { return Collections.unmodifiableMap(cache); }

    private void persist() {
        try { mapper.writerWithDefaultPrettyPrinter().writeValue(FILE, cache); }
        catch (Exception ignored) {}
    }
}
