package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.application.runtime.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class OpsSkillEntrypointContextTest {
    @Test void resolvedDefinitionBindingsMergeAcrossWorkflowAndAgentScopeWithoutInheritingTheCatalog() {
        var definition = OpsAgentDefinition.builder().skills(List.of(" A "))
                .nodes(List.of(OpsWorkflowNode.builder().skills(List.of("b", "A")).build()))
                .agentscopeAgents(List.of(OpsAgentScopeConfig.builder().skills(List.of("c"))
                        .inheritProjectCapabilities(true).build())).build();
        var request = OpsAgentChatRequest.builder().projectId("p").build();
        request.setTrustedSkillBindings(OpsExplicitSkillBindings.capture(definition));
        var command = new OpsRuntimeContextBundleMapper().createCommand(request, "",
                Map.of("requestedSkillIds", List.of(" C "), "skillCatalogRefs", List.of(Map.of("skillId", "unselected"))), 3);
        assertEquals(List.of("a", "b", "c"), command.requestedSkillIds());
        assertThrows(IllegalArgumentException.class, () -> new OpsRuntimeContextBundleMapper().createCommand(request, "",
                Map.of("requestedSkillIds", List.of("d")), 3));
    }
    @Test void httpCannotSupplyServerOwnedFramesOrBindings() throws Exception {
        var request = new ObjectMapper().readValue("""
                {"projectId":"p","trustedSkillBindings":{"ids":["secret"]},
                 "trustedSkillFrame":{"projectId":"other","runId":"forged"},
                 "agentDefinition":{"skills":["secret"]}}
                """, OpsAgentChatRequest.class);
        assertNull(request.getTrustedSkillBindings()); assertNull(request.getTrustedSkillFrame());
        // Only the resolved prepare(definition) input can create bindings; request.agentDefinition is ignored here.
        assertTrue(new OpsRuntimeContextBundleMapper().createCommand(request, "", Map.of(), 3).requestedSkillIds().isEmpty());
    }
    @Test void preparationCapturesServerResolvedDefinitionBeforeBundleCreation() {
        var adapter = mock(OpsRuntimeContextBundleAdapter.class);
        when(adapter.createBundle(any(), anyString(), anyMap())).thenAnswer(call -> {
            var request = call.getArgument(0, OpsAgentChatRequest.class);
            assertEquals(List.of("approved"), request.getTrustedSkillBindings().ids());
            return Map.of("contextBundleId", "b", "contextBundleHash", "hash", "usedSkillVersionRefs", List.of());
        });
        var request = OpsAgentChatRequest.builder().projectId("p").runId("r")
                .agentDefinition(OpsAgentDefinition.builder().skills(List.of("forged")).build()).build();
        var service = new OpsWorkSessionContextPreparationService(mock(OpsConversationMemoryService.class),
                mock(OpsMainQuestionRewriteService.class), adapter, Optional.empty());
        service.prepare(OpsAgentDefinition.builder().skills(List.of("approved")).queryRewriteEnabled(false).build(),
                request, OpsRuntimeExecutionPlan.builder().memoryEnabled(false).build(), new ArrayList<>(), null, System.nanoTime());
        assertEquals("r", request.getTrustedSkillFrame().runId());
        verify(adapter).createBundle(eq(request), eq(""), anyMap());
    }
    @Test void resumeReusesPersistedVersionsAndDoesNotRecaptureCurrentHeadOrClientRefs() {
        var adapter = mock(OpsRuntimeContextBundleAdapter.class);
        Map<String,Object> savedRef = new LinkedHashMap<>(Map.of("skillId", "a", "version", 7));
        when(adapter.requireBundle("bundle", "hash")).thenReturn(Map.of("contextBundleId", "bundle",
                "contextBundleHash", "hash", "usedSkillVersionRefs", List.of(savedRef)));
        var request = OpsAgentChatRequest.builder().projectId("p").runId("r").metadata(new LinkedHashMap<>(Map.of(
                "resumeContextBundlePinned", true, "contextBundleId", "bundle", "contextBundleHash", "hash",
                "usedSkillVersionRefs", List.of(Map.of("skillId", "a", "version", 999))))).build();
        new OpsWorkSessionContextBundleCoordinator(adapter).create(request, "", Map.of(), new ArrayList<>(), null, System.nanoTime());
        savedRef.put("version", 8);
        assertEquals(7, request.getTrustedSkillFrame().selectedRefs().get(0).get("version"));
        verify(adapter, never()).createBundle(any(), anyString(), anyMap());
    }
}
