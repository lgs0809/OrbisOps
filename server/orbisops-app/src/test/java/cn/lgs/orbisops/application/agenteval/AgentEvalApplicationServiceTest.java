package cn.lgs.orbisops.application.agenteval;

import cn.lgs.orbisops.domain.agenteval.adapter.repository.IAgentEvalRepository;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalCase;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalDefinitionSnapshot;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalEdge;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalNode;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunResult;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalRunStart;
import cn.lgs.orbisops.domain.agenteval.model.AgentEvalSuite;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentEvalApplicationServiceTest {

    @Test
    void createSuiteUsesStableOrderingForExplicitAndGeneratedCaseIds() {
        FakeRepository repository = new FakeRepository();
        FakeAudit audit = new FakeAudit();
        AgentEvalApplicationService service = service(repository, audit,
                definition(3, "a".repeat(64), Map.of()), Optional.empty());

        AgentEvalSuite saved = service.createSuite(new AgentEvalCreateSuiteCommand(
                "",
                "project-1",
                "agent-1",
                "gate",
                List.of(
                        evalCase("explicit-case", Map.of("expectedIntent", "OPS_INVESTIGATION")),
                        evalCase("", Map.of("requiredNodes", List.of("end")))),
                "alice"));

        assertEquals("agent-eval-suite-fixed", saved.suiteId());
        assertEquals("explicit-case", saved.cases().get(0).caseId());
        assertEquals("agent-eval-suite-fixed-case-2", saved.cases().get(1).caseId());
        assertEquals(saved, repository.savedSuite);
        assertEquals(saved, audit.createdSuite);
    }

    @Test
    void runPersistsCandidateBaselineAndCaseResultsInOneAtomicRepositoryCall() {
        FakeRepository repository = new FakeRepository();
        FakeAudit audit = new FakeAudit();
        AgentEvalDefinitionSnapshot candidate = definition(
                3, "a".repeat(64), Map.of("writesTargetResource", true));
        AgentEvalDefinitionSnapshot baseline = definition(2, "b".repeat(64), Map.of());
        repository.suite = new AgentEvalSuite(
                "suite-1", "project-1", "agent-1", "gate", 1,
                List.of(evalCase("case-1", Map.of("expectedIntent", "OPS_INVESTIGATION"))),
                "alice");
        AgentEvalApplicationService service = service(repository, audit, candidate, Optional.of(baseline));

        AgentEvalRunResult result = service.run("project-1", "agent-1", 3, "suite-1", "alice");

        assertEquals("FAILED", result.status());
        assertEquals("FAILED", result.regressionStatus());
        assertEquals(2, result.baselineVersion());
        assertEquals(1, repository.saveRunCalls);
        assertNotNull(repository.runStart);
        assertNotNull(repository.runResult);
        assertNotNull(repository.finishedAt);
        assertEquals("agent-eval-run-fixed", repository.runStart.evalRunId());
        assertEquals("b".repeat(64), repository.runStart.baselineDefinitionHash());
        assertEquals(1, repository.runResult.caseExecutions().size());
        assertEquals("agent-eval-case-run-1",
                repository.runResult.caseExecutions().get(0).caseRunId());
        assertEquals(result, audit.completedRun);
        assertEquals("alice", audit.completedActor);
    }

    @Test
    void releaseGateMatchesProjectAgentVersionAndDefinitionHashExactly() {
        FakeRepository repository = new FakeRepository();
        AgentEvalApplicationService service = service(repository, new FakeAudit(),
                definition(3, "a".repeat(64), Map.of()), Optional.empty());

        repository.releaseGatePassed = false;
        assertThrows(IllegalStateException.class,
                () -> service.assertReleaseAllowed("project-1", "agent-1", 3, "a".repeat(64)));
        assertEquals("project-1", repository.gateProjectId);
        assertEquals("agent-1", repository.gateAgentId);
        assertEquals(3, repository.gateVersion);
        assertEquals("a".repeat(64), repository.gateDefinitionHash);

        repository.releaseGatePassed = true;
        assertDoesNotThrow(
                () -> service.assertReleaseAllowed("project-1", "agent-1", 3, "a".repeat(64)));
        assertThrows(IllegalStateException.class,
                () -> service.assertReleaseAllowed("project-1", "agent-1", null, ""));
    }

    @Test
    void runRejectsCrossProjectDefinitionsAndSuitesForOtherAgents() {
        FakeRepository repository = new FakeRepository();
        AgentEvalDefinitionSnapshot otherProject = new AgentEvalDefinitionSnapshot(
                "project-2", "agent-1", 3, "a".repeat(64), "VALIDATED", "start",
                List.of(new AgentEvalNode("start", "START", Map.of()),
                        new AgentEvalNode("end", "END", Map.of())),
                List.of(new AgentEvalEdge("start", "end")),
                List.of());
        AgentEvalApplicationService crossProjectService = service(
                repository, new FakeAudit(), otherProject, Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> crossProjectService.run("project-1", "agent-1", 3, "suite-1", "alice"));

        repository.suite = new AgentEvalSuite(
                "suite-1", "project-1", "agent-2", "gate", 1,
                List.of(evalCase("case-1", Map.of("requiredNodes", List.of("end")))), "alice");
        AgentEvalApplicationService wrongAgentService = service(
                repository, new FakeAudit(),
                definition(3, "a".repeat(64), Map.of()), Optional.empty());
        assertThrows(IllegalArgumentException.class,
                () -> wrongAgentService.run("project-1", "agent-1", 3, "suite-1", "alice"));
    }

    private AgentEvalApplicationService service(
            FakeRepository repository,
            FakeAudit audit,
            AgentEvalDefinitionSnapshot candidate,
            Optional<AgentEvalDefinitionSnapshot> baseline) {
        AgentEvalDefinitionPort definitions = new AgentEvalDefinitionPort() {
            @Override
            public AgentEvalDefinitionSnapshot resolve(String agentId, int version) {
                return candidate;
            }

            @Override
            public Optional<AgentEvalDefinitionSnapshot> publishedBaseline(
                    String projectId, String agentId, int excludedVersion) {
                return baseline;
            }
        };
        AtomicInteger tokens = new AtomicInteger();
        AgentEvalIdentityFactory identities = new AgentEvalIdentityFactory(
                () -> {
                    int sequence = tokens.incrementAndGet();
                    return sequence == 1 ? "fixed" : String.valueOf(sequence - 1);
                },
                new TickingClock());
        return new AgentEvalApplicationService(
                repository, definitions, audit, identities);
    }

    private AgentEvalCase evalCase(String caseId, Map<String, Object> assertions) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("caseId", caseId);
        values.put("name", "case");
        values.put("input", "帮我查最近十分钟错误日志");
        values.putAll(assertions);
        return AgentEvalCase.fromMap(values);
    }

    private AgentEvalDefinitionSnapshot definition(
            int version,
            String hash,
            Map<String, Object> investigateConfig) {
        return new AgentEvalDefinitionSnapshot(
                "project-1",
                "agent-1",
                version,
                hash,
                version == 2 ? "PUBLISHED" : "VALIDATED",
                "start",
                List.of(
                        new AgentEvalNode("start", "START", Map.of()),
                        new AgentEvalNode("investigate", "AGENT", investigateConfig),
                        new AgentEvalNode("end", "END", Map.of())),
                List.of(
                        new AgentEvalEdge("start", "investigate"),
                        new AgentEvalEdge("investigate", "end")),
                List.of("INVESTIGATOR"));
    }

    private static final class FakeRepository implements IAgentEvalRepository {
        private AgentEvalSuite suite;
        private AgentEvalSuite savedSuite;
        private AgentEvalRunStart runStart;
        private AgentEvalRunResult runResult;
        private Instant finishedAt;
        private int saveRunCalls;
        private boolean releaseGatePassed;
        private String gateProjectId;
        private String gateAgentId;
        private int gateVersion;
        private String gateDefinitionHash;

        @Override
        public AgentEvalSuite saveSuite(AgentEvalSuite suite) {
            this.savedSuite = suite;
            this.suite = suite;
            return suite;
        }

        @Override
        public Optional<AgentEvalSuite> findSuite(String suiteId, String projectId) {
            return Optional.ofNullable(suite)
                    .filter(value -> value.suiteId().equals(suiteId) && value.projectId().equals(projectId));
        }

        @Override
        public void saveRun(AgentEvalRunStart run, AgentEvalRunResult result, Instant finishedAt) {
            saveRunCalls++;
            runStart = run;
            runResult = result;
            this.finishedAt = finishedAt;
        }

        @Override
        public boolean hasPassedReleaseGate(
                String projectId, String agentId, int version, String definitionHash) {
            gateProjectId = projectId;
            gateAgentId = agentId;
            gateVersion = version;
            gateDefinitionHash = definitionHash;
            return releaseGatePassed;
        }
    }

    private static final class FakeAudit implements AgentEvalAuditPort {
        private AgentEvalSuite createdSuite;
        private AgentEvalRunResult completedRun;
        private String completedActor;

        @Override
        public void recordSuiteCreated(AgentEvalSuite suite) {
            createdSuite = suite;
        }

        @Override
        public void recordRunCompleted(AgentEvalRunResult result, String actor) {
            completedRun = result;
            completedActor = actor;
        }
    }

    private static final class TickingClock extends Clock {
        private final AtomicInteger tick = new AtomicInteger();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.parse("2026-07-23T00:00:00Z").plusMillis(tick.getAndIncrement());
        }
    }
}
