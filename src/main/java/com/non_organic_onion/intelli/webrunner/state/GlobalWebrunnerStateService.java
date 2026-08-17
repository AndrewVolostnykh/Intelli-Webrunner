package com.non_organic_onion.intelli.webrunner.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.non_organic_onion.webrunner.core.state.ChainState;
import com.non_organic_onion.webrunner.core.state.GlobalContextState;
import com.non_organic_onion.webrunner.core.state.HeaderPresetState;
import com.non_organic_onion.webrunner.core.state.NodeState;
import com.non_organic_onion.webrunner.core.state.RequestDetailsState;
import com.non_organic_onion.webrunner.core.state.RequestStatusState;
import com.non_organic_onion.webrunner.core.state.RequestType;
import com.non_organic_onion.webrunner.core.state.WebrunnerState;
import com.non_organic_onion.webrunner.core.state.WebrunnerStateStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@State(name = "IntelliWebrunnerGlobalState", storages = @Storage(StoragePathMacros.NON_ROAMABLE_FILE))
public class GlobalWebrunnerStateService implements PersistentStateComponent<WebrunnerState> {
	private static final String SETTINGS_FILE_NAME = "IntelliWebrunnerGlobalState";
	private static final String DEFAULT_COLLECTIONS_FILE_NAME = "collections.json";

	private final ObjectMapper mapper = new ObjectMapper();
	private final WebrunnerStateStore store = new WebrunnerStateStore() {
		@Override
		protected void persistCollectionsState() {
			GlobalWebrunnerStateService.this.persistCollectionsState();
		}
	};
	private boolean isLoading = false;

	public static GlobalWebrunnerStateService getInstance() {
		return ApplicationManager.getApplication().getService(GlobalWebrunnerStateService.class);
	}

	@Override
	public @Nullable WebrunnerState getState() {
		WebrunnerState settings = store.getState();
		settings.collectionsFilePath = getCollectionsFilePath();
		return settings;
	}

	@Override
	public void loadState(@NotNull WebrunnerState loaded) {
		isLoading = true;
		loaded.collectionsFilePath = blankToDefault(loaded.collectionsFilePath);
		store.loadState(loaded);
		isLoading = false;
		loadCollectionsFromFileOrMigrate();
	}

	public String getCollectionsFilePath() {
		return blankToDefault(store.getCollectionsFilePath());
	}

	public String getSettingsFilePath() {
		return PathManager.getOptionsFile(SETTINGS_FILE_NAME).getAbsolutePath();
	}

	public void changeCollectionsFilePath(String path) {
		String normalized = blankToDefault(path);
		store.changeCollectionsFilePath(normalized);
		File file = new File(normalized);
		if (file.isFile() && file.length() > 0) {
			loadCollectionsFromFile();
			return;
		}
		persistCollectionsState();
	}

	public List<NodeState> getNodes() {
		return store.getNodes();
	}

	public WebrunnerState exportState() {
		return store.exportState();
	}

	public void replaceState(WebrunnerState incoming) {
		store.replaceState(incoming);
	}

	public void mergeState(WebrunnerState incoming) {
		store.mergeState(incoming);
	}

	public List<HeaderPresetState> getHeaderPresets() {
		return store.getHeaderPresets();
	}

	public void saveHeaderPresets(List<HeaderPresetState> presets) {
		store.saveHeaderPresets(presets);
	}

	public boolean isStressTestsEnabled() {
		return store.isStressTestsEnabled();
	}

	public void saveStressTestsEnabled(boolean enabled) {
		store.saveStressTestsEnabled(enabled);
	}

	public int getDefaultTimeoutMillis() {
		return store.getDefaultTimeoutMillis();
	}

	public void saveDefaultTimeoutMillis(int timeoutMillis) {
		store.saveDefaultTimeoutMillis(timeoutMillis);
	}

	public GlobalContextState getGlobalContext() {
		return store.getGlobalContext();
	}

	public void saveGlobalContext(GlobalContextState globalContext) {
		store.saveGlobalContext(globalContext);
	}

	public void saveGlobalContextVariables(Map<String, Object> values) {
		store.saveGlobalContextVariables(values);
	}

	public NodeState createFolder(
		String name,
		String parentId
	) {
		return store.createFolder(name, parentId);
	}

	public NodeState createRequest(
		String name,
		RequestType type,
		String parentId
	) {
		return store.createRequest(name, type, parentId);
	}

	public void deleteNode(String nodeId) {
		store.deleteNode(nodeId);
	}

	public void updateNodeName(
		String nodeId,
		String name
	) {
		store.updateNodeName(nodeId, name);
	}

	public NodeState cloneRequest(
		String sourceRequestId,
		String name
	) {
		return store.cloneRequest(sourceRequestId, name);
	}

	public void moveNode(
		String nodeId,
		String newParentId,
		int newIndex
	) {
		store.moveNode(nodeId, newParentId, newIndex);
	}

	public NodeState findNode(String nodeId) {
		return store.findNode(nodeId);
	}

	public RequestDetailsState getRequestDetails(String requestId) {
		return store.getRequestDetails(requestId);
	}

	public void saveRequestDetails(RequestDetailsState details) {
		store.saveRequestDetails(details);
	}

	public RequestStatusState getRequestStatus(String requestId) {
		return store.getRequestStatus(requestId);
	}

	public void saveRequestStatus(RequestStatusState status) {
		store.saveRequestStatus(status);
	}

	public ChainState getChainState(String requestId) {
		return store.getChainState(requestId);
	}

	public void saveChainState(ChainState chain) {
		store.saveChainState(chain);
	}

	private void loadCollectionsFromFileOrMigrate() {
		File file = new File(getCollectionsFilePath());
		if (file.isFile() && file.length() > 0) {
			loadCollectionsFromFile();
			return;
		}
		persistCollectionsState();
	}

	private void loadCollectionsFromFile() {
		try {
			WebrunnerState collections = mapper.readValue(new File(getCollectionsFilePath()), WebrunnerState.class);
			store.loadCollectionsState(collections);
		} catch (Exception ignored) {
		}
	}

	private void persistCollectionsState() {
		if (isLoading) {
			return;
		}
		try {
			File file = new File(getCollectionsFilePath());
			File parent = file.getParentFile();
			if (parent != null) {
				Files.createDirectories(parent.toPath());
			}
			mapper.writerWithDefaultPrettyPrinter().writeValue(file, store.exportCollectionsState());
		} catch (Exception ignored) {
		}
	}

	private String blankToDefault(String path) {
		if (path != null && !path.isBlank()) {
			return path;
		}
		Path configDir = Path.of(PathManager.getConfigPath(), "intelli-webrunner");
		return configDir.resolve(DEFAULT_COLLECTIONS_FILE_NAME).toString();
	}
}
