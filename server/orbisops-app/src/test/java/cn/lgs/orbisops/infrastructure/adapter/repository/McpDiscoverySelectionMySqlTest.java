package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.mcp.McpDiscoverySelectionStore.*;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class McpDiscoverySelectionMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("mcp_discovery").withUsername("fixture").withPassword("fixture")
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(
                    new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",0),
                            new com.github.dockerjava.api.model.ExposedPort(3306))));
    JdbcTemplate jdbc;
    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(ds);
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("db/migrations/sql/ops-mcp-discovery-selection.sql"))) root = root.getParent();
        assertNotNull(root);
        try (var connection = ds.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(root.resolve("db/migrations/sql/ops-mcp-discovery-selection.sql")));
        }
    }
    Selection small = new Selection("SUMMARY", 12, 749, "O200K_BASE", "a".repeat(64));
    Selection large = new Selection("SEARCH", 36, 2045, "O200K_BASE", "b".repeat(64));
    @Test void restartAndCatalogGrowthPreserveTheOriginalModeButNewRunsAndScopesRemainIndependent() {
        String run = UUID.randomUUID().toString();
        var scope = new Scope("a", run, "agent", "node");
        assertEquals(small, new JdbcMcpDiscoverySelectionStore(jdbc).freeze(scope, small));
        assertEquals(small, new JdbcMcpDiscoverySelectionStore(jdbc).freeze(scope, large));
        for (Scope other : List.of(new Scope("b",run,"agent","node"), new Scope("a",run+"new","agent","node"),
                new Scope("a",run,"other-agent","node"),new Scope("a",run,"agent","other-node")))
            assertEquals(large, new JdbcMcpDiscoverySelectionStore(jdbc).freeze(other, large));
    }
    @Test void eightConcurrentStartsObserveExactlyOneFrozenSelection() throws Exception {
        var scope = new Scope("concurrent", UUID.randomUUID().toString(), "agent", "node");
        var pool = Executors.newFixedThreadPool(8);
        var gate = new CountDownLatch(1);
        try {
            var futures = new ArrayList<Future<Selection>>();
            for (int i=0; i<8; i++) {
                var proposed = i%2==0 ? small : large;
                futures.add(pool.submit(() -> { gate.await(); return new JdbcMcpDiscoverySelectionStore(jdbc).freeze(scope,proposed); }));
            }
            gate.countDown();
            var observations = new HashSet<Selection>();
            for (var future : futures) observations.add(future.get(10, TimeUnit.SECONDS));
            assertEquals(1, observations.size());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_mcp_discovery_selection WHERE run_id=?", Integer.class, scope.runId()));
        } finally { pool.shutdownNow(); }
    }
}
