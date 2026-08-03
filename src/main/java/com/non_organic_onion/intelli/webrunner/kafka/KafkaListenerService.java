package com.non_organic_onion.intelli.webrunner.kafka;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class KafkaListenerService {
    private static final int TIMEOUT_MS = 10_000;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Map<String, ListenerSession> sessions = new ConcurrentHashMap<>();

    public void start(
        String listenerId,
        KafkaListenRequest request,
        Consumer<KafkaListenMessage> onMessage,
        Consumer<Throwable> onError
    ) {
        validate(listenerId, request);
        stop(listenerId);
        ListenerSession session = new ListenerSession();
        sessions.put(listenerId, session);
        executor.submit(() -> listen(listenerId, request, session, onMessage, onError));
    }

    public void stop(String listenerId) {
        ListenerSession session = sessions.remove(listenerId);
        if (session == null) {
            return;
        }
        session.running.set(false);
        KafkaConsumer<byte[], byte[]> consumer = session.consumer;
        if (consumer != null) {
            consumer.wakeup();
        }
    }

    public boolean isListening(String listenerId) {
        return sessions.containsKey(listenerId);
    }

    public void shutdown() {
        for (String listenerId : List.copyOf(sessions.keySet())) {
            stop(listenerId);
        }
        executor.shutdownNow();
    }

    private void listen(
        String listenerId,
        KafkaListenRequest request,
        ListenerSession session,
        Consumer<KafkaListenMessage> onMessage,
        Consumer<Throwable> onError
    ) {
        Properties properties = consumerProperties(request);
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(
            properties,
            new ByteArrayDeserializer(),
            new ByteArrayDeserializer()
        )) {
            session.consumer = consumer;
            consumer.subscribe(List.of(request.topic.trim()));
            applyInitialOffset(consumer, request.offsetStrategy);
            while (session.running.get()) {
                ConsumerRecords<byte[], byte[]> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<byte[], byte[]> record : records) {
                    if (!session.running.get()) {
                        break;
                    }
                    onMessage.accept(toMessage(record));
                }
            }
        } catch (org.apache.kafka.common.errors.WakeupException ignored) {
        } catch (Throwable error) {
            if (session.running.get() && onError != null) {
                onError.accept(error);
            }
        } finally {
            sessions.remove(listenerId, session);
        }
    }

    private Properties consumerProperties(KafkaListenRequest request) {
        Properties properties = new Properties();
        properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, request.bootstrapServers.trim());
        properties.put(CommonClientConfigs.CLIENT_ID_CONFIG, "intelli-webrunner-listener");
        properties.put(CommonClientConfigs.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(TIMEOUT_MS));
        properties.put(CommonClientConfigs.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(TIMEOUT_MS));
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, request.groupId.trim());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset(request.offsetStrategy));
        return properties;
    }

    private void applyInitialOffset(
        KafkaConsumer<byte[], byte[]> consumer,
        String offsetStrategy
    ) {
        ConsumerRecords<byte[], byte[]> ignored = consumer.poll(Duration.ofMillis(1000));
        Collection<TopicPartition> assignments = consumer.assignment();
        if (assignments.isEmpty()) {
            return;
        }
        String normalized = normalizeOffsetStrategy(offsetStrategy);
        if ("earliest".equals(normalized)) {
            consumer.seekToBeginning(assignments);
        } else if ("latest".equals(normalized)) {
            consumer.seekToEnd(assignments);
        }
        if (!ignored.isEmpty()) {
            consumer.poll(Duration.ZERO);
        }
    }

    private KafkaListenMessage toMessage(ConsumerRecord<byte[], byte[]> record) {
        KafkaListenMessage message = new KafkaListenMessage();
        message.topic = record.topic();
        message.partition = record.partition();
        message.offset = record.offset();
        message.timestamp = record.timestamp();
        message.key = decode(record.key());
        message.body = decode(record.value());
        List<KafkaListenMessage.Header> headers = new ArrayList<>();
        record.headers().forEach(header -> headers.add(new KafkaListenMessage.Header(header.key(), decode(header.value()))));
        message.headers = headers;
        return message;
    }

    private String decode(byte[] bytes) {
        return bytes == null ? "" : new String(bytes, StandardCharsets.UTF_8);
    }

    private String autoOffsetReset(String offsetStrategy) {
        String normalized = normalizeOffsetStrategy(offsetStrategy);
        return "earliest".equals(normalized) ? "earliest" : "latest";
    }

    private String normalizeOffsetStrategy(String offsetStrategy) {
        return offsetStrategy == null ? "latest" : offsetStrategy.trim().toLowerCase(Locale.ROOT);
    }

    private void validate(
        String listenerId,
        KafkaListenRequest request
    ) {
        if (listenerId == null || listenerId.isBlank()) {
            throw new IllegalArgumentException("Missing Kafka listener id.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Missing Kafka listen request.");
        }
        if (request.bootstrapServers == null || request.bootstrapServers.isBlank()) {
            throw new IllegalArgumentException("Missing Kafka bootstrap servers.");
        }
        if (request.topic == null || request.topic.isBlank()) {
            throw new IllegalArgumentException("Missing Kafka topic.");
        }
        if (request.groupId == null || request.groupId.isBlank()) {
            throw new IllegalArgumentException("Missing Kafka group id.");
        }
    }

    private static final class ListenerSession {
        private final AtomicBoolean running = new AtomicBoolean(true);
        private volatile KafkaConsumer<byte[], byte[]> consumer;
    }
}
