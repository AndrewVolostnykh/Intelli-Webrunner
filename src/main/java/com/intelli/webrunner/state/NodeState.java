package com.intelli.webrunner.state;

public class NodeState {
    public String id;
    public String name;
    public NodeType type;
    public RequestType requestType;
    public String parentId;
    public int order;

    @Override
    public String toString() {
        return name == null ? "" : name;
    }
}
