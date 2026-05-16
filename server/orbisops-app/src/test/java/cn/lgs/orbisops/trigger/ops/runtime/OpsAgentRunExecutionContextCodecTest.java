package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStage;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentExecutionStyle;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentRunExecutionContext;
import cn.lgs.orbisops.domain.worksession.runtime.model.AgentSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.ApprovedPackageSnapshot;
import cn.lgs.orbisops.domain.worksession.runtime.model.CapabilityProfile;
import cn.lgs.orbisops.domain.worksession.runtime.model.TriggerSource;
import com.alibaba.fastjson.JSON;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class OpsAgentRunExecutionContextCodecTest {

    private final OpsAgentRunExecutionContextCodec codec = new OpsAgentRunExecutionContextCodec();

    @Test
    void roundTripsV5LandingAuthorityWithoutPerToolApprovalAcl() {
        Instant expiry = Instant.now().plusSeconds(120);
        AgentSnapshot agent = agent();
        ApprovedPackageSnapshot approved = new ApprovedPackageSnapshot(
                "cp-1", 2, "hash-2", "project-1", "prod", "sha256:a", expiry);
        AgentRunExecutionContext context = new AgentRunExecutionContext(
                "run-1", "session-1", "project-1", TriggerSource.LANDING,
                AgentExecutionStyle.REACT, AgentExecutionStage.LANDING, agent,
                Optional.of(approved), CapabilityProfile.PROD_FULL,
                Set.of("prod-mcp"), Set.of(), expiry);

        AgentRunExecutionContext decoded = codec.decode(codec.encode(context));

        assertEquals(context, decoded);
        assertEquals(OpsAgentRunExecutionContextCodec.CURRENT_SCHEMA_VERSION,
                codec.encode(context).get("schemaVersion"));
        assertEquals("REACT", codec.encode(context).get("executionStyle"));
    }

    @Test
    void persistedAuthorityMustRemainPureJsonValueTreeWithoutFastjsonRefs() {
        Instant deadline = Instant.now().plusSeconds(120);
        Set<String> sharedTools = Set.of("tool-a");
        AgentSnapshot agent = new AgentSnapshot(
                "agent-1", 3, "definition-hash", "prompt-hash", "model-1",
                sharedTools, Set.of(), Set.of(), Set.of());
        AgentRunExecutionContext context = new AgentRunExecutionContext(
                "run-json", "session-json", "project-1", TriggerSource.WORKFLOW,
                AgentExecutionStyle.WORKFLOW, AgentExecutionStage.PREPARE, agent,
                Optional.empty(), CapabilityProfile.TEST_FULL,
                Set.of(), sharedTools, deadline);

        String json = JSON.toJSONString(codec.encode(context));
        AgentRunExecutionContext decoded = codec.decode(JSON.parseObject(json, LinkedHashMap.class));

        assertFalse(json.contains("\"$ref\""));
        assertEquals(context, decoded);
    }

    @Test
    void decodesLegacyFastjsonBindingRefsOnlyInsideTrustedSnapshot() {
        Instant deadline = Instant.now().plusSeconds(120);
        AgentSnapshot expectedAgent = new AgentSnapshot(
                "agent-ref", 1, "definition-ref", "prompt-ref", "model-ref",
                Set.of(), Set.of(), Set.of(), Set.of());
        Map<String, Object> agent = new LinkedHashMap<>(codec.encodeAgent(expectedAgent));
        String emptyRef = "$.metadata._agentRunExecutionContext.agentSnapshot.knowledgeBindingSnapshot";
        agent.put("toolBindingSnapshot", Map.of("$ref", emptyRef));
        agent.put("mcpBindingSnapshot", Map.of("$ref", emptyRef));
        agent.put("skillBindingSnapshot", Map.of("$ref", emptyRef));

        Map<String, Object> encoded = new LinkedHashMap<>();
        encoded.put("schemaVersion", OpsAgentRunExecutionContextCodec.CURRENT_SCHEMA_VERSION);
        encoded.put("runId", "run-ref");
        encoded.put("workSessionId", "session-ref");
        encoded.put("projectId", "project-1");
        encoded.put("triggerSource", "WORKFLOW");
        encoded.put("executionStyle", "WORKFLOW");
        encoded.put("stage", "PREPARE");
        encoded.put("agentSnapshot", agent);
        encoded.put("approvedPackage", Map.of());
        encoded.put("capabilityProfile", "TEST_FULL");
        encoded.put("allowedResourceIds", Map.of("$ref", emptyRef));
        encoded.put("allowedToolIds", Map.of("$ref", emptyRef));
        encoded.put("deadline", deadline.toString());

        AgentRunExecutionContext decoded = codec.decode(encoded);

        assertEquals(expectedAgent, decoded.agentSnapshot());
        assertEquals(Set.of(), decoded.allowedResourceIds());
        assertEquals(Set.of(), decoded.allowedToolIds());
    }

    @Test
    void safelyMigratesLegacyLandingSnapshotAndIgnoresOldPerCallAclFields() {
        Instant deadline = Instant.now().plusSeconds(60);
        Map<String, Object> legacy = Map.ofEntries(
                Map.entry("schemaVersion", 4),
                Map.entry("runId", "run-old"),
                Map.entry("workSessionId", "session-old"),
                Map.entry("projectId", "project-1"),
                Map.entry("triggerSource", "LANDING"),
                Map.entry("disposition", "EXPLICIT_AGENT"),
                Map.entry("stage", "LANDING"),
                Map.entry("agentSnapshot", codec.encodeAgent(agent())),
                Map.entry("capabilityProfile", "PROD_LANDING"),
                Map.entry("allowedResourceIds", Set.of()),
                Map.entry("allowedToolIds", Set.of()),
                Map.entry("deadline", deadline.toString()),
                Map.entry("approvedPackage", Map.ofEntries(
                        Map.entry("packageId", "cp-old"),
                        Map.entry("packageVersion", 1),
                        Map.entry("packageHash", "hash-old"),
                        Map.entry("projectId", "project-1"),
                        Map.entry("targetEnvironment", "prod"),
                        Map.entry("artifactDigest", ""),
                        Map.entry("allowedToolIds", Set.of("old-tool")),
                        Map.entry("allowedResourceScopes", Set.of()))));

        AgentRunExecutionContext decoded = codec.decode(legacy);

        assertEquals(AgentExecutionStyle.REACT, decoded.executionStyle());
        assertEquals(CapabilityProfile.PROD_FULL, decoded.capabilityProfile());
        assertEquals("cp-old", decoded.approvedPackage().orElseThrow().packageId());
        assertEquals(deadline, decoded.approvedPackage().orElseThrow().approvalExpiresAt());
    }

    private AgentSnapshot agent() {
        return new AgentSnapshot(
                "platform-landing-react", 2, "definition-hash", "prompt-hash", "model",
                Set.of("tool-a"), Set.of("mcp-a"), Set.of(), Set.of());
    }
}
