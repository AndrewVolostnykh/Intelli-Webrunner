package com.intelli.webrunner.kafka;

import com.intelli.webrunner.state.HeaderEntryState;

import java.util.List;

public class KafkaSendRequest {
    public String bootstrapServers;
    public String topic;
    public String key;
    public String keyType;
    public String body;
    public String bodyType;
    public String partition;
    public List<HeaderEntryState> headers = List.of();
}
