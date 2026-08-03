package com.non_organic_onion.intelli.webrunner.execution;

import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutionResponse;
import com.non_organic_onion.intelli.webrunner.grpc.GrpcExecutor;
import com.non_organic_onion.intelli.webrunner.kafka.KafkaMessageProducer;
import com.non_organic_onion.intelli.webrunner.script.ScriptRuntime;
import com.non_organic_onion.intelli.webrunner.state.GlobalContextState;
import com.non_organic_onion.intelli.webrunner.state.GlobalWebrunnerStateService;
import com.non_organic_onion.intelli.webrunner.state.HeaderEntryState;
import com.non_organic_onion.intelli.webrunner.state.RequestDetailsState;
import com.non_organic_onion.intelli.webrunner.state.RequestType;
import com.non_organic_onion.intelli.webrunner.util.TemplateEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestExecutionServiceTest {

	@Test
	void executeGrpcWithScriptsResolvesGlobalContextInEndpointFields() {
		GlobalWebrunnerStateService stateService = new GlobalWebrunnerStateService();
		GlobalContextState globalContext = new GlobalContextState();
		globalContext.variables = new ArrayList<>(List.of(
			variable("host", "localhost", true),
			variable("port", "9090", true),
			variable("service", "demo.EchoService", true),
			variable("method", "Echo", true)
		));
		stateService.saveGlobalContext(globalContext);
		CapturingGrpcExecutor grpcExecutor = new CapturingGrpcExecutor();
		RequestExecutionService service = new RequestExecutionService(
			new TemplateEngine(),
			new ScriptRuntime(),
			new HttpExecutor(),
			grpcExecutor,
			new KafkaMessageProducer(),
			stateService
		);
		RequestDetailsState details = new RequestDetailsState();
		details.type = RequestType.GRPC;
		details.target = "{{host}}:{{port}}";
		details.service = "{{service}}";
		details.grpcMethod = "{{method}}";

		service.executeGrpcWithScripts(details, List.of(), List.of(), "{}", "", "", null);

		assertEquals("localhost:9090", grpcExecutor.target);
		assertEquals("demo.EchoService", grpcExecutor.service);
		assertEquals("Echo", grpcExecutor.method);
	}

	private HeaderEntryState variable(String name, String value, boolean enabled) {
		HeaderEntryState entry = new HeaderEntryState();
		entry.name = name;
		entry.value = value;
		entry.enabled = enabled;
		return entry;
	}

	private static final class CapturingGrpcExecutor extends GrpcExecutor {
		private String target;
		private String service;
		private String method;

		@Override
		public GrpcExecutionResponse execute(
			String target,
			String service,
			String method,
			String payload,
			List<HeaderEntryState> metadata
		) {
			this.target = target;
			this.service = service;
			this.method = method;
			return new GrpcExecutionResponse(0, "OK", Map.of(), "{}");
		}
	}
}
