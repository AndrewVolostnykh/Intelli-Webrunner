package com.intelli.webrunner.kafka;

public class KafkaListenRequest {
    public String bootstrapServers;
    public String topic;
    public String groupId;
    public String offsetStrategy;
}
