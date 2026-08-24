package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.mcp.*;
import cn.lgs.orbisops.application.mcp.McpToolCatalogStore.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic catalogs in real MySQL; source-generation atomicity, not a model-quality claim. */
@Testcontainers(disabledWithoutDocker=true)
class McpToolCatalogMySqlTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0.36")
        .withDatabaseName("mcp_catalog").withUsername("fixture").withPassword("fixture")
        .withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withPortBindings(
            new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",0),new com.github.dockerjava.api.model.ExposedPort(3306))));
    JdbcTemplate jdbc;McpToolCatalogStore store;McpToolCatalogService service;Scope scope;
    @BeforeEach void setup() throws Exception {
        var ds=new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());jdbc=new JdbcTemplate(ds);
        Path root=Path.of("").toAbsolutePath();while(root!=null&&!Files.exists(root.resolve("db/migrations/sql/ops-mcp-remote-catalog.sql"))) root=root.getParent();assertNotNull(root);
        try(var c=ds.getConnection()) {ScriptUtils.executeSqlScript(c,new FileSystemResource(root.resolve("db/migrations/sql/ops-mcp-remote-catalog.sql")));}
        var proxy=new ProxyFactory(new JdbcMcpToolCatalogStore(jdbc));proxy.setProxyTargetClass(true);
        proxy.addAdvice(new TransactionInterceptor(new DataSourceTransactionManager(ds),new AnnotationTransactionAttributeSource()));
        store=(McpToolCatalogStore)proxy.getProxy();service=new McpToolCatalogService(store,Clock.systemUTC());
        scope=new Scope(UUID.randomUUID().toString(),"synthetic-project","synthetic-server");
    }
    Map<String,Object> tool(String name,String description,Map<String,Object> properties) {
        return Map.of("name",name,"description",description,"inputSchema",Map.of("type","object","properties",properties));
    }
    long versions() {return jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_mcp_remote_catalog_version WHERE identity_hash=?",Long.class,scope.identity());}
    @Test void initialFullSnapshotSurvivesRestartAndUnchangedRefreshDoesNotCreateVersions() {
        var calls=new AtomicInteger();var t=tool("metrics","query",Map.of("x",Map.of("type","string")));
        var first=service.current(scope,()->{calls.incrementAndGet();return List.of(t);});assertEquals(1,first.generation());
        var restarted=new McpToolCatalogService(new JdbcMcpToolCatalogStore(jdbc),Clock.systemUTC());
        assertEquals(first.contentHash(),restarted.current(scope,()->{fail("restart must not fetch tools/list");return List.of();}).contentHash());
        var reorder=new LinkedHashMap<String,Object>();reorder.put("inputSchema",t.get("inputSchema"));reorder.put("description","query");reorder.put("name","metrics");
        assertEquals(1,service.refresh(scope,()->List.of(reorder)).generation());assertEquals(1,versions());assertEquals(1,calls.get());
    }
    @Test void completeChangesPublishTogetherButPageFailureOrMalformedToolKeepsOldGeneration() {
        var original=service.current(scope,()->List.of(tool("old","read",Map.of())));
        assertThrows(IllegalStateException.class,()->service.refresh(scope,()->{throw new IllegalStateException("page two timeout with secret");}));
        assertEquals(original.contentHash(),store.find(scope.identity()).orElseThrow().contentHash());
        assertEquals("MCP_CATALOG_REFRESH_FAILED",store.find(scope.identity()).orElseThrow().errorCode());
        assertThrows(IllegalArgumentException.class,()->service.refresh(scope,()->List.of(tool("ok","read",Map.of()),Map.of("name","bad"))));
        assertEquals(1,versions());
        var changed=service.refresh(scope,()->List.of(tool("renamed","new description",Map.of("requiredValue",Map.of("type","integer")))));
        assertEquals(2,changed.generation());assertFalse(changed.toolsJson().contains("\"old\""));assertEquals("",changed.errorCode());
        assertEquals(3,service.refresh(scope,List::of).generation());assertEquals("[]",store.find(scope.identity()).orElseThrow().toolsJson());
    }
    @Test void concurrentRefreshesCannotOverwriteNewerCatalogAndLateFailureCannotEraseSuccess() throws Exception {
        var old=service.current(scope,()->List.of(tool("first","one",Map.of())));
        var start=Instant.now().plusSeconds(1);var pool=Executors.newFixedThreadPool(8);var gate=new CountDownLatch(1);
        try {
            var work=new ArrayList<Future<Snapshot>>();
            for(int i=0;i<8;i++) {int n=i;work.add(pool.submit(()->{gate.await();return store.publish(scope,old.generation(),"hash-"+n,"[]",start);}));}
            gate.countDown();for(var f:work) assertEquals(2,f.get(10,TimeUnit.SECONDS).generation());
            assertEquals(2,versions());String winner=store.find(scope.identity()).orElseThrow().contentHash();
            store.failed(scope,start.minusSeconds(1),"LATE_FAILURE");
            var current=store.find(scope.identity()).orElseThrow();assertEquals(winner,current.contentHash());assertEquals("",current.errorCode());
            assertEquals(winner,store.publish(scope,1,"stale","[]",start.plusSeconds(2)).contentHash());
        } finally {pool.shutdownNow();}
    }
    @Test void credentialsAndProjectsUseIndependentCatalogIdentities() {
        var other=new Scope(UUID.randomUUID().toString(),"other-project",scope.serverId());
        service.current(scope,()->List.of(tool("a","a",Map.of())));
        service.current(other,()->List.of(tool("b","b",Map.of())));
        assertNotEquals(store.find(scope.identity()).orElseThrow().toolsJson(),store.find(other.identity()).orElseThrow().toolsJson());
        assertTrue(store.scopesAfter("",200).containsAll(List.of(scope,other)));
        assertTrue(store.status("synthetic-project").stream().noneMatch(row->row.connectionId().equals(other.identity())));
        assertEquals(1,store.status("other-project").get(0).toolCount());
    }
}
