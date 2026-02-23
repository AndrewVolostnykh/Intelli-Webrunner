package com.intelli.webrunner.grpc;

import com.google.protobuf.ByteString;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.intelli.webrunner.state.HeaderEntryState;
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

public class GrpcExecutor {
    private static final String REFLECTION_SERVICE = "grpc.reflection.v1alpha.ServerReflection";

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
                services.add(new GrpcServiceInfo(serviceName, methods));
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

            DynamicMessage requestMessage = buildRequestMessage(methodDescriptor, payload);
            Metadata requestHeaders = toMetadata(metadata);
            MetadataCapture capture = new MetadataCapture();
            Channel intercepted = ClientInterceptors.intercept(channel, capture.interceptor(), metadataInterceptor(requestHeaders));
            MethodDescriptor<DynamicMessage, DynamicMessage> grpcMethod =
                    MethodDescriptor.<DynamicMessage, DynamicMessage>newBuilder()
                            .setType(MethodDescriptor.MethodType.UNARY)
                            .setFullMethodName(MethodDescriptor.generateFullMethodName(
                                    serviceDescriptor.getFullName(),
                                    methodDescriptor.getName()
                            ))
                            .setRequestMarshaller(ProtoUtils.marshaller(requestMessage.getDefaultInstanceForType()))
                            .setResponseMarshaller(ProtoUtils.marshaller(
                                    DynamicMessage.getDefaultInstance(methodDescriptor.getOutputType())
                            ))
                            .build();

            DynamicMessage responseMessage = ClientCalls.blockingUnaryCall(
                    intercepted,
                    grpcMethod,
                    CallOptions.DEFAULT,
                    requestMessage
            );

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
