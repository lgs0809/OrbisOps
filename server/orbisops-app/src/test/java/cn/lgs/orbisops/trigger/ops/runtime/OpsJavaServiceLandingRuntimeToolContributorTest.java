package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStyle;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.CapabilityProfile;
import cn.lgs.orbisops.domain.worksession.runtime.model.TriggerSource;
import cn.lgs.orbisops.trigger.ops.toolset.OpsLocalJavaServiceToolExecutionHandler;
import cn.lgs.orbisops.trigger.ops.toolset.OpsToolExecutionService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.tool.ToolCallback;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsJavaServiceLandingRuntimeToolContributorTest {

    private static final String SHA = "a".repeat(64);

    @Test
    void landingDeployInjectsApprovedDigestAndPlatformIdempotency() {
        OpsToolExecutionService service = mock(OpsToolExecutionService.class);
        when(service.executeLanding(anyMap(), eq("alice"))).thenReturn(Map.of("status", "SUCCEEDED"));
        OpsJavaServiceLandingRuntimeToolContributor contributor =
                new OpsJavaServiceLandingRuntimeToolContributor((Supplier<OpsToolExecutionService>) () -> service);
        OpsRuntimeResourceContext context = landingContext("sha256:" + SHA);

        contributor.contribute(context);

        assertEquals(2, context.getTools().size());
        ToolCallback deploy = context.getTools().stream()
                .filter(tool -> OpsJavaServiceLandingRuntimeToolContributor.DEPLOY_TOOL.equals(tool.getToolDefinition().name()))
                .findFirst().orElseThrow();
        deploy.call("{\"executionResourceId\":\"prod-like-8092\",\"serviceId\":\"order-service\",\"artifactPath\":\"/tmp/approved.jar\"}");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(service).executeLanding(captor.capture(), eq("alice"));
        Map<String, Object> request = captor.getValue();
        assertEquals("deployment.local-java", request.get("toolsetId"));
        assertEquals(OpsLocalJavaServiceToolExecutionHandler.DEPLOY, request.get("toolName"));
        assertEquals("pkg-1", request.get("packageId"));
        assertEquals("package-hash", request.get("packageHash"));
        assertTrue(String.valueOf(request.get("idempotencyKey")).startsWith("java-landing:"));
        Map<?, ?> arguments = (Map<?, ?>) request.get("arguments");
        assertEquals(SHA, arguments.get("artifactSha256"));
        assertEquals(request.get("idempotencyKey"), arguments.get("executionKey"));
        assertFalse(arguments.containsKey("approvedArtifactDigest"));
    }

    @Test
    void nonLandingAndInvalidDigestDoNotExposeDeploymentTools() {
        OpsToolExecutionService service = mock(OpsToolExecutionService.class);
        OpsJavaServiceLandingRuntimeToolContributor contributor =
                new OpsJavaServiceLandingRuntimeToolContributor((Supplier<OpsToolExecutionService>) () -> service);

        OpsRuntimeResourceContext invalidDigest = landingContext("not-a-sha");
        contributor.contribute(invalidDigest);
        assertTrue(invalidDigest.getTools().isEmpty());

        OpsRuntimeResourceContext investigate = OpsRuntimeResourceContext.builder()
                .projectId("project-1")
                .request(request())
                .executionContext(authority(AgentExecutionStage.INVESTIGATE, Optional.empty()))
                .tools(new ArrayList<>())
                .events(new ArrayList<>())
                .metadata(new LinkedHashMap<>())
                .build();
        contributor.contribute(investigate);
        assertTrue(investigate.getTools().isEmpty());
    }

    private OpsRuntimeResourceContext landingContext(String digest) {
        ApprovedPackageSnapshot approved = new ApprovedPackageSnapshot(
                "pkg-1", 3, "package-hash", "project-1", "prod", digest,
                Instant.now().plusSeconds(600));
        return OpsRuntimeResourceContext.builder()
                .projectId("project-1")
                .request(request())
                .executionContext(authority(AgentExecutionStage.LANDING, Optional.of(approved)))
                .tools(new ArrayList<>())
                .events(new ArrayList<>())
                .metadata(new LinkedHashMap<>())
                .build();
    }

    private OpsAgentChatRequest request() {
        return OpsAgentChatRequest.builder()
                .projectId("project-1")
                .userId("alice")
                .runId("run-1")
                .sessionId("session-1")
                .metadata(new LinkedHashMap<>())
                .build();
    }

    private AgentRunExecutionContext authority(
            AgentExecutionStage stage,
            Optional<ApprovedPackageSnapshot> approved) {
        AgentSnapshot agent = new AgentSnapshot(
                "platform-landing-react", 1, "definition", "prompt", "model",
                Set.of(), Set.of(), Set.of(), Set.of());
        return new AgentRunExecutionContext(
                "run-1", "session-1", "project-1",
                stage == AgentExecutionStage.LANDING ? TriggerSource.LANDING : TriggerSource.CHAT,
                AgentExecutionStyle.REACT,
                stage,
                agent,
                approved,
                stage == AgentExecutionStage.LANDING ? CapabilityProfile.PROD_FULL : CapabilityProfile.PROD_DIAGNOSTIC,
                Set.of(), Set.of(), Instant.now().plusSeconds(600));
    }
}
