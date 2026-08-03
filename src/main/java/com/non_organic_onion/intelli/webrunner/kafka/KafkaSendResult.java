package com.non_organic_onion.intelli.webrunner.kafka;

public class KafkaSendResult {
    public final String topic;
    public final int partition;
    public final long offset;
    public final long timestamp;
    public final int keyBytes;
    public final int valueBytes;
    public final int headers;

    public KafkaSendResult(
        String topic,
        int partition,
        long offset,
        long timestamp,
        int keyBytes,
        int valueBytes,
        int headers
    ) {
        this.topic = topic;
        this.partition = partition;
        this.offset = offset;
        this.timestamp = timestamp;
        this.keyBytes = keyBytes;
        this.valueBytes = valueBytes;
        this.headers = headers;
    }
}
