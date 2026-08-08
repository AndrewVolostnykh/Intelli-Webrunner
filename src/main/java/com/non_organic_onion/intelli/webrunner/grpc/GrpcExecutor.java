package com.non_organic_onion.intelli.webrunner.grpc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import io.grpc.*;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.reflection.v1alpha.ServerReflectionGrpc;
import io.grpc.reflection.v1alpha.ServerReflectionRequest;
import io.grpc.reflection.v1alpha.ServerReflectionResponse;
import io.grpc.reflection.v1alpha.ServiceResponse;
import io.grpc.stub.ClientCalls;
import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class GrpcExecutor {
    private static final String REFLECTION_SERVICE = "grpc.reflection.v1alpha.ServerReflection";
    private static final int DEFAULT_TIMEOUT_MILLIS = 0;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<GrpcServiceInfo> listServices(String target) {
        if (target == null || target.isBlank()) {
            return List.of();
        }
        ManagedChannel channel = null;
        try {
            channel = createChannel(target);
            ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
            ServerReflectionRequest listRequest = ServerReflectionRequest.newBuilder()
                    .setListServices("*")
                    .build();
            ServerReflectionResponse response = blockingReflectionCall(stub, listRequest);
            List<String> serviceNames = response.getListServicesResponse().getServiceList().stream()
                    .map(ServiceResponse::getName)
                    .filter(name -> !REFLECTION_SERVICE.equals(name))
                    .sorted()
                    .toList();

            List<GrpcServiceInfo> services = new ArrayList<>();
            for (String serviceName : serviceNames) {
                Optional<Descriptors.ServiceDescriptor> descriptor = fetchServiceDescriptor(stub, serviceName);
                if (descriptor.isEmpty()) {
                    continue;
                }
                List<String> methods = descriptor.get().getMethods().stream()
                        .map(Descriptors.MethodDescriptor::getName)
                        .sorted()
                        .toList();
                Map<String, String> methodStreamingKinds = new HashMap<>();
                for (Descriptors.MethodDescriptor method : descriptor.get().getMethods()) {
                    methodStreamingKinds.put(method.getName(), streamingKind(method).name());
                }
                services.add(new GrpcServiceInfo(serviceName, methods, methodStreamingKinds));
            }
            services.sort(Comparator.comparing(info -> info.name));
            return services;
        } catch (Exception error) {
            throw new IllegalStateException("Failed to load gRPC services: " + error.getMessage(), error);
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
        }
    }

    public GrpcExecutionResponse execute(String target, String service, String method, String payload, List<HeaderEntryState> metadata) {
        return execute(target, service, method, payload, metadata, DEFAULT_TIMEOUT_MILLIS);
    }

    public boolean isClientStreaming(
            String target,
            String service,
            String method
    ) {
        return streamingKind(target, service, method) == StreamingKind.CLIENT;
    }

    public boolean isBidirectionalStreaming(
            String target,
            String service,
            String method
    ) {
        return streamingKind(target, service, method) == StreamingKind.BIDI;
    }

    private StreamingKind streamingKind(
            String target,
            String service,
            String method
    ) {
        ManagedChannel channel = null;
        try {
            channel = createChannel(target);
            ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
            Descriptors.ServiceDescriptor serviceDescriptor = fetchServiceDescriptor(stub, service)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown service"));
            Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(method);
            if (methodDescriptor == null) {
                throw new IllegalArgumentException("Unknown method");
            }
            return streamingKind(methodDescriptor);
        } catch (Exception error) {
            throw new IllegalArgumentException("Failed to inspect gRPC method", error);
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
        }
    }

    private StreamingKind streamingKind(Descriptors.MethodDescriptor methodDescriptor) {
        if (methodDescriptor.isClientStreaming() && methodDescriptor.isServerStreaming()) {
            return StreamingKind.BIDI;
        }
        if (methodDescriptor.isClientStreaming()) {
            return StreamingKind.CLIENT;
        }
        if (methodDescriptor.isServerStreaming()) {
            return StreamingKind.SERVER;
        }
        return StreamingKind.UNARY;
    }

    private enum StreamingKind {
        UNARY,
        CLIENT,
        SERVER,
        BIDI
    }

    public ClientStreamingCall openClientStreaming(
            String target,
            String service,
            String method,
            List<HeaderEntryState> metadata,
            int timeoutMillis
    ) {
        ManagedChannel channel = null;
        try {
            channel = createChannel(target);
            ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
            Descriptors.ServiceDescriptor serviceDescriptor = fetchServiceDescriptor(stub, service)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown service"));
            Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(method);
            if (methodDescriptor == null) {
                throw new IllegalArgumentException("Unknown method");
            }
            if (!methodDescriptor.isClientStreaming() || methodDescriptor.isServerStreaming()) {
                throw new IllegalArgumentException("Method is not client streaming.");
            }
            Metadata requestHeaders = toMetadata(metadata);
            MetadataCapture capture = new MetadataCapture();
            Channel intercepted = ClientInterceptors.intercept(channel, capture.interceptor(), metadataInterceptor(requestHeaders));
            MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod =
                    MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                            .setType(MethodDescriptor.MethodType.CLIENT_STREAMING)
                            .setFullMethodName(MethodDescriptor.generateFullMethodName(
                                    serviceDescriptor.getFullName(),
                                    methodDescriptor.getName()
                            ))
                            .setRequestMarshaller(ProtoUtils.marshaller(
                                    DynamicMessage.getDefaultInstance(methodDescriptor.getInputType())
                            ))
                            .setResponseMarshaller(ProtoUtils.marshaller(
                                    DynamicMessage.getDefaultInstance(methodDescriptor.getOutputType())
                            ))
                            .build();
            return new ClientStreamingCall(channel, intercepted, grpcMethod, methodDescriptor, capture, timeoutMillis);
        } catch (Exception error) {
            if (channel != null) {
                channel.shutdownNow();
            }
            throw new IllegalArgumentException("Failed to open gRPC client stream", error);
        }
    }

    public BidirectionalStreamingCall openBidirectionalStreaming(
            String target,
            String service,
            String method,
            List<HeaderEntryState> metadata,
            int timeoutMillis,
            Consumer<GrpcExecutionResponse> serverMessageConsumer
    ) {
        ManagedChannel channel = null;
        try {
            channel = createChannel(target);
            ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
            Descriptors.ServiceDescriptor serviceDescriptor = fetchServiceDescriptor(stub, service)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown service"));
            Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(method);
            if (methodDescriptor == null) {
                throw new IllegalArgumentException("Unknown method");
            }
            if (!methodDescriptor.isClientStreaming() || !methodDescriptor.isServerStreaming()) {
                throw new IllegalArgumentException("Method is not bidirectional streaming.");
            }
            Metadata requestHeaders = toMetadata(metadata);
            MetadataCapture capture = new MetadataCapture();
            Channel intercepted = ClientInterceptors.intercept(channel, capture.interceptor(), metadataInterceptor(requestHeaders));
            MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod =
                    MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                            .setType(MethodDescriptor.MethodType.BIDI_STREAMING)
                            .setFullMethodName(MethodDescriptor.generateFullMethodName(
                                    serviceDescriptor.getFullName(),
                                    methodDescriptor.getName()
                            ))
                            .setRequestMarshaller(ProtoUtils.marshaller(
                                    DynamicMessage.getDefaultInstance(methodDescriptor.getInputType())
                            ))
                            .setResponseMarshaller(ProtoUtils.marshaller(
                                    DynamicMessage.getDefaultInstance(methodDescriptor.getOutputType())
                            ))
                            .build();
            return new BidirectionalStreamingCall(
                    channel,
                    intercepted,
                    grpcMethod,
                    methodDescriptor,
                    capture,
                    timeoutMillis,
                    serverMessageConsumer
            );
        } catch (Exception error) {
            if (channel != null) {
                channel.shutdownNow();
            }
            throw new IllegalArgumentException("Failed to open gRPC bidirectional stream", error);
        }
    }

    public GrpcExecutionResponse execute(
            String target,
            String service,
            String method,
            String payload,
            List<HeaderEntryState> metadata,
            int timeoutMillis
    ) {
        return execute(target, service, method, payload, metadata, timeoutMillis, null);
    }

    public GrpcExecutionResponse execute(
            String target,
            String service,
            String method,
            String payload,
            List<HeaderEntryState> metadata,
            int timeoutMillis,
            Consumer<GrpcExecutionResponse> serverMessageConsumer
    ) {
        ManagedChannel channel = null;
        try {
            channel = createChannel(target);
            ServerReflectionGrpc.ServerReflectionStub stub = ServerReflectionGrpc.newStub(channel);
            Descriptors.ServiceDescriptor serviceDescriptor = fetchServiceDescriptor(stub, service)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown service"));
            Descriptors.MethodDescriptor methodDescriptor = serviceDescriptor.findMethodByName(method);
            if (methodDescriptor == null) {
                throw new IllegalArgumentException("Unknown method");
            }

            Metadata requestHeaders = toMetadata(metadata);
            MetadataCapture capture = new MetadataCapture();
            Channel intercepted = ClientInterceptors.intercept(channel, capture.interceptor(), metadataInterceptor(requestHeaders));
            MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod =
                    MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                            .setType(methodType(methodDescriptor))
                            .setFullMethodName(MethodDescriptor.generateFullMethodName(
                                    serviceDescriptor.getFullName(),
                                    methodDescriptor.getName()
                            ))
                            .setRequestMarshaller(ProtoUtils.marshaller(
                                    DynamicMessage.getDefaultInstance(methodDescriptor.getInputType())
                            ))
                            .setResponseMarshaller(ProtoUtils.marshaller(
                                    DynamicMessage.getDefaultInstance(methodDescriptor.getOutputType())
                            ))
                            .build();

            DynamicMessage responseMessage;
            if (methodDescriptor.isClientStreaming() && !methodDescriptor.isServerStreaming()) {
                responseMessage = executeClientStreaming(
                        intercepted,
                        grpcMethod,
                        methodDescriptor,
                        payload,
                        timeoutMillis
                );
            } else if (methodDescriptor.isClientStreaming() && methodDescriptor.isServerStreaming()) {
                List<DynamicMessage> responseMessages = executeBidirectionalStreaming(
                        intercepted,
                        grpcMethod,
                        methodDescriptor,
                        payload,
                        timeoutMillis,
                        capture,
                        serverMessageConsumer
                );
                return new GrpcExecutionResponse(
                        Status.OK.getCode().value(),
                        Status.OK.getCode().name(),
                        metadataToMap(capture.headers.get()),
                        formatResponseMessages(responseMessages),
                        true
                );
            } else if (!methodDescriptor.isClientStreaming() && methodDescriptor.isServerStreaming()) {
                List<DynamicMessage> responseMessages = executeServerStreaming(
                        intercepted,
                        grpcMethod,
                        methodDescriptor,
                        payload,
                        timeoutMillis,
                        capture,
                        serverMessageConsumer
                );
                return new GrpcExecutionResponse(
                        Status.OK.getCode().value(),
                        Status.OK.getCode().name(),
                        metadataToMap(capture.headers.get()),
                        formatResponseMessages(responseMessages),
                        true
                );
            } else if (!methodDescriptor.isClientStreaming() && !methodDescriptor.isServerStreaming()) {
                DynamicMessage requestMessage = buildRequestMessage(methodDescriptor, payload);
                responseMessage = ClientCalls.blockingUnaryCall(
                        intercepted,
                        grpcMethod,
                        callOptions(timeoutMillis),
                        requestMessage
                );
            } else {
                throw new IllegalArgumentException("Unsupported gRPC streaming method type.");
            }

            String responseJson = formatResponseMessage(responseMessage);
            return new GrpcExecutionResponse(
                    Status.OK.getCode().value(),
                    Status.OK.getCode().name(),
                    metadataToMap(capture.headers.get()),
                    responseJson
            );
        } catch (StatusRuntimeException error) {
            Status status = error.getStatus();
            return new GrpcExecutionResponse(
                    status.getCode().value(),
                    status.getCode().name(),
                    metadataToMap(error.getTrailers()),
                    status.getDescription() == null ? "" : status.getDescription()
            );
        } catch (Exception error) {
            throw new IllegalArgumentException("Failed to execute gRPC request", error);
        } finally {
            if (channel != null) {
                channel.shutdownNow();
            }
        }
    }

    private ManagedChannel createChannel(String target) {
        String normalized = normalizeTarget(target);
        return NettyChannelBuilder.forTarget(normalized).usePlaintext().build();
    }

    private CallOptions callOptions(int timeoutMillis) {
        if (timeoutMillis <= 0) {
            return CallOptions.DEFAULT;
        }
        return CallOptions.DEFAULT.withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private MethodDescriptor.MethodType methodType(Descriptors.MethodDescriptor methodDescriptor) {
        if (methodDescriptor.isClientStreaming() && methodDescriptor.isServerStreaming()) {
            return MethodDescriptor.MethodType.BIDI_STREAMING;
        }
        if (methodDescriptor.isClientStreaming()) {
            return MethodDescriptor.MethodType.CLIENT_STREAMING;
        }
        if (methodDescriptor.isServerStreaming()) {
            return MethodDescriptor.MethodType.SERVER_STREAMING;
        }
        return MethodDescriptor.MethodType.UNARY;
    }

    private String normalizeTarget(String target) {
        String trimmed = target.trim();
        if (trimmed.startsWith("http://")) {
            return trimmed.substring("http://".length());
        }
        if (trimmed.startsWith("https://")) {
            return trimmed.substring("https://".length());
        }
        return trimmed;
    }

    private ServerReflectionResponse blockingReflectionCall(
            ServerReflectionGrpc.ServerReflectionStub stub,
            ServerReflectionRequest request
    ) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ServerReflectionResponse> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        StreamObserver<ServerReflectionResponse> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(ServerReflectionResponse value) {
                responseRef.set(value);
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };
        StreamObserver<ServerReflectionRequest> requestObserver = stub.serverReflectionInfo(responseObserver);
        requestObserver.onNext(request);
        requestObserver.onCompleted();
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Reflection request timed out");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reflection request interrupted", error);
        }
        if (errorRef.get() != null) {
            throw new IllegalStateException("Reflection request failed", errorRef.get());
        }
        ServerReflectionResponse response = responseRef.get();
        if (response == null) {
            throw new IllegalStateException("Missing reflection response");
        }
        return response;
    }

    private Optional<Descriptors.ServiceDescriptor> fetchServiceDescriptor(
            ServerReflectionGrpc.ServerReflectionStub stub,
            String serviceName
    ) throws InvalidProtocolBufferException, Descriptors.DescriptorValidationException {
        ServerReflectionRequest request = ServerReflectionRequest.newBuilder()
                .setFileContainingSymbol(serviceName)
                .build();
        ServerReflectionResponse response = blockingReflectionCall(stub, request);
        List<ByteString> descriptorData = response.getFileDescriptorResponse().getFileDescriptorProtoList();
        if (descriptorData.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Descriptors.FileDescriptor> descriptors = buildFileDescriptors(descriptorData);
        for (Descriptors.FileDescriptor descriptor : descriptors.values()) {
            for (Descriptors.ServiceDescriptor service : descriptor.getServices()) {
                if (service.getFullName().equals(serviceName)) {
                    return Optional.of(service);
                }
            }
        }
        return Optional.empty();
    }

    private Map<String, Descriptors.FileDescriptor> buildFileDescriptors(List<ByteString> descriptorData)
            throws InvalidProtocolBufferException, Descriptors.DescriptorValidationException {
        Map<String, DescriptorProtos.FileDescriptorProto> protoByName = new HashMap<>();
        for (ByteString data : descriptorData) {
            DescriptorProtos.FileDescriptorProto proto = DescriptorProtos.FileDescriptorProto.parseFrom(data);
            protoByName.put(proto.getName(), proto);
        }
        Map<String, Descriptors.FileDescriptor> descriptorMap = new HashMap<>();
        for (String name : protoByName.keySet()) {
            buildFileDescriptor(name, protoByName, descriptorMap);
        }
        return descriptorMap;
    }

    private Descriptors.FileDescriptor buildFileDescriptor(
            String name,
            Map<String, DescriptorProtos.FileDescriptorProto> protoByName,
            Map<String, Descriptors.FileDescriptor> descriptorMap
    ) throws Descriptors.DescriptorValidationException {
        Descriptors.FileDescriptor cached = descriptorMap.get(name);
        if (cached != null) {
            return cached;
        }
        DescriptorProtos.FileDescriptorProto proto = protoByName.get(name);
        if (proto == null) {
            throw new IllegalStateException("Missing proto for " + name);
        }
        List<Descriptors.FileDescriptor> dependencies = new ArrayList<>();
        for (String dep : proto.getDependencyList()) {
            if (!protoByName.containsKey(dep)) {
                continue;
            }
            dependencies.add(buildFileDescriptor(dep, protoByName, descriptorMap));
        }
        Descriptors.FileDescriptor descriptor = Descriptors.FileDescriptor.buildFrom(
                proto,
                dependencies.toArray(new Descriptors.FileDescriptor[0])
        );
        descriptorMap.put(name, descriptor);
        return descriptor;
    }

    private DynamicMessage buildRequestMessage(
            Descriptors.MethodDescriptor method,
            String payload
    ) throws InvalidProtocolBufferException {
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(method.getInputType());
        if (payload != null && !payload.isBlank()) {
            JsonFormat.parser().ignoringUnknownFields().merge(payload, builder);
        }
        return builder.build();
    }

    private List<DynamicMessage> buildRequestMessages(
            Descriptors.MethodDescriptor method,
            String payload
    ) throws InvalidProtocolBufferException {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = mapper.readTree(payload);
            JsonNode messages = root.isObject() && root.has("messages") && root.get("messages").isArray()
                    ? root.get("messages")
                    : root;
            if (messages.isArray()) {
                List<DynamicMessage> result = new ArrayList<>();
                for (JsonNode message : messages) {
                    result.add(buildRequestMessage(method, message.toString()));
                }
                return result;
            }
        } catch (Exception ignored) {
            // Fall back to parsing the whole payload as a single protobuf JSON message.
        }
        return List.of(buildRequestMessage(method, payload));
    }

    private DynamicMessage executeClientStreaming(
            Channel channel,
            MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod,
            Descriptors.MethodDescriptor methodDescriptor,
            String payload,
            int timeoutMillis
    ) throws InvalidProtocolBufferException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<DynamicMessage> responseRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        StreamObserver<DynamicMessage> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(DynamicMessage value) {
                responseRef.set(value);
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };
        StreamObserver<DynamicMessage> requestObserver = ClientCalls.asyncClientStreamingCall(
                channel.newCall(grpcMethod, callOptions(timeoutMillis)),
                responseObserver
        );
        try {
            for (DynamicMessage message : buildRequestMessages(methodDescriptor, payload)) {
                requestObserver.onNext(message);
            }
            requestObserver.onCompleted();
        } catch (RuntimeException error) {
            requestObserver.onError(error);
            throw error;
        }
        awaitClientStreamingResponse(latch, timeoutMillis);
        Throwable error = errorRef.get();
        if (error instanceof StatusRuntimeException statusError) {
            throw statusError;
        }
        if (error != null) {
            throw new IllegalArgumentException("Client streaming call failed", error);
        }
        DynamicMessage response = responseRef.get();
        if (response == null) {
            throw new IllegalStateException("Client streaming call completed without a response.");
        }
        return response;
    }

    private List<DynamicMessage> executeServerStreaming(
            Channel channel,
            MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod,
            Descriptors.MethodDescriptor methodDescriptor,
            String payload,
            int timeoutMillis,
            MetadataCapture capture,
            Consumer<GrpcExecutionResponse> serverMessageConsumer
    ) throws InvalidProtocolBufferException {
        CountDownLatch latch = new CountDownLatch(1);
        List<DynamicMessage> responses = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        StreamObserver<DynamicMessage> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(DynamicMessage value) {
                responses.add(value);
                if (serverMessageConsumer != null) {
                    serverMessageConsumer.accept(new GrpcExecutionResponse(
                            Status.OK.getCode().value(),
                            Status.OK.getCode().name(),
                            metadataToMap(capture.headers.get()),
                            formatResponseMessage(value),
                            true
                    ));
                }
            }

            @Override
            public void onError(Throwable t) {
                errorRef.set(t);
                latch.countDown();
            }

            @Override
            public void onCompleted() {
                latch.countDown();
            }
        };
        DynamicMessage requestMessage = buildRequestMessage(methodDescriptor, payload);
        ClientCalls.asyncServerStreamingCall(
                channel.newCall(grpcMethod, callOptions(timeoutMillis)),
                requestMessage,
                responseObserver
        );
        awaitServerStreamingResponse(latch, timeoutMillis);
        Throwable error = errorRef.get();
        if (error instanceof StatusRuntimeException statusError) {
            throw statusError;
        }
        if (error != null) {
            throw new IllegalArgumentException("Server streaming call failed", error);
        }
        return new ArrayList<>(responses);
    }

    private List<DynamicMessage> executeBidirectionalStreaming(
            Channel channel,
            MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod,
            Descriptors.MethodDescriptor methodDescriptor,
            String payload,
            int timeoutMillis,
            MetadataCapture capture,
            Consumer<GrpcExecutionResponse> serverMessageConsumer
    ) throws InvalidProtocolBufferException {
        BidirectionalStreamingCall call = new BidirectionalStreamingCall(
                null,
                channel,
                grpcMethod,
                methodDescriptor,
                capture,
                timeoutMillis,
                serverMessageConsumer
        );
        for (DynamicMessage message : buildRequestMessages(methodDescriptor, payload)) {
            call.send(message);
        }
        return call.completeMessages();
    }

    private void awaitClientStreamingResponse(
            CountDownLatch latch,
            int timeoutMillis
    ) {
        try {
            boolean completed = timeoutMillis > 0
                    ? latch.await(timeoutMillis + 1000L, TimeUnit.MILLISECONDS)
                    : await(latch);
            if (!completed) {
                throw new IllegalStateException("Client streaming call timed out.");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Client streaming call interrupted", error);
        }
    }

    private boolean await(CountDownLatch latch) throws InterruptedException {
        latch.await();
        return true;
    }

    private void awaitServerStreamingResponse(
            CountDownLatch latch,
            int timeoutMillis
    ) {
        try {
            boolean completed = timeoutMillis > 0
                    ? latch.await(timeoutMillis + 1000L, TimeUnit.MILLISECONDS)
                    : await(latch);
            if (!completed) {
                throw new IllegalStateException("Server streaming call timed out.");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Server streaming call interrupted", error);
        }
    }

    public interface GrpcStreamingCall {
        void send(String payload) throws InvalidProtocolBufferException;
        GrpcExecutionResponse complete();
        void cancel();
    }

    public final class ClientStreamingCall implements GrpcStreamingCall {
        private final ManagedChannel channel;
        private final Descriptors.MethodDescriptor methodDescriptor;
        private final MetadataCapture capture;
        private final int timeoutMillis;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<DynamicMessage> responseRef = new AtomicReference<>();
        private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        private final StreamObserver<DynamicMessage> requestObserver;
        private volatile boolean closed = false;

        private ClientStreamingCall(
                ManagedChannel channel,
                Channel intercepted,
                MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod,
                Descriptors.MethodDescriptor methodDescriptor,
                MetadataCapture capture,
                int timeoutMillis
        ) {
            this.channel = channel;
            this.methodDescriptor = methodDescriptor;
            this.capture = capture;
            this.timeoutMillis = timeoutMillis;
            StreamObserver<DynamicMessage> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(DynamicMessage value) {
                    responseRef.set(value);
                }

                @Override
                public void onError(Throwable t) {
                    errorRef.set(t);
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            };
            this.requestObserver = ClientCalls.asyncClientStreamingCall(
                    intercepted.newCall(grpcMethod, callOptions(timeoutMillis)),
                    responseObserver
            );
        }

        public void send(String payload) throws InvalidProtocolBufferException {
            if (closed) {
                throw new IllegalStateException("Client stream is already closed.");
            }
            requestObserver.onNext(buildRequestMessage(methodDescriptor, payload));
        }

        public GrpcExecutionResponse complete() {
            if (!closed) {
                closed = true;
                requestObserver.onCompleted();
            }
            try {
                awaitClientStreamingResponse(latch, timeoutMillis);
                Throwable error = errorRef.get();
                if (error instanceof StatusRuntimeException statusError) {
                    Status status = statusError.getStatus();
                    return new GrpcExecutionResponse(
                            status.getCode().value(),
                            status.getCode().name(),
                            metadataToMap(statusError.getTrailers()),
                            status.getDescription() == null ? "" : status.getDescription()
                    );
                }
                if (error != null) {
                    throw new IllegalArgumentException("Client streaming call failed", error);
                }
                DynamicMessage response = responseRef.get();
                return new GrpcExecutionResponse(
                        Status.OK.getCode().value(),
                        Status.OK.getCode().name(),
                        metadataToMap(capture.headers.get()),
                        formatResponseMessage(response)
                );
            } finally {
                channel.shutdownNow();
            }
        }

        public void cancel() {
            closed = true;
            try {
                requestObserver.onError(Status.CANCELLED.asRuntimeException());
            } catch (Exception ignored) {
            }
            channel.shutdownNow();
        }
    }

    public final class BidirectionalStreamingCall implements GrpcStreamingCall {
        private final ManagedChannel managedChannel;
        private final Descriptors.MethodDescriptor methodDescriptor;
        private final MetadataCapture capture;
        private final int timeoutMillis;
        private final CountDownLatch latch = new CountDownLatch(1);
        private final List<DynamicMessage> responses = Collections.synchronizedList(new ArrayList<>());
        private final AtomicReference<Throwable> errorRef = new AtomicReference<>();
        private final StreamObserver<DynamicMessage> requestObserver;
        private volatile boolean closed = false;

        private BidirectionalStreamingCall(
                ManagedChannel managedChannel,
                Channel channel,
                MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod,
                Descriptors.MethodDescriptor methodDescriptor,
                MetadataCapture capture,
                int timeoutMillis,
                Consumer<GrpcExecutionResponse> serverMessageConsumer
        ) {
            this.managedChannel = managedChannel;
            this.methodDescriptor = methodDescriptor;
            this.capture = capture;
            this.timeoutMillis = timeoutMillis;
            StreamObserver<DynamicMessage> responseObserver = new StreamObserver<>() {
                @Override
                public void onNext(DynamicMessage value) {
                    responses.add(value);
                    if (serverMessageConsumer != null) {
                        serverMessageConsumer.accept(new GrpcExecutionResponse(
                                Status.OK.getCode().value(),
                                Status.OK.getCode().name(),
                                metadataToMap(capture.headers.get()),
                                formatResponseMessage(value),
                                true
                        ));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    errorRef.set(t);
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                    latch.countDown();
                }
            };
            this.requestObserver = ClientCalls.asyncBidiStreamingCall(
                    channel.newCall(grpcMethod, callOptions(timeoutMillis)),
                    responseObserver
            );
        }

        @Override
        public void send(String payload) throws InvalidProtocolBufferException {
            send(buildRequestMessage(methodDescriptor, payload));
        }

        private void send(DynamicMessage message) {
            if (closed) {
                throw new IllegalStateException("Bidirectional stream is already closed.");
            }
            requestObserver.onNext(message);
        }

        @Override
        public GrpcExecutionResponse complete() {
            List<DynamicMessage> messages = completeMessages();
            return new GrpcExecutionResponse(
                    Status.OK.getCode().value(),
                    Status.OK.getCode().name(),
                    metadataToMap(capture.headers.get()),
                    formatResponseMessages(messages),
                    true
            );
        }

        private List<DynamicMessage> completeMessages() {
            if (!closed) {
                closed = true;
                requestObserver.onCompleted();
            }
            try {
                awaitServerStreamingResponse(latch, timeoutMillis);
                Throwable error = errorRef.get();
                if (error instanceof StatusRuntimeException statusError) {
                    throw statusError;
                }
                if (error != null) {
                    throw new IllegalArgumentException("Bidirectional streaming call failed", error);
                }
                return new ArrayList<>(responses);
            } finally {
                if (managedChannel != null) {
                    managedChannel.shutdownNow();
                }
            }
        }

        @Override
        public void cancel() {
            closed = true;
            try {
                requestObserver.onError(Status.CANCELLED.asRuntimeException());
            } catch (Exception ignored) {
            }
            if (managedChannel != null) {
                managedChannel.shutdownNow();
            }
        }
    }

    private Metadata toMetadata(List<HeaderEntryState> entries) {
        Metadata metadata = new Metadata();
        if (entries == null) {
            return metadata;
        }
        for (HeaderEntryState entry : entries) {
            if (entry == null || !entry.enabled) {
                continue;
            }
            if (entry.name == null || entry.name.isBlank()) {
                continue;
            }
            String name = entry.name.trim().toLowerCase(Locale.ROOT);
            if (name.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                Metadata.Key<byte[]> key = Metadata.Key.of(name, Metadata.BINARY_BYTE_MARSHALLER);
                metadata.put(key, toBinaryHeaderValue(entry.value));
            } else {
                Metadata.Key<String> key = Metadata.Key.of(name, Metadata.ASCII_STRING_MARSHALLER);
                metadata.put(key, entry.value == null ? "" : entry.value);
            }
        }
        return metadata;
    }

    private ClientInterceptor metadataInterceptor(Metadata headers) {
        return new ClientInterceptor() {
            @Override
            public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method,
                    CallOptions callOptions,
                    Channel next
            ) {
                ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
                return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {
                    @Override
                    public void start(Listener<RespT> responseListener, Metadata metadata) {
                        metadata.merge(headers);
                        super.start(responseListener, metadata);
                    }
                };
            }
        };
    }

    private Map<String, List<String>> metadataToMap(Metadata metadata) {
        if (metadata == null) {
            return Map.of();
        }
        Map<String, List<String>> result = new HashMap<>();
        for (String key : metadata.keys()) {
            if (key.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                continue;
            }
            Metadata.Key<String> metaKey = Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER);
            try {
                Iterable<String> values = metadata.getAll(metaKey);
                if (values == null) {
                    continue;
                }
                List<String> list = new ArrayList<>();
                values.forEach(list::add);
                result.put(key, list);
            } catch (IllegalArgumentException error) {
                result.put(key, List.of("<invalid metadata: " + error.getMessage() + ">"));
            }
        }
        return result;
    }

    private String formatResponseMessage(DynamicMessage responseMessage) {
        if (responseMessage == null) {
            return "";
        }
        try {
            return JsonFormat.printer()
                    .includingDefaultValueFields(responseMessage.getAllFields().keySet())
                    .print(responseMessage);
        } catch (Exception ignored) {
            return responseMessage.toString();
        }
    }

    private String formatResponseMessages(List<DynamicMessage> responseMessages) {
        List<Object> values = new ArrayList<>();
        if (responseMessages != null) {
            for (DynamicMessage message : responseMessages) {
                String json = formatResponseMessage(message);
                try {
                    values.add(mapper.readValue(json, Object.class));
                } catch (Exception ignored) {
                    values.add(json);
                }
            }
        }
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(values);
        } catch (Exception ignored) {
            return String.valueOf(values);
        }
    }

    private byte[] toBinaryHeaderValue(String value) {
        if (value == null || value.isBlank()) {
            return new byte[0];
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("base64:")) {
            String payload = trimmed.substring("base64:".length()).trim();
            try {
                return Base64.getDecoder().decode(payload);
            } catch (IllegalArgumentException ignored) {
                return payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return trimmed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static final class MetadataCapture {
        private final AtomicReference<Metadata> headers = new AtomicReference<>(new Metadata());

        private ClientInterceptor interceptor() {
            return new ClientInterceptor() {
                @Override
                public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                        MethodDescriptor<ReqT, RespT> method,
                        CallOptions callOptions,
                        Channel next
                ) {
                    ClientCall<ReqT, RespT> call = next.newCall(method, callOptions);
                    return new ForwardingClientCall.SimpleForwardingClientCall<>(call) {
                        @Override
                        public void start(Listener<RespT> responseListener, Metadata metadata) {
                            super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<>(responseListener) {
                                @Override
                                public void onHeaders(Metadata headers) {
                                    MetadataCapture.this.headers.set(headers);
                                    super.onHeaders(headers);
                                }
                            }, metadata);
                        }
                    };
                }
            };
        }
    }
}
