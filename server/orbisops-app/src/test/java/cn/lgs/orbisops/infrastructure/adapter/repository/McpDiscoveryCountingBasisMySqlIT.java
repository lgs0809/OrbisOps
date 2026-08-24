package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore.*;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="ORBISOPS_DISCOVERY_TEST_JDBC_URL", matches=".+")
class McpDiscoveryCountingBasisMySqlIT {
    JdbcTemplate jdbc;
    Scope legacy;
    @BeforeEach void migratePreservingOldSelection() throws Exception {
        var ds = OrbisOpsExternalMySqlFixture.dataSource();
        jdbc = new JdbcTemplate(ds);
        OrbisOpsExternalMySqlFixture.apply(ds, "ops-mcp-discovery-selection.sql");
        legacy = new Scope("it", "legacy-" + UUID.randomUUID(), "agent", "node");
        jdbc.update("""
                INSERT INTO ai_ops_mcp_discovery_selection
                (scope_hash,project_id,run_id,agent_id,node_id,discovery_mode,tool_count,summary_tokens,tokenizer,catalog_hash)
                VALUES (?,?,?,?,?,'SUMMARY',2,84,'O200K_BASE',?)
                """, CanonicalObjectHasher.sha256(legacy), legacy.projectId(), legacy.runId(), legacy.agentId(), legacy.nodeId(), "a".repeat(64));
        OrbisOpsExternalMySqlFixture.apply(ds, "ops-mcp-discovery-counting-basis.sql");
        OrbisOpsExternalMySqlFixture.apply(ds, "ops-mcp-discovery-counting-basis.sql");
    }
    private Selection proposal(String model, String encoding, int limit) {
        return new Selection("SEARCH", 21, 2100, encoding, "b".repeat(64), new CountingBasis(
                "mcp-summary-count/v1", "org.springframework.ai.tokenizer.JTokkitTokenCountEstimator", "fixture-version",
                "fixture-version", encoding, "ESTIMATE", model, model, 20, 2000, limit));
    }
    @Test void nullableMigrationLeavesOldRowsUncertifiedAndCanBeRerun() {
        var restored = new JdbcMcpDiscoverySelectionStore(jdbc).freeze(legacy, proposal("new-model", "CL100K_BASE", 5));
        assertEquals("SUMMARY", restored.mode()); assertEquals(84, restored.summaryTokens());
        assertEquals("O200K_BASE", restored.tokenizer()); assertNull(restored.countingBasis());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_ops_mcp_discovery_selection' AND column_name='counting_basis_json'", Integer.class));
    }
    @Test void concurrentStartsAndFreshRepositoryKeepExactlyOneFullCountingBasis() throws Exception {
        var scope = new Scope("it", "concurrent-" + UUID.randomUUID(), "agent", "node");
        var a = proposal("gpt-6-luna", "O200K_BASE", 1);
        var b = proposal("gpt-6.1-terra", "CL100K_BASE", 5);
        var pool = Executors.newFixedThreadPool(8); var gate = new CountDownLatch(1);
        try {
            var pending = new ArrayList<Future<Selection>>();
            for (int i=0; i<8; i++) { var proposed = i%2 == 0 ? a : b;
                pending.add(pool.submit(() -> {gate.await(); return new JdbcMcpDiscoverySelectionStore(jdbc).freeze(scope, proposed);})); }
            gate.countDown(); var values = new HashSet<Selection>();
            for (var future : pending) values.add(future.get(10, TimeUnit.SECONDS));
            assertEquals(1, values.size()); var frozen = values.iterator().next();
            assertTrue(frozen.equals(a) || frozen.equals(b));
            assertEquals(frozen, new JdbcMcpDiscoverySelectionStore(jdbc).freeze(scope, proposal("later-model", "CL100K_BASE", 3)));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_mcp_discovery_selection WHERE run_id=?", Integer.class, scope.runId()));
            assertEquals("ESTIMATE", jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(counting_basis_json,'$.accuracy')) FROM ai_ops_mcp_discovery_selection WHERE run_id=?", String.class, scope.runId()));
        } finally { pool.shutdownNow(); }
    }
    @Test void independentRunAndNodeCanSelectDifferentPolicies() {
        var first = new Scope("it", "scope-" + UUID.randomUUID(), "agent", "node");
        var store = new JdbcMcpDiscoverySelectionStore(jdbc);
        var a = proposal("gpt-6-luna", "O200K_BASE", 1);
        var b = proposal("gpt-6.1-terra", "CL100K_BASE", 5);
        assertEquals(a, store.freeze(first, a));
        assertEquals(b, store.freeze(new Scope("it", first.runId()+"-next", "agent", "node"), b));
        assertEquals(b, store.freeze(new Scope("it", first.runId(), "agent", "other-node"), b));
        assertEquals(a, store.freeze(first, b));
    }
}
