package com.non_organic_onion.intelli.webrunner.kafka;

import java.util.List;

public class KafkaListenMessage {
    public String topic;
    public int partition;
    public long offset;
    public long timestamp;
    public String key;
    public String body;
    public List<Header> headers;

    public static class Header {
        public String name;
        public String value;

        public Header(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }
}
