package cn.lgs.orbisops.application.skill;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillEvolutionMultiCandidateAuthoringServiceTest {

    @Test
    void legacyAuthoringPortMustProduceThreeDirectedAuditedCandidates() {
        List<Map<String, Object>> requests = new ArrayList<>();
        SkillEvolutionAuthoringPort legacy = input -> {
            requests.add(new LinkedHashMap<>(input));
            return SkillEvolutionAuthoredCandidate.from(Map.of(
                    "patchType", "PATCH",
                    "changes", List.of(Map.of(
                            "operation", "REPLACE",
                            "path", "/procedure",
                            "value", input.get("candidateDirection"))),
                    "authoringModel", "model-v2",
                    "authoringPromptVersion", "skill-author-v4",
                    "authoringSeed", 42L,
                    "authoringSource", "TEST"));
        };
        SkillEvolutionMultiCandidateAuthoringService service =
                new SkillEvolutionMultiCandidateAuthoringService(legacy);

        List<SkillEvolutionAuthoredCandidateOption> options = service.author(Map.of(
                "projectId", "project-1",
                "defectDiagnoses", List.of(Map.of("layer", "ROUTING"))), 3);

        assertEquals(3, options.size());
        assertEquals(3, requests.size());
        assertEquals(List.of("ROUTING_PATCH", "MINIMAL_PATCH", "PROCEDURE_PATCH"),
                requests.stream().map(item -> String.valueOf(item.get("candidateDirection"))).toList());
        assertEquals(List.of(
                        SkillAuthoringDirection.ROUTING_PATCH,
                        SkillAuthoringDirection.MINIMAL_PATCH,
                        SkillAuthoringDirection.PROCEDURE_PATCH),
                options.stream().map(option -> option.audit().direction()).toList());
        assertEquals(1, options.stream().map(option -> option.audit().inputHash()).distinct().count());
        options.forEach(option -> {
            assertEquals("model-v2", option.audit().modelId());
            assertEquals("skill-author-v4", option.audit().promptVersion());
            assertEquals(42L, option.audit().seed());
            assertEquals(3, option.audit().candidateBudget());
            assertTrue(option.candidate().payload().containsKey("authoringAudit"));
            assertTrue(option.candidate().reusableChange());
        });
        assertNotEquals(
                options.get(0).candidate().payload().get("candidateDirection"),
                options.get(1).candidate().payload().get("candidateDirection"));
    }

    @Test
    void defaultAndExplicitOneMakeOnlyOneAuthoringRequest() {
        List<Map<String, Object>> requests = new ArrayList<>();
        SkillEvolutionAuthoringPort legacy = input -> {
            requests.add(new LinkedHashMap<>(input));
            return SkillEvolutionAuthoredCandidate.from(Map.of(
                    "patchType", "NO_CHANGE",
                    "authoringModel", "model-v1",
                    "authoringPromptVersion", "prompt-v1",
                    "authoringSeed", 0));
        };
        SkillEvolutionMultiCandidateAuthoringService service =
                new SkillEvolutionMultiCandidateAuthoringService(legacy);

        assertEquals(1, service.author(Map.of(), 1).size());
        assertEquals(4, service.author(Map.of(), 9).size());
        assertEquals(1, service.author(Map.of(), 0).size());
        assertEquals(6, requests.size());
        assertEquals("MAINTENANCE_REVIEW", requests.get(0).get("candidateDirection"));
        assertEquals("MAINTENANCE_REVIEW", requests.get(5).get("candidateDirection"));
    }

    @Test void emptyAdapterResponseDoesNotSpendAdditionalPhysicalAttempts() {
        var port=new SkillEvolutionAuthoringPort() {
            @Override public SkillEvolutionAuthoredCandidate author(Map<String,Object> input) {
                throw new AssertionError("No implicit fallback author request is permitted");
            }
            @Override public List<SkillEvolutionAuthoredCandidate> authorCandidates(Map<String,Object> input,int budget) {
                assertEquals(1,budget); return List.of();
            }
        };
        var result=new SkillEvolutionMultiCandidateAuthoringService(port).author(Map.of(),1);
        assertEquals(1,result.size());
        assertTrue(!result.get(0).candidate().reusableChange());
        assertEquals(1,result.get(0).audit().candidateBudget());
    }

    @Test
    void evidenceDiagnosisMustPrioritizeEvidencePatch() {
        SkillEvolutionAuthoringPort legacy = input -> SkillEvolutionAuthoredCandidate.from(Map.of(
                "patchType", "PATCH",
                "changes", List.of(Map.of("operation", "ADD", "path", "/evidence")),
                "authoringModel", "model-v1",
                "authoringPromptVersion", "prompt-v1"));
        SkillEvolutionMultiCandidateAuthoringService service =
                new SkillEvolutionMultiCandidateAuthoringService(legacy);

        List<SkillEvolutionAuthoredCandidateOption> options = service.author(Map.of(
                "defectDiagnoses", List.of(Map.of("layer", "EVIDENCE"))), 2);

        assertEquals(SkillAuthoringDirection.EVIDENCE_PATCH,
                options.get(0).audit().direction());
        assertEquals(SkillAuthoringDirection.MINIMAL_PATCH,
                options.get(1).audit().direction());
    }
}
