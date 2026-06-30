package com.intelli.webrunner.state;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@State(name = "IntelliWebrunnerGlobalState", storages = @Storage(StoragePathMacros.NON_ROAMABLE_FILE))
public class GlobalWebrunnerStateService implements PersistentStateComponent<WebrunnerState> {
    private final WebrunnerState state = new WebrunnerState();

    public static GlobalWebrunnerStateService getInstance() {
        return ApplicationManager.getApplication().getService(GlobalWebrunnerStateService.class);
    }

    @Override
    public @Nullable WebrunnerState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull WebrunnerState loaded) {
        state.nodes = loaded.nodes == null ? new ArrayList<>() : new ArrayList<>(loaded.nodes);
        state.requestDetails = loaded.requestDetails == null ? new ArrayList<>() : new ArrayList<>(loaded.requestDetails);
        state.requestStatuses = loaded.requestStatuses == null ? new ArrayList<>() : new ArrayList<>(loaded.requestStatuses);
        state.chainStates = loaded.chainStates == null ? new ArrayList<>() : new ArrayList<>(loaded.chainStates);
        state.headerPresets = loaded.headerPresets == null ? new ArrayList<>() : cloneHeaderPresets(loaded.headerPresets);
        state.globalContext = cloneGlobalContext(loaded.globalContext);
    }

    public List<NodeState> getNodes() {
        return state.nodes;
    }

    public WebrunnerState exportState() {
        WebrunnerState snapshot = new WebrunnerState();
        snapshot.nodes = cloneNodes(state.nodes);
        snapshot.requestDetails = cloneDetails(state.requestDetails);
        snapshot.requestStatuses = cloneStatuses(state.requestStatuses);
        snapshot.chainStates = cloneChains(state.chainStates);
        snapshot.headerPresets = cloneHeaderPresets(state.headerPresets);
        snapshot.globalContext = cloneGlobalContext(state.globalContext);
        return snapshot;
    }

    public void replaceState(WebrunnerState incoming) {
        if (incoming == null) {
            return;
        }
        state.nodes = incoming.nodes == null ? new ArrayList<>() : cloneNodes(incoming.nodes);
        state.requestDetails = incoming.requestDetails == null ? new ArrayList<>() : cloneDetails(incoming.requestDetails);
        state.requestStatuses = incoming.requestStatuses == null ? new ArrayList<>() : cloneStatuses(incoming.requestStatuses);
        state.chainStates = incoming.chainStates == null ? new ArrayList<>() : cloneChains(incoming.chainStates);
        state.headerPresets = incoming.headerPresets == null ? new ArrayList<>() : cloneHeaderPresets(incoming.headerPresets);
        state.globalContext = cloneGlobalContext(incoming.globalContext);
        normalizeOrders();
    }

    public void mergeState(WebrunnerState incoming) {
        if (incoming == null) {
            return;
        }
        List<NodeState> incomingNodes = cloneNodes(incoming.nodes);
        List<RequestDetailsState> incomingDetails = cloneDetails(incoming.requestDetails);
        List<RequestStatusState> incomingStatuses = cloneStatuses(incoming.requestStatuses);
        List<ChainState> incomingChains = cloneChains(incoming.chainStates);
        List<HeaderPresetState> incomingPresets = cloneHeaderPresets(incoming.headerPresets);
        GlobalContextState incomingGlobalContext = cloneGlobalContext(incoming.globalContext);

        Map<String, String> idMap = new HashMap<>();
        Set<String> existingIds = new HashSet<>();
        for (NodeState node : state.nodes) {
            existingIds.add(node.id);
        }

        for (NodeState node : incomingNodes) {
            if (node.id == null || existingIds.contains(node.id) || idMap.containsKey(node.id)) {
                String newId = UUID.randomUUID().toString();
                if (node.id != null) {
                    idMap.put(node.id, newId);
                }
                node.id = newId;
            }
        }

        for (NodeState node : incomingNodes) {
            if (node.parentId != null && idMap.containsKey(node.parentId)) {
                node.parentId = idMap.get(node.parentId);
            }
        }

        for (RequestDetailsState details : incomingDetails) {
            if (details.requestId != null && idMap.containsKey(details.requestId)) {
                details.requestId = idMap.get(details.requestId);
            }
        }
        for (RequestStatusState status : incomingStatuses) {
            if (status.requestId != null && idMap.containsKey(status.requestId)) {
                status.requestId = idMap.get(status.requestId);
            }
        }
        for (ChainState chain : incomingChains) {
            if (chain.requestId != null && idMap.containsKey(chain.requestId)) {
                chain.requestId = idMap.get(chain.requestId);
            }
            if (chain.requestIds != null) {
                List<String> mapped = new ArrayList<>();
                for (String id : chain.requestIds) {
                    mapped.add(idMap.getOrDefault(id, id));
                }
                chain.requestIds = mapped;
            }
        }

        // Drop orphaned nodes whose parent no longer exists (map to root).
        Set<String> allNodeIds = new HashSet<>();
        for (NodeState node : state.nodes) {
            allNodeIds.add(node.id);
        }
        for (NodeState node : incomingNodes) {
            allNodeIds.add(node.id);
        }
        for (NodeState node : incomingNodes) {
            if (node.parentId != null && !allNodeIds.contains(node.parentId)) {
                node.parentId = null;
            }
        }

        // Append incoming nodes, keeping order per parent.
        Map<String, List<NodeState>> byParent = new HashMap<>();
        for (NodeState node : incomingNodes) {
            byParent.computeIfAbsent(node.parentId, key -> new ArrayList<>()).add(node);
        }
        for (List<NodeState> nodes : byParent.values()) {
            nodes.sort(Comparator.comparingInt(a -> a.order));
            int start = nextOrder(nodes.get(0).parentId);
            for (NodeState node : nodes) {
                node.order = start++;
                state.nodes.add(node);
            }
        }

        state.requestDetails.addAll(incomingDetails);
        state.requestStatuses.addAll(incomingStatuses);
        state.chainStates.addAll(incomingChains);
        if (!incomingPresets.isEmpty()) {
            state.headerPresets = incomingPresets;
        }
        if (!incomingGlobalContext.variables.isEmpty() || !safe(incomingGlobalContext.script).isBlank()) {
            state.globalContext = incomingGlobalContext;
        }
        normalizeOrders();
    }

    public List<HeaderPresetState> getHeaderPresets() {
        return cloneHeaderPresets(state.headerPresets);
    }

    public void saveHeaderPresets(List<HeaderPresetState> presets) {
        state.headerPresets = presets == null ? new ArrayList<>() : cloneHeaderPresets(presets);
    }

    public GlobalContextState getGlobalContext() {
        return cloneGlobalContext(state.globalContext);
    }

    public void saveGlobalContext(GlobalContextState globalContext) {
        state.globalContext = cloneGlobalContext(globalContext);
    }

    public void saveGlobalContextVariables(Map<String, Object> values) {
        GlobalContextState updated = cloneGlobalContext(state.globalContext);
        Map<String, HeaderEntryState> enabledByName = new LinkedHashMap<>();
        for (HeaderEntryState variable : updated.variables) {
            if (variable == null || variable.name == null || variable.name.isBlank() || !variable.enabled) {
                continue;
            }
            enabledByName.put(variable.name, variable);
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.isBlank()) {
                continue;
            }
            HeaderEntryState variable = enabledByName.get(name);
            if (variable == null) {
                variable = new HeaderEntryState();
                variable.id = UUID.randomUUID().toString();
                variable.name = name;
                variable.enabled = true;
                updated.variables.add(variable);
                enabledByName.put(name, variable);
            }
            variable.value = stringifyGlobalContextValue(entry.getValue());
        }
        state.globalContext = updated;
    }

    public NodeState createFolder(String name, String parentId) {
        NodeState node = new NodeState();
        node.id = UUID.randomUUID().toString();
        node.name = name;
        node.type = NodeType.FOLDER;
        node.parentId = parentId;
        node.order = nextOrder(parentId);
        state.nodes.add(node);
        return node;
    }

    public NodeState createRequest(String name, RequestType type, String parentId) {
        NodeState node = new NodeState();
        node.id = UUID.randomUUID().toString();
        node.name = name;
        node.type = NodeType.REQUEST;
        node.requestType = type;
        node.parentId = parentId;
        node.order = nextOrder(parentId);
        state.nodes.add(node);

        RequestDetailsState details = new RequestDetailsState();
        details.requestId = node.id;
        details.type = type;
        if (type == RequestType.HTTP) {
            details.method = "GET";
            details.url = "";
            details.payloadType = "RAW";
        } else if (type == RequestType.GRPC) {
            details.target = "";
            details.service = "";
            details.grpcMethod = "";
        } else if (type == RequestType.KAFKA) {
            details.kafkaBootstrapServers = "";
            details.kafkaTopic = "";
            details.kafkaKey = "";
        } else if (type == RequestType.KAFKA_LISTEN) {
            details.kafkaBootstrapServers = "";
            details.kafkaTopic = "";
            details.kafkaGroupId = safe(name) + "-webrunner";
        }
        state.requestDetails.add(details);

        RequestStatusState status = new RequestStatusState();
        status.requestId = node.id;
        status.requestBody = "";
        status.responseBody = "";
        status.responseHeaders = "";
        status.logs = "";
        status.beforeScript = "";
        status.afterScript = "";
        if (type == RequestType.KAFKA) {
            status.kafkaKeyType = "String";
            status.kafkaBodyType = "JSON";
            status.kafkaPartitions = "";
        } else if (type == RequestType.KAFKA_LISTEN) {
            status.kafkaOffsetStrategy = "Latest";
        }
        state.requestStatuses.add(status);

        if (type == RequestType.CHAIN) {
            ChainState chain = new ChainState();
            chain.requestId = node.id;
            chain.logs = "";
            chain.currentState = "";
            state.chainStates.add(chain);
        }

        return node;
    }

    public void deleteNode(String nodeId) {
        state.nodes.removeIf(node -> Objects.equals(node.id, nodeId));
        state.requestDetails.removeIf(details -> Objects.equals(details.requestId, nodeId));
        state.requestStatuses.removeIf(status -> Objects.equals(status.requestId, nodeId));
        state.chainStates.removeIf(chain -> Objects.equals(chain.requestId, nodeId));
        for (NodeState node : state.nodes) {
            if (Objects.equals(node.parentId, nodeId)) {
                node.parentId = null;
            }
        }
    }

    public void updateNodeName(String nodeId, String name) {
        NodeState node = findNode(nodeId);
        if (node != null) {
            node.name = name;
        }
    }

    public void moveNode(String nodeId, String newParentId, int newIndex) {
        NodeState node = findNode(nodeId);
        if (node == null) {
            return;
        }
        String oldParentId = node.parentId;
        if (Objects.equals(oldParentId, newParentId) && node.order == newIndex) {
            return;
        }

        List<NodeState> oldSiblings = siblingsOf(oldParentId);
        oldSiblings.removeIf(entry -> Objects.equals(entry.id, nodeId));
        reindex(oldSiblings);

        node.parentId = newParentId;
        List<NodeState> newSiblings = siblingsOf(newParentId);
        newSiblings.removeIf(entry -> Objects.equals(entry.id, nodeId));
        int insertIndex = Math.max(0, Math.min(newIndex, newSiblings.size()));
        newSiblings.add(insertIndex, node);
        reindex(newSiblings);
    }

    public NodeState findNode(String nodeId) {
        for (NodeState node : state.nodes) {
            if (Objects.equals(node.id, nodeId)) {
                return node;
            }
        }
        return null;
    }

    public RequestDetailsState getRequestDetails(String requestId) {
        for (RequestDetailsState details : state.requestDetails) {
            if (Objects.equals(details.requestId, requestId)) {
                return details;
            }
        }
        return null;
    }

    public void saveRequestDetails(RequestDetailsState details) {
        RequestDetailsState existing = getRequestDetails(details.requestId);
        if (existing == null) {
            state.requestDetails.add(details);
        } else {
            existing.type = details.type;
            existing.method = details.method;
            existing.payloadType = details.payloadType;
            existing.url = details.url;
            existing.target = details.target;
            existing.service = details.service;
            existing.grpcMethod = details.grpcMethod;
            existing.kafkaBootstrapServers = details.kafkaBootstrapServers;
            existing.kafkaTopic = details.kafkaTopic;
            existing.kafkaKey = details.kafkaKey;
            existing.kafkaGroupId = details.kafkaGroupId;
        }
    }

    public RequestStatusState getRequestStatus(String requestId) {
        for (RequestStatusState status : state.requestStatuses) {
            if (Objects.equals(status.requestId, requestId)) {
                return status;
            }
        }
        return null;
    }

    public void saveRequestStatus(RequestStatusState status) {
        RequestStatusState existing = getRequestStatus(status.requestId);
        if (existing == null) {
            state.requestStatuses.add(status);
        } else {
            existing.requestBody = status.requestBody;
            existing.requestHeaders = status.requestHeaders == null ? new ArrayList<>() : new ArrayList<>(status.requestHeaders);
            existing.requestParams = status.requestParams == null ? new ArrayList<>() : new ArrayList<>(status.requestParams);
            existing.formData = status.formData == null ? new ArrayList<>() : new ArrayList<>(status.formData);
            existing.binaryFilePath = status.binaryFilePath;
            existing.responseBody = status.responseBody;
            existing.responseHeaders = status.responseHeaders;
            existing.logs = status.logs;
            existing.beforeScript = status.beforeScript;
            existing.afterScript = status.afterScript;
            existing.kafkaKeyType = status.kafkaKeyType;
            existing.kafkaBodyType = status.kafkaBodyType;
            existing.kafkaPartitions = status.kafkaPartitions;
            existing.kafkaOffsetStrategy = status.kafkaOffsetStrategy;
            existing.stressEnabled = status.stressEnabled;
            existing.stressRequestsPerSec = status.stressRequestsPerSec;
            existing.stressTotalDuration = status.stressTotalDuration;
            existing.stressTotalDurationUnit = status.stressTotalDurationUnit;
            existing.stressNumberOfRequests = status.stressNumberOfRequests;
            existing.stressParallelWorkers = status.stressParallelWorkers;
            existing.stressRampUpTime = status.stressRampUpTime;
            existing.stressRampUpTimeUnit = status.stressRampUpTimeUnit;
            existing.stressDelayBetweenRequests = status.stressDelayBetweenRequests;
            existing.stressDelayBetweenRequestsUnit = status.stressDelayBetweenRequestsUnit;
            existing.stressJitterFrom = status.stressJitterFrom;
            existing.stressJitterTo = status.stressJitterTo;
        }
    }

    public ChainState getChainState(String requestId) {
        for (ChainState chain : state.chainStates) {
            if (Objects.equals(chain.requestId, requestId)) {
                return chain;
            }
        }
        return null;
    }

    public void saveChainState(ChainState chain) {
        ChainState existing = getChainState(chain.requestId);
        if (existing == null) {
            state.chainStates.add(chain);
        } else {
            existing.requestIds = chain.requestIds == null ? new ArrayList<>() : new ArrayList<>(chain.requestIds);
            existing.logs = chain.logs;
            existing.currentState = chain.currentState;
        }
    }

    private int nextOrder(String parentId) {
        int max = -1;
        for (NodeState node : state.nodes) {
            if (Objects.equals(node.parentId, parentId)) {
                max = Math.max(max, node.order);
            }
        }
        return max + 1;
    }

    private List<NodeState> siblingsOf(String parentId) {
        List<NodeState> siblings = new ArrayList<>();
        for (NodeState node : state.nodes) {
            if (Objects.equals(node.parentId, parentId)) {
                siblings.add(node);
            }
        }
        siblings.sort(Comparator.comparingInt(a -> a.order));
        return siblings;
    }

    private void reindex(List<NodeState> nodes) {
        for (int i = 0; i < nodes.size(); i++) {
            nodes.get(i).order = i;
        }
    }

    private void normalizeOrders() {
        Map<String, List<NodeState>> byParent = new HashMap<>();
        for (NodeState node : state.nodes) {
            byParent.computeIfAbsent(node.parentId, key -> new ArrayList<>()).add(node);
        }
        for (List<NodeState> nodes : byParent.values()) {
            nodes.sort(Comparator.comparingInt(a -> a.order));
            reindex(nodes);
        }
    }

    private List<NodeState> cloneNodes(List<NodeState> nodes) {
        List<NodeState> copy = new ArrayList<>();
        if (nodes == null) {
            return copy;
        }
        for (NodeState node : nodes) {
            NodeState clone = new NodeState();
            clone.id = node.id;
            clone.name = node.name;
            clone.type = node.type;
            clone.requestType = node.requestType;
            clone.parentId = node.parentId;
            clone.order = node.order;
            copy.add(clone);
        }
        return copy;
    }

    private List<HeaderPresetState> cloneHeaderPresets(List<HeaderPresetState> presets) {
        List<HeaderPresetState> copy = new ArrayList<>();
        if (presets == null) {
            return copy;
        }
        for (HeaderPresetState preset : presets) {
            HeaderPresetState clone = new HeaderPresetState();
            clone.name = preset.name;
            clone.values = preset.values == null ? new ArrayList<>() : new ArrayList<>(preset.values);
            copy.add(clone);
        }
        return copy;
    }

    private GlobalContextState cloneGlobalContext(GlobalContextState globalContext) {
        GlobalContextState clone = new GlobalContextState();
        if (globalContext == null) {
            return clone;
        }
        clone.variables = cloneHeaders(globalContext.variables == null ? List.of() : globalContext.variables);
        clone.script = safe(globalContext.script);
        return clone;
    }

    private String stringifyGlobalContextValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof CharSequence) {
            return String.valueOf(value);
        }
        return String.valueOf(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private List<RequestDetailsState> cloneDetails(List<RequestDetailsState> detailsList) {
        List<RequestDetailsState> copy = new ArrayList<>();
        if (detailsList == null) {
            return copy;
        }
        for (RequestDetailsState details : detailsList) {
            RequestDetailsState clone = new RequestDetailsState();
            clone.requestId = details.requestId;
            clone.type = details.type;
            clone.method = details.method;
            clone.payloadType = details.payloadType;
            clone.url = details.url;
            clone.target = details.target;
            clone.service = details.service;
            clone.grpcMethod = details.grpcMethod;
            clone.kafkaBootstrapServers = details.kafkaBootstrapServers;
            clone.kafkaTopic = details.kafkaTopic;
            clone.kafkaKey = details.kafkaKey;
            clone.kafkaGroupId = details.kafkaGroupId;
            copy.add(clone);
        }
        return copy;
    }

    private List<RequestStatusState> cloneStatuses(List<RequestStatusState> statuses) {
        List<RequestStatusState> copy = new ArrayList<>();
        if (statuses == null) {
            return copy;
        }
        for (RequestStatusState status : statuses) {
            RequestStatusState clone = new RequestStatusState();
            clone.requestId = status.requestId;
            clone.requestBody = status.requestBody;
            clone.requestHeaders = status.requestHeaders == null ? new ArrayList<>() : cloneHeaders(status.requestHeaders);
            clone.requestParams = status.requestParams == null ? new ArrayList<>() : cloneHeaders(status.requestParams);
            clone.formData = status.formData == null ? new ArrayList<>() : cloneFormData(status.formData);
            clone.binaryFilePath = status.binaryFilePath;
            clone.responseBody = status.responseBody;
            clone.responseHeaders = status.responseHeaders;
            clone.logs = status.logs;
            clone.beforeScript = status.beforeScript;
            clone.afterScript = status.afterScript;
            clone.kafkaKeyType = status.kafkaKeyType;
            clone.kafkaBodyType = status.kafkaBodyType;
            clone.kafkaPartitions = status.kafkaPartitions;
            clone.kafkaOffsetStrategy = status.kafkaOffsetStrategy;
            clone.stressEnabled = status.stressEnabled;
            clone.stressRequestsPerSec = status.stressRequestsPerSec;
            clone.stressTotalDuration = status.stressTotalDuration;
            clone.stressTotalDurationUnit = status.stressTotalDurationUnit;
            clone.stressNumberOfRequests = status.stressNumberOfRequests;
            clone.stressParallelWorkers = status.stressParallelWorkers;
            clone.stressRampUpTime = status.stressRampUpTime;
            clone.stressRampUpTimeUnit = status.stressRampUpTimeUnit;
            clone.stressDelayBetweenRequests = status.stressDelayBetweenRequests;
            clone.stressDelayBetweenRequestsUnit = status.stressDelayBetweenRequestsUnit;
            clone.stressJitterFrom = status.stressJitterFrom;
            clone.stressJitterTo = status.stressJitterTo;
            copy.add(clone);
        }
        return copy;
    }

    private List<FormEntryState> cloneFormData(List<FormEntryState> entries) {
        List<FormEntryState> copy = new ArrayList<>();
        for (FormEntryState entry : entries) {
            FormEntryState clone = new FormEntryState();
            clone.id = entry.id;
            clone.name = entry.name;
            clone.value = entry.value;
            clone.enabled = entry.enabled;
            clone.file = entry.file;
            copy.add(clone);
        }
        return copy;
    }

    private List<HeaderEntryState> cloneHeaders(List<HeaderEntryState> headers) {
        List<HeaderEntryState> copy = new ArrayList<>();
        for (HeaderEntryState header : headers) {
            HeaderEntryState clone = new HeaderEntryState();
            clone.id = header.id;
            clone.name = header.name;
            clone.value = header.value;
            clone.enabled = header.enabled;
            copy.add(clone);
        }
        return copy;
    }

    private List<ChainState> cloneChains(List<ChainState> chains) {
        List<ChainState> copy = new ArrayList<>();
        if (chains == null) {
            return copy;
        }
        for (ChainState chain : chains) {
            ChainState clone = new ChainState();
            clone.requestId = chain.requestId;
            clone.requestIds = chain.requestIds == null ? new ArrayList<>() : new ArrayList<>(chain.requestIds);
            clone.logs = chain.logs;
            clone.currentState = chain.currentState;
            copy.add(clone);
        }
        return copy;
    }
}
