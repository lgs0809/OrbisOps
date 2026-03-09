package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillDefectDiagnosis;
import cn.lgs.orbisops.domain.skill.model.SkillDefectLayer;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationMemory;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationMemoryType;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationRun;
import cn.lgs.orbisops.domain.skill.model.SkillOptimizationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillOptimizationApplicationServicesTest {

    @Test
    void diagnosisAndMemoryMustRemainTypedOptimizationFacts() {
        InMemoryPort port = new InMemoryPort();
        StepClock clock = new StepClock();
        SkillDefectDiagnosisApplicationService diagnosisService =
                new SkillDefectDiagnosisApplicationService(port, clock);
        SkillOptimizationMemoryApplicationService memoryService =
                new SkillOptimizationMemoryApplicationService(port, clock);

        SkillDefectDiagnosis diagnosis = diagnosisService.record(new SkillDefectDiagnosisCommand(
                "diagnosis-1", "skill-1", 3, SkillDefectLayer.ROUTING,
                "false positive", "routing boundary too broad",
                List.of("trajectory-b", "trajectory-a", "trajectory-a"),
                List.of("counterexample-1"), 0.92, "narrow whenToUse"));
        SkillOptimizationMemory memory = memoryService.record(new SkillOptimizationMemoryCommand(
                "memory-1", "skill-1", 3, SkillOptimizationMemoryType.ROUTING_FALSE_POSITIVE,
                "Broad routing boundary repeatedly selected unrelated requests",
                List.of("diagnosis-1", "evaluation-1"), "model-v2", "prod-readonly", false));

        assertEquals(List.of("trajectory-a", "trajectory-b"), diagnosis.supportingTrajectoryIds());
        assertEquals(SkillOptimizationMemoryType.ROUTING_FALSE_POSITIVE, memory.type());
        assertEquals(List.of(diagnosis), diagnosisService.diagnoses("skill-1", 3, 20));
        assertEquals(List.of(memory), memoryService.memories("skill-1", 20));
        assertEquals(20, port.lastDiagnosisLimit);
        assertEquals(20, port.lastMemoryLimit);
    }

    @Test
    void runServiceMustPersistEveryBoundedTransition() {
        InMemoryPort port = new InMemoryPort();
        SkillOptimizationRunApplicationService service =
                new SkillOptimizationRunApplicationService(port, new StepClock());

        service.start("optimization-1", "skill-1", 3, "base-hash", 2);
        service.startRound("optimization-1", List.of("diagnosis-1"));
        service.beginEvaluation("optimization-1", List.of("candidate-a", "candidate-b"));
        SkillOptimizationRun finished = service.completeRound(
                "optimization-1", List.of("evaluation-a"), "candidate-b",
                "verifier-v1", "PROMOTABLE", true);

        assertEquals(SkillOptimizationStatus.SUCCEEDED, finished.status());
        assertEquals(4, port.runSaves);
        assertEquals("candidate-b", service.get("optimization-1")
                .rounds().get(0).selectedCandidateId());
        assertThrows(IllegalStateException.class, () -> service.startRound(
                "optimization-1", List.of("diagnosis-2")));
    }

    @Test
    void invalidConfidenceAndRoundBudgetMustFailBeforePersistence() {
        InMemoryPort port = new InMemoryPort();
        SkillDefectDiagnosisApplicationService diagnosisService =
                new SkillDefectDiagnosisApplicationService(port, new StepClock());
        SkillOptimizationRunApplicationService runService =
                new SkillOptimizationRunApplicationService(port, new StepClock());

        assertThrows(IllegalArgumentException.class, () -> diagnosisService.record(
                new SkillDefectDiagnosisCommand(
                        "diagnosis-1", "skill-1", 3, SkillDefectLayer.EVIDENCE,
                        "symptom", "root cause", List.of(), List.of(), 1.1,
                        "direction")));
        assertThrows(IllegalArgumentException.class, () -> runService.start(
                "optimization-1", "skill-1", 3, "base-hash", 4));
        assertEquals(0, port.runSaves);
    }

    private static final class InMemoryPort implements
            SkillDefectDiagnosisPort,
            SkillOptimizationMemoryPort,
            SkillOptimizationRunPort {
        private final List<SkillDefectDiagnosis> diagnoses = new ArrayList<>();
        private final List<SkillOptimizationMemory> memories = new ArrayList<>();
        private final Map<String, SkillOptimizationRun> runs = new LinkedHashMap<>();
        private int lastDiagnosisLimit;
        private int lastMemoryLimit;
        private int runSaves;

        @Override
        public SkillDefectDiagnosis save(SkillDefectDiagnosis diagnosis) {
            diagnoses.add(diagnosis);
            return diagnosis;
        }

        @Override
        public List<SkillDefectDiagnosis> findBySkill(
                String skillId,
                long skillVersion,
                int limit) {
            lastDiagnosisLimit = limit;
            return diagnoses.stream().filter(item -> item.skillId().equals(skillId)
                    && item.skillVersion() == skillVersion).limit(limit).toList();
        }

        @Override
        public SkillOptimizationMemory save(SkillOptimizationMemory memory) {
            memories.add(memory);
            return memory;
        }

        @Override
        public List<SkillOptimizationMemory> findBySkill(String skillId, int limit) {
            lastMemoryLimit = limit;
            return memories.stream().filter(item -> item.skillId().equals(skillId))
                    .limit(limit).toList();
        }

        @Override
        public SkillOptimizationRun save(SkillOptimizationRun run) {
            runSaves++;
            runs.put(run.runId(), run);
            return run;
        }

        @Override
        public SkillOptimizationRun get(String runId) {
            return runs.get(runId);
        }
    }

    private static final class StepClock implements SkillOptimizationClockPort {
        private final AtomicInteger step = new AtomicInteger();

        @Override
        public Instant now() {
            return Instant.parse("2026-08-02T00:00:00Z").plusSeconds(step.getAndIncrement());
        }
    }
}
