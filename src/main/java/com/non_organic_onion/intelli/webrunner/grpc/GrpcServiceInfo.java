package com.non_organic_onion.intelli.webrunner.grpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GrpcServiceInfo {
    public String name;
    public List<String> methods = new ArrayList<>();
    public Map<String, String> methodStreamingKinds = new HashMap<>();

    public GrpcServiceInfo() {
    }

    public GrpcServiceInfo(String name, List<String> methods) {
        this.name = name;
        this.methods = methods;
    }

    public GrpcServiceInfo(String name, List<String> methods, Map<String, String> methodStreamingKinds) {
        this.name = name;
        this.methods = methods;
        this.methodStreamingKinds = methodStreamingKinds == null ? new HashMap<>() : methodStreamingKinds;
    }
}
