package com.non_organic_onion.intelli.webrunner.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class KafkaMessageProducer {
    private final ObjectMapper mapper = new ObjectMapper();

    public KafkaSendResult send(KafkaSendRequest request) {
        validate(request);
        byte[] key = encode(request.key, request.keyType, true);
        byte[] value = encode(request.body, request.bodyType, false);
        Integer partition = parsePartition(request.partition);
        int timeoutMillis = Math.max(0, request.timeoutMillis);

        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, request.bootstrapServers.trim());
		properties.put(CommonClientConfigs.CLIENT_ID_CONFIG, "intelli-webrunner-producer");
        if (timeoutMillis > 0) {
		    properties.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(timeoutMillis));
		    properties.put(CommonClientConfigs.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(timeoutMillis));
        }

		ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(request.topic.trim(), partition, key, value);
		int headerCount = addHeaders(record, request);
		try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(
			properties,
			new ByteArraySerializer(),
			new ByteArraySerializer()
		)) {
            RecordMetadata metadata = timeoutMillis > 0
                ? producer.send(record).get(timeoutMillis, TimeUnit.MILLISECONDS)
                : producer.send(record).get();
            return new KafkaSendResult(
                metadata.topic(),
                metadata.partition(),
                metadata.offset(),
                metadata.timestamp(),
                key == null ? 0 : key.length,
                value == null ? 0 : value.length,
                headerCount
            );
        } catch (Exception error) {
            throw new IllegalStateException("Failed to send Kafka message: " + error.getMessage(), error);
        }
    }

    private void validate(KafkaSendRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing Kafka request.");
        }
        if (request.bootstrapServers == null || request.bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("Missing Kafka bootstrap servers.");
        }
        if (request.topic == null || request.topic.isBlank()) {
            throw new IllegalArgumentException("Missing Kafka topic.");
        }
    }

    private int addHeaders(ProducerRecord<byte[], byte[]> record, KafkaSendRequest request) {
        if (request.headers == null) {
            return 0;
        }
        int count = 0;
        for (HeaderEntryState header : request.headers) {
            if (header == null || !header.enabled || header.name == null || header.name.isBlank()) {
                continue;
            }
            record.headers().add(new RecordHeader(header.name.trim(), encodeBytes(header.value)));
            count++;
        }
        return count;
    }

    private byte[] encode(
        String value,
        String type,
        boolean nullableBlank
    ) {
        if (value == null || (nullableBlank && value.isBlank())) {
            return null;
        }
        String normalized = type == null || type.isBlank() ? "String" : type.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "json" -> encodeJson(value);
            case "bytes" -> encodeBytes(value);
            case "integer" -> ByteBuffer.allocate(Integer.BYTES).putInt(Integer.parseInt(value.trim())).array();
            case "long" -> ByteBuffer.allocate(Long.BYTES).putLong(Long.parseLong(value.trim())).array();
            case "uuid" -> encodeUuid(value);
            default -> value.getBytes(StandardCharsets.UTF_8);
        };
    }

    private byte[] encodeJson(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        try {
            return mapper.writeValueAsBytes(mapper.readTree(value));
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid JSON payload: " + error.getMessage(), error);
        }
    }

    private byte[] encodeBytes(String value) {
        if (value == null) {
            return new byte[0];
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("base64:")) {
            return Base64.getDecoder().decode(trimmed.substring("base64:".length()).trim());
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] encodeUuid(String value) {
        UUID uuid = UUID.fromString(value.trim());
        return ByteBuffer.allocate(16)
            .putLong(uuid.getMostSignificantBits())
            .putLong(uuid.getLeastSignificantBits())
            .array();
    }

    private Integer parsePartition(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }
}
