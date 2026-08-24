package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.SkillRouteProjectionIdentity;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.ExposedPort;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real isolated pgvector and transactions, with explicit synthetic vectors (not Qwen inference). */
@Testcontainers(disabledWithoutDocker=true)
class PgSkillRouteIndexTest {
    @Container static final GenericContainer<?> PG=new GenericContainer<>("pgvector/pgvector:pg16")
            .withEnv("POSTGRES_DB","route_test").withEnv("POSTGRES_USER","fixture").withEnv("POSTGRES_PASSWORD","fixture")
            .withExposedPorts(5432).withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),new ExposedPort(5432))))
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n",2).withStartupTimeout(Duration.ofSeconds(60)));
    JdbcTemplate jdbc;PgSkillRouteIndexRepository index;String p;
    private static final String MODEL="SYNTHETIC_UNIT_VECTOR:1024:v1";
    @BeforeEach void init() {
        jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://"+PG.getHost()+":"+PG.getMappedPort(5432)+"/route_test","fixture","fixture"));
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");index=new PgSkillRouteIndexRepository(jdbc);index.initialize();p="p-"+UUID.randomUUID();
    }
    @Test void readyGenerationRequiresBothRowsAndOldVersionSurvivesIncompleteReplacement() {
        var old=skill("s",p,1);var next=skill("s",p,2);index.stage(old,MODEL);
        assertTrue(index.search(p,List.of(old),MODEL,vector(0),20).isEmpty());index.ready(old,MODEL,vector(0));
        index.stage(next,MODEL);assertFalse(index.contains(next,MODEL));assertEquals(1,index.search(p,List.of(old),MODEL,vector(0),20).size());
        assertTrue(index.search(p,List.of(next),MODEL,vector(0),20).isEmpty());
        index.ready(next,MODEL,vector(1));assertTrue(index.contains(next,MODEL));
        assertEquals(1D,index.search(p,List.of(old),MODEL,vector(0),20).get("s"),1e-6);
    }
    @Test void rollbackOnDocumentConflictKeepsGenerationInvisibleThenRetryRecovers() {
        var c=skill("s",p,1);index.stage(c,MODEL);String key=SkillRouteProjectionIdentity.key(c,MODEL);
        jdbc.update("INSERT INTO ops_skill_route_document VALUES (?,?,'synthetic conflicting row',?::vector)",key,"0".repeat(64),Arrays.toString(vector(0)));
        assertThrows(RuntimeException.class,()->index.ready(c,MODEL,vector(0)));assertFalse(index.contains(c,MODEL));assertTrue(index.search(p,List.of(c),MODEL,vector(0),20).isEmpty());
        // Disposable failure fixture only; acceptance-stack volumes/data are never deleted.
        jdbc.update("DELETE FROM ops_skill_route_document WHERE generation_id=?",key);index.ready(c,MODEL,vector(0));assertTrue(index.contains(c,MODEL));
    }
    @Test void filteredUnderfillNeverReturnsForeignOrUnallowedSameProjectDocuments() {
        var allowed=skill("allowed",p,1);var sameProject=skill("not-granted",p,1);var foreign=skill("foreign","different",1);
        for(var c:List.of(allowed,sameProject,foreign)) {index.stage(c,MODEL);index.ready(c,MODEL,vector(0));}
        assertEquals(Set.of("allowed"),index.search(p,List.of(allowed),MODEL,vector(0),20).keySet());
        assertThrows(SecurityException.class,()->index.search(p,List.of(foreign),MODEL,vector(0),20));
        assertTrue(index.search(p,List.of(allowed),MODEL+"changed",vector(0),20).isEmpty());
        assertThrows(IllegalArgumentException.class,()->index.search(p,List.of(allowed),MODEL,new float[512],20));
    }
    @Test void concurrentDuplicateProjectionCommitsOneImmutableRow() throws Exception {
        var c=skill("s",p,1);index.stage(c,MODEL);var executor=Executors.newFixedThreadPool(8);
        try {
            var tasks=new ArrayList<Callable<Boolean>>();for(int i=0;i<8;i++) tasks.add(()->{ index.ready(c,MODEL,vector(0));return true;});
            for(var future:executor.invokeAll(tasks)) assertTrue(future.get(10,TimeUnit.SECONDS));
        } finally {executor.shutdownNow();}
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM ops_skill_route_document WHERE generation_id=?",Integer.class,SkillRouteProjectionIdentity.key(c,MODEL)));
    }
    @Test void unavailableDataSourceCanStartButCannotClaimAProjectionOrPerformVectorSearch() {
        var disabled=new PgSkillRouteIndexRepository((JdbcTemplate)null);
        assertDoesNotThrow(disabled::initialize);
        var c=skill("s",p,1);
        assertThrows(IllegalStateException.class,()->disabled.stage(c,MODEL));
        assertThrows(IllegalStateException.class,()->disabled.contains(c,MODEL));
        assertThrows(IllegalStateException.class,()->disabled.search(p,List.of(c),MODEL,vector(0),20));
    }
    @Test void duplicateAuthorizedReferencesDoNotConsumeResultCapacityOrChangeScope() {
        var first=skill("first",p,1);var second=skill("second",p,1);var notGranted=skill("not-granted",p,1);
        for(var c:List.of(first,second,notGranted)) {index.stage(c,MODEL);index.ready(c,MODEL,vector(0));}
        var authorized=new ArrayList<SkillRuntimeCandidate>(Collections.nCopies(100,first));authorized.add(second);
        assertEquals(Set.of("first","second"),index.search(p,authorized,MODEL,vector(0),2).keySet());
    }
    @Test void actualPgvectorSearchMustKeepTheAuthorizationBoundaryAtTenThousandRows() {
        // Capacity fixture only: real pgvector, synthetic unit vectors, no model-quality claim.
        var candidates = new ArrayList<SkillRuntimeCandidate>();
        for (int i = 0; i < 10_000; i++) candidates.add(skill("capacity-" + i, p, 1));
        var vectors = new ArrayList<String>();
        for (int i = 0; i < 32; i++) vectors.add(Arrays.toString(vector(i)));
        jdbc.batchUpdate("INSERT INTO ops_skill_route_generation(generation_id,scope,project_id,skill_id,skill_version,"
                + "skill_hash,package_hash,model_identity,dimension,status) VALUES (?,'PROJECT',?,?,1,?,?,?,1024,'READY')",
                candidates, 500, (statement, c) -> {
                    statement.setString(1, SkillRouteProjectionIdentity.key(c, MODEL));
                    statement.setString(2, p); statement.setString(3, c.skillId());
                    statement.setString(4, c.skillHash()); statement.setString(5, c.packageHash()); statement.setString(6, MODEL);
                });
        jdbc.batchUpdate("INSERT INTO ops_skill_route_document(generation_id,content_hash,routing_text,embedding) VALUES (?,?,?,?::vector)",
                candidates, 500, (statement, c) -> {
                    String document = SkillRouteProjectionIdentity.document(c);
                    statement.setString(1, SkillRouteProjectionIdentity.key(c, MODEL));
                    statement.setString(2, cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher.sha256Text(document));
                    statement.setString(3, document);
                    statement.setString(4, vectors.get(Integer.parseInt(c.skillId().substring("capacity-".length())) % 32));
                });
        jdbc.execute("ANALYZE ops_skill_route_generation"); jdbc.execute("ANALYZE ops_skill_route_document");
        for (int size : List.of(100, 1000, 10_000)) {
            var authorized = candidates.subList(0, size);
            long started = System.nanoTime();
            var results = index.search(p, authorized, MODEL, vector(0), 20);
            double elapsedMs = (System.nanoTime() - started) / 1_000_000D;
            assertEquals(20, results.size());
            assertTrue(results.keySet().stream().allMatch(id -> Integer.parseInt(id.substring("capacity-".length())) < size));
            assertEquals(Math.min(20, (size + 31) / 32), results.values().stream().filter(score -> Math.abs(score - 1D) < 1e-6).count());
            System.out.println("PGVECTOR_CAPACITY_REAL_DATABASE " + cn.lgs.orbisops.domain.shared.json.CanonicalJson.stringify(
                    Map.of("authorizedRows", size, "storedRows", 10_000, "returnedRows", results.size(), "elapsedMs", elapsedMs,
                            "vectors", "SYNTHETIC_UNIT_VECTOR", "scopeBoundaryPassed", true, "statementBudgetMs", 1000)));
        }
        assertTrue(index.search(p, List.of(candidates.get(0)), MODEL, vector(0), 20).keySet().equals(Set.of("capacity-0")));
    }

    private static float[] vector(int coordinate) {float[] v=new float[1024];v[coordinate]=1;return v;}
    private static SkillRuntimeCandidate skill(String id,String project,int version) {
        return new SkillRuntimeCandidate(id,project,"PROJECT",id,"inspect logs",version,"hash"+version,"package"+version,"manifest",Map.of(),"SKILL.md","ACTIVE","AUTO",20,
                new SkillRoutingProfile("GENERAL","","inspect logs",List.of("inspect logs"),List.of("delete production"),List.of(id)));
    }
}
