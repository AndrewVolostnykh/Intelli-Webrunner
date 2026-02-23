package com.intelli.webrunner.grpc;

import java.util.ArrayList;
import java.util.List;

public class GrpcServiceInfo {
    public String name;
    public List<String> methods = new ArrayList<>();

    public GrpcServiceInfo() {
    }

    public GrpcServiceInfo(String name, List<String> methods) {
        this.name = name;
        this.methods = methods;
    }
}
