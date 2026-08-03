package com.non_organic_onion.intelli.webrunner.kafka;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListTopicsOptions;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.clients.CommonClientConfigs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class KafkaMetadataService {
    private static final int TIMEOUT_MS = 5_000;

    public List<String> listTopics(String bootstrapServers) {
        if (bootstrapServers == null || bootstrapServers.isBlank()) {
            return List.of();
        }
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers.trim());
        properties.put(CommonClientConfigs.CLIENT_ID_CONFIG, "intelli-webrunner-metadata");
        properties.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(TIMEOUT_MS));
        properties.put(CommonClientConfigs.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(TIMEOUT_MS));

        try (AdminClient client = AdminClient.create(properties)) {
            ListTopicsOptions options = new ListTopicsOptions()
                .listInternal(false)
                .timeoutMs(TIMEOUT_MS);
            List<String> topics = new ArrayList<>();
            for (TopicListing listing : client.listTopics(options).listings().get(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                topics.add(listing.name());
            }
            topics.sort(Comparator.naturalOrder());
            return topics;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to load Kafka topics: " + error.getMessage(), error);
        }
    }
}
