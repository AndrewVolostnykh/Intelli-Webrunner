package com.intelli.webrunner.script;

import java.util.HashMap;
import java.util.Map;

public class VarsStore {
    private final Map<String, Object> store = new HashMap<>();

    public void add(String name, Object value) {
        store.put(name, value);
    }

    public Object get(String name) {
        return store.get(name);
    }

    public Map<String, Object> entries() {
        return new HashMap<>(store);
    }
}
