package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.application.skill.*;
import cn.lgs.orbisops.domain.skill.model.*;
import cn.lgs.orbisops.domain.skill.service.*;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;
import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;
import com.github.dockerjava.api.model.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Both real databases, synthetic packages/unit vectors. No accepted tasks or model success are fabricated. */
@Testcontainers(disabledWithoutDocker=true)
class SkillRuntimePublicationMySqlPgTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("publication").withUsername("fixture").withPassword("fixture")
            .withCreateContainerCmdModifier(c -> c.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),new ExposedPort(3306))));
    @Container static final GenericContainer<?> PG = new GenericContainer<>("pgvector/pgvector:pg16")
            .withEnv("POSTGRES_DB","publication").withEnv("POSTGRES_USER","fixture").withEnv("POSTGRES_PASSWORD","fixture")
            .withExposedPorts(5432).withCreateContainerCmdModifier(c -> c.getHostConfig().withPortBindings(
                    new PortBinding(Ports.Binding.bindIpAndPort("127.0.0.1",0),new ExposedPort(5432))))
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n",2).withStartupTimeout(Duration.ofSeconds(60)));
    private static final String MODEL="SYNTHETIC_UNIT_VECTOR:1024:publication-v1";
    JdbcTemplate jdbc; PgSkillRouteIndexRepository index; JdbcSkillCatalogRepository catalog;
    JdbcSkillPackageRepository packages; JdbcSkillRoutePublicationRepository publication; SkillCatalogPort read;
    String project;

    @BeforeEach void setup() throws Exception {
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        jdbc = new JdbcTemplate(ds);
        Path root=Path.of("").toAbsolutePath();
        while(root!=null&&!Files.exists(root.resolve("db/migrations/sql/ops-skill-runtime-publication.sql"))) root=root.getParent();
        assertNotNull(root);
        try(var c=ds.getConnection()) {
            for(String name:List.of("ops06-related-skill-tables.sql","ops06-published-skill-tables.sql"))
                ScriptUtils.executeSqlScript(c,new FileSystemResource(root.getParent().resolve("scripts/fixtures/"+name)));
            ScriptUtils.executeSqlScript(c,new FileSystemResource(root.resolve("db/migrations/sql/ops-skill-runtime-publication.sql")));
            ScriptUtils.executeSqlScript(c,new FileSystemResource(root.resolve("db/migrations/sql/ops-skill-runtime-budget.sql")));
            ScriptUtils.executeSqlScript(c,new FileSystemResource(root.resolve("db/migrations/sql/ops-skill-file-projection.sql")));
            ScriptUtils.executeSqlScript(c,new FileSystemResource(root.resolve("db/migrations/sql/ops-skill-atomic-publication.sql")));
        }
        var pg = new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://"+PG.getHost()+":"+PG.getMappedPort(5432)+"/publication","fixture","fixture"));
        pg.execute("CREATE EXTENSION IF NOT EXISTS vector");index=new PgSkillRouteIndexRepository(pg);index.initialize();
        var beans=new DefaultListableBeanFactory();beans.registerSingleton("mysqlJdbcTemplate",jdbc);
        catalog=new JdbcSkillCatalogRepository(jdbc);packages=new JdbcSkillPackageRepository(beans.getBeanProvider(JdbcTemplate.class));
        publication=new JdbcSkillRoutePublicationRepository(jdbc,catalog,packages,index);
        read=new SkillCatalogReadService(catalog,packages,mock(SkillFileSourcePort.class),mock(SkillProjectValidationPort.class));
        project="p-"+UUID.randomUUID();
    }

    @Test void newHeadStaysInvisibleUntilBothDatabasesAreReadyAndFrozenBodyRemainsExact() {
        var old=save(1);index.stage(old,MODEL);
        assertFalse(publication.activate(old,MODEL));assertTrue(visible().isEmpty());
        index.ready(old,MODEL,vector());assertTrue(publication.activate(old,MODEL));
        var next=save(2);index.stage(next,MODEL);
        assertEquals(1,visible().get(0).version());
        var query=query((p,current)->publication.visible(p,current,MODEL));
        var selected=query.select(new SelectRuntimeSkillsQuery.Request(project,"agent",List.of("fixture"),"inspect logs",3));
        assertEquals(1,selected.selectedRefs().get(0).get("version"));
        var bodies=new SkillPackageQueryService(read,packages,mock(SkillProjectValidationPort.class));
        assertTrue(String.valueOf(bodies.getVersion(project,"fixture",1,old.skillHash(),old.packageHash(),"PROJECT").get("content")).contains("version 1"));
        var immutable=bodies.getVersion(project,"fixture",1,old.skillHash(),old.packageHash(),"PROJECT");
        assertEquals("fixture version 1",immutable.get("name"));
        assertEquals("inspect logs version 1",immutable.get("description"));
        assertEquals(old.packageHash(),immutable.get("currentPackageHash"));
        index.ready(next,MODEL,vector());
        assertEquals(1,visible().get(0).version()); // simulated crash between PG READY and pointer commit
        publication=new JdbcSkillRoutePublicationRepository(jdbc,catalog,packages,index);
        assertTrue(publication.activate(next,MODEL));assertEquals(2,visible().get(0).version());
        assertFalse(publication.activate(old,MODEL));assertEquals(2,visible().get(0).version());
        var frozen=query.selectFrozen(new SelectRuntimeSkillsQuery.FrozenRequest(project,selected.catalogRefs(),"inspect logs",3));
        assertEquals(1,frozen.selectedRefs().get(0).get("version"));
        assertTrue(String.valueOf(bodies.getVersion(project,"fixture",1,old.skillHash(),old.packageHash(),"PROJECT").get("content")).contains("version 1"));
    }

    @Test void historicalRollbackRetainsRoutingAndBecomesVisibleOnlyAfterItsNewIndexIsReady() {
        var original=save(1);ready(original);assertTrue(publication.activate(original,MODEL));
        var newer=save(2);ready(newer);assertTrue(publication.activate(newer,MODEL));
        var transaction=new org.springframework.transaction.support.TransactionTemplate(
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        SkillTransactionPort tx=new SkillTransactionPort() {
            public <T> T required(java.util.function.Supplier<T> work) {return transaction.execute(status->work.get());}
        };
        var rollback=new SkillRollbackUseCase(catalog,packages,tx,SkillPackageManifest.Limits.defaults());
        var result=rollback.rollbackProject(project,"fixture",1,"fixture-admin");
        var source=packages.findVersion(new SkillPackageKey("PROJECT",project,"fixture",1)).orElseThrow();
        assertEquals(source.artifactHashesJson(),result.publishedVersion().artifactHashesJson());
        assertEquals(SkillPackageManifest.routingProfile(source.manifestJson()),
                SkillPackageManifest.routingProfile(result.publishedVersion().manifestJson()));
        var restored=new SkillRuntimeCatalogAccess(read).active(project).get(0);
        assertEquals(3,restored.version());assertTrue(restored.routingReady());
        assertFalse(publication.activate(restored,MODEL));assertEquals(2,visible().get(0).version());
        ready(restored);assertTrue(publication.activate(restored,MODEL));assertEquals(3,visible().get(0).version());
        assertEquals(3,packages.findVersions("PROJECT",project,"fixture").size());
    }

    @Test void corruptOrIncompleteBodyCannotPublishEvenWithReadyVectorAndRetryRepairsIt() {
        var old=save(1);ready(old);assertTrue(publication.activate(old,MODEL));var next=save(2);ready(next);
        String body=packages.findVersion(new SkillPackageKey("PROJECT",project,"fixture",2)).orElseThrow().content();
        jdbc.update("UPDATE ai_ops_skill_artifact SET content='SYNTHETIC damaged body' WHERE project_id=? AND skill_id='fixture' AND version=2",project);
        assertThrows(IllegalStateException.class,()->publication.activate(next,MODEL));assertEquals(1,visible().get(0).version());
        jdbc.update("UPDATE ai_ops_skill_artifact SET content=? WHERE project_id=? AND skill_id='fixture' AND version=2",body,project);
        assertTrue(publication.activate(next,MODEL));assertEquals(2,visible().get(0).version());
    }

    @Test void concurrentActivationIsIdempotentAndLateWorkerCannotRegressPointer() throws Exception {
        var old=save(1);ready(old);assertTrue(publication.activate(old,MODEL));var next=save(2);ready(next);
        var pool=Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> work=new ArrayList<>();for(int i=0;i<8;i++)work.add(()->publication.activate(next,MODEL));
            for(var result:pool.invokeAll(work))assertTrue(result.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_runtime_publication WHERE project_id=?",Integer.class,project));
        assertFalse(publication.activate(old,MODEL));assertEquals(2,visible().get(0).version());
    }

    @Test void currentRevocationOverridesOldPublishedPointerAndMissingOrWrongModelNeverWidensScope() {
        var old=save(1);ready(old);assertTrue(publication.activate(old,MODEL));
        assertTrue(publication.visible(project,List.of(),MODEL).isEmpty());
        assertTrue(publication.visible(project,List.of(old),MODEL+"other").isEmpty());
        assertThrows(SecurityException.class,()->publication.visible("foreign",List.of(old),MODEL));
        jdbc.update("UPDATE ai_ops_skill SET status='DISABLED',execution_mode='DISABLED' WHERE project_id=? AND skill_id='fixture'",project);
        assertTrue(visible().isEmpty());assertFalse(publication.activate(old,MODEL));
        var query=query((p,c)->publication.visible(p,c,MODEL));
        assertTrue(query.select(new SelectRuntimeSkillsQuery.Request(project,"agent",List.of(),"inspect logs",3)).selectedRefs().isEmpty());
    }

    @Test void pointerChangeDuringRecallCannotFreezeAMixedVersion() {
        var old=save(1);ready(old);assertTrue(publication.activate(old,MODEL));var next=save(2);ready(next);
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var query=query((p,c)->{
            if(calls.incrementAndGet()==2) assertTrue(publication.activate(next,MODEL));
            return publication.visible(p,c,MODEL);
        });
        assertThrows(SecurityException.class,()->query.select(new SelectRuntimeSkillsQuery.Request(project,"agent",List.of("fixture"),"inspect logs",3)));
    }

    @Test void budgetSurvivesRestartAndConcurrentLoadsCannotExceedSixThousand() throws Exception {
        String run="run-"+UUID.randomUUID();
        var budget=new JdbcSkillRuntimeBudgetRepository(jdbc);
        var first=SkillRuntimeBudgetPort.Load.body("skill-one-v1", "a".repeat(4000));
        budget.reserve(project,run,List.of(first));
        new JdbcSkillRuntimeBudgetRepository(jdbc).reserve(project,run,List.of(first));
        var pool=Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> work=new ArrayList<>();
            for(int i=0;i<8;i++) { final int n=i; work.add(()-> {
                try { new JdbcSkillRuntimeBudgetRepository(jdbc).reserve(project,run,
                        List.of(SkillRuntimeBudgetPort.Load.body("skill-two-v1", String.valueOf(n).repeat(1500)))); return true; }
                catch(IllegalStateException e) { assertEquals("SKILL_RUNTIME_TOTAL_BODY_BUDGET_EXCEEDED",e.getMessage()); return false; }
            }); }
            int passed=0; for(var result:pool.invokeAll(work))if(result.get(20,TimeUnit.SECONDS))passed++;
            assertEquals(1,passed);
        } finally {pool.shutdownNow();}
        assertEquals(5500,jdbc.queryForObject("SELECT SUM(units) FROM ai_ops_skill_runtime_budget_item WHERE project_id=? AND run_id=?",Integer.class,project,run));
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_runtime_budget_item WHERE project_id=? AND run_id=?",Integer.class,project,run));
    }

    @Test void budgetCountsAllCanaryAndPublishedVersionsAndRollsBackOversizedBatch() {
        var budget=new JdbcSkillRuntimeBudgetRepository(jdbc); String run="budget-"+UUID.randomUUID();
        List<SkillRuntimeBudgetPort.Load> loads=new ArrayList<>();
        for(int i=0;i<4;i++)loads.add(SkillRuntimeBudgetPort.Load.body("distinct-version-"+i,"safe"));
        assertEquals("SKILL_RUNTIME_SKILL_COUNT_EXCEEDED",assertThrows(IllegalStateException.class,()->budget.reserve(project,run,loads)).getMessage());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_runtime_budget_item WHERE project_id=? AND run_id=?",Integer.class,project,run));
        budget.reserve(project,run,loads.subList(0,3));
        assertThrows(IllegalStateException.class,()->new JdbcSkillRuntimeBudgetRepository(jdbc).reserve(project,run,List.of(loads.get(3))));
        // Another project and Run has its own independent allowance, never a reset of this one.
        budget.reserve(project+"-other",run,loads.subList(0,3));
        assertEquals(12,jdbc.queryForObject("SELECT SUM(units) FROM ai_ops_skill_runtime_budget_item WHERE project_id=? AND run_id=?",Integer.class,project,run));
    }

    @Test void filesAcquireImmutableVersionsAndDisappearFromRuntimeImmediatelyOnRemoval() {
        var files=mock(SkillFileSourcePort.class); var importer=new JdbcSkillFileProjectionRepository(jdbc,catalog,packages);
        var first=file("file-fixture","first mandatory safety body");
        when(files.findAll()).thenReturn(List.of(first));
        var reads=new SkillCatalogReadService(catalog,packages,files,mock(SkillProjectValidationPort.class),importer);
        assertTrue(new SkillRuntimeCatalogAccess(reads).active(project).isEmpty());
        importer.synchronize(first); importer.synchronize(first);
        var v1=new SkillRuntimeCatalogAccess(reads).active(project).get(0);
        assertEquals(1,v1.version()); ready(v1);assertTrue(publication.activate(v1,MODEL));
        var second=file("file-fixture","second mandatory safety body");
        when(files.findAll()).thenReturn(List.of(second));importer.synchronize(second);
        var heads=new SkillRuntimeCatalogAccess(reads).active(project);
        assertEquals(2,heads.get(0).version());
        assertEquals(1,publication.visible(project,heads,MODEL).get(0).version());
        var bodies=new SkillPackageQueryService(reads,packages,mock(SkillProjectValidationPort.class));
        assertEquals(first.content(),bodies.getVersion(project,v1.skillId(),1,v1.skillHash(),v1.packageHash(),"PROJECT").get("content"));
        ready(heads.get(0));assertTrue(publication.activate(heads.get(0),MODEL));
        when(files.findAll()).thenReturn(List.of());
        assertTrue(new SkillRuntimeCatalogAccess(reads).active(project).isEmpty());
        assertEquals(2,packages.findVersions("PROJECT",project,"file-fixture").size());
        assertThrows(SkillRuntimeAccessRevokedException.class,()->new SkillCatalogQueryService(reads,bodies)
                .getRuntimeSkillVersion(project,v1.skillId(),1,v1.skillHash(),v1.packageHash(),"PROJECT"));
    }

    @Test void concurrentFileImportNeverOverwritesManualHeadOrGovernanceChange() throws Exception {
        var importer=new JdbcSkillFileProjectionRepository(jdbc,catalog,packages);var first=file("file-fixture","body");
        var pool=Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> work=new ArrayList<>();for(int i=0;i<8;i++)work.add(()->{importer.synchronize(first);return true;});
            for(var result:pool.invokeAll(work))assertTrue(result.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertEquals(1,packages.findVersions("PROJECT",project,"file-fixture").size());
        // Simulated admin governance change in the disposable test DB. The importer must not undo it.
        jdbc.update("UPDATE ai_ops_skill SET current_version=2,version=2,current_skill_hash='manual-change',status='DISABLED',execution_mode='DISABLED' WHERE project_id=? AND skill_id='file-fixture'",project);
        importer.synchronize(file("file-fixture","new file must not overwrite manual choice"));
        assertEquals("body",catalog.find("PROJECT",project,"file-fixture",true).orElseThrow().content());
        assertTrue(importer.managedIds("PROJECT",project).isEmpty());
        var manual=save(1);importer.synchronize(file("fixture","collision"));
        assertEquals(manual.skillHash(),catalog.find("PROJECT",project,"fixture",false).orElseThrow().currentSkillHash());
    }

    @Test void fileHeadAndImmutableBodyRollbackTogetherOnPersistenceFailure() {
        var failed=mock(cn.lgs.orbisops.domain.skill.adapter.repository.ISkillPackageRepository.class);
        doThrow(new IllegalStateException("SYNTHETIC storage outage")).when(failed).appendVersion(any(),anyList());
        var importer=new JdbcSkillFileProjectionRepository(jdbc,catalog,failed);
        assertThrows(IllegalStateException.class,()->importer.synchronize(file("file-fixture","body")));
        assertTrue(catalog.find("PROJECT",project,"file-fixture",false).isEmpty());
        assertTrue(importer.managedIds("PROJECT",project).isEmpty());
        new JdbcSkillFileProjectionRepository(jdbc,catalog,packages).synchronize(file("file-fixture","body"));
        assertEquals(1,packages.findVersions("PROJECT",project,"file-fixture").size());
    }
    private SkillFileDefinition file(String id,String body) {
        return new SkillFileDefinition(id,"/SYNTHETIC-TEST/"+id,Map.of("scope","PROJECT","projectId",project,"description","inspect logs",
                "whenToUse",List.of("inspect logs"),"whenNotToUse",List.of("delete production")),body,body,"");
    }

    private SelectRuntimeSkillsQuery query(SkillRuntimePublishedVersionPort versions) {
        return new SelectRuntimeSkillsQuery(read,(q,c)->index.search(project,c,MODEL,vector(),20),(q,c)->Map.of(),
                new SkillRuntimeSelectionSettings(20,3,3,0.9,0.99,0.42,20,0.12,0.65,true,20,0.35),versions);
    }
    private List<SkillRuntimeCandidate> visible() {return publication.visible(project,new SkillRuntimeCatalogAccess(read).active(project),MODEL);}
    private void ready(SkillRuntimeCandidate c) {index.stage(c,MODEL);index.ready(c,MODEL,vector());}
    private static float[] vector() {var v=new float[1024];v[0]=1;return v;}
    private SkillRuntimeCandidate save(int version) {return save("fixture",version);}
    private SkillRuntimeCandidate save(String id,int version) {
        String body="# SYNTHETIC fixture version "+version+"\nInspect logs with authorized read tools.\nNever modify production.\n";
        var descriptor=SkillPackageManifest.create("PROJECT",project,id,"fixture version "+version,"inspect logs version "+version,version,body,
                Map.of("whenToUse",List.of("inspect logs"),"whenNotToUse",List.of("delete production"),"keywords",List.of("logs")),SkillPackageManifest.Limits.defaults());
        String hash=CanonicalObjectHasher.sha256Text("SYNTHETIC:"+body);
        var key=new SkillPackageKey("PROJECT",project,id,version);
        var v=new SkillPackageVersion(0,key,hash,0,"","","","","SYNTHETIC",body,"MANUAL","","synthetic publication test",
                descriptor.packageHash(),descriptor.manifestJson(),CanonicalJson.stringify(descriptor.artifactHashes()),descriptor.entrypoint(),
                descriptor.artifactCount(),descriptor.packageSize(),Instant.now());
        var artifacts=descriptor.artifacts().values().stream().map(a->new SkillArtifact(a.path(),a.role(),a.mediaType(),a.encoding(),a.contentHash(),a.sizeBytes(),a.content())).toList();
        packages.appendVersion(v,artifacts);
        jdbc.update("""
            INSERT INTO ai_ops_skill (skill_id,project_id,skill_name,scope,description,content,version,current_version,current_skill_hash,
              skill_hash,current_package_hash,package_manifest_json,artifact_hashes_json) VALUES(?,?,'fixture','PROJECT','inspect logs',?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE content=VALUES(content),version=VALUES(version),current_version=VALUES(current_version),
              current_skill_hash=VALUES(current_skill_hash),skill_hash=VALUES(skill_hash),current_package_hash=VALUES(current_package_hash),
              package_manifest_json=VALUES(package_manifest_json),artifact_hashes_json=VALUES(artifact_hashes_json)
            """,id,project,body,version,version,hash,hash,descriptor.packageHash(),descriptor.manifestJson(),CanonicalJson.stringify(descriptor.artifactHashes()));
        jdbc.update("UPDATE ai_ops_skill SET skill_name=?,description=? WHERE project_id=? AND skill_id=?", "fixture version "+version, "inspect logs version "+version,project,id);
        return new SkillRuntimeCatalogAccess(read).active(project).stream().filter(c->id.equals(c.skillId())).findFirst().orElseThrow();
    }

    @Test void splitPublishesAllBranchesTogetherSurvivesRestartAndRollbackPreservesOriginalBinding() {
        var original=automatic(save("original",1));ready(original);assertTrue(publication.activate(original,MODEL));
        var left=save("left",1);var right=save("right",1);
        var plan=plan("SPLIT_SKILL",List.of(original),List.of(left,right));
        String id="SYNTHETIC-split-"+UUID.randomUUID();publication=atomic(plan);stage(id,plan,List.of(left,right));
        assertEquals(Set.of("original"),catalog.findAuthoringMetadata("PROJECT",project).stream().map(SkillCatalogEntry::skillId)
                .collect(java.util.stream.Collectors.toSet()),"Unpublished branches must not be authoring references");
        assertEquals(Set.of("original"),visibleIds());ready(left);
        assertFalse(publication.activate(left,MODEL));assertEquals(Set.of("original"),visibleIds());
        ready(right);publication=atomic(plan); // process restart after all PG generations but before MySQL publication
        assertTrue(publication.activate(right,MODEL));assertEquals(Set.of("left","right"),visibleIds());
        assertEquals(Set.of("left","right"),catalog.findAuthoringMetadata("PROJECT",project).stream().map(SkillCatalogEntry::skillId)
                .collect(java.util.stream.Collectors.toSet()),"Replaced methods must not enter a new maintenance proposal");
        assertEquals(Set.of("left","right","original"),publication.visible(project,new SkillRuntimeCatalogAccess(read).active(project),MODEL,Set.of("original"))
                .stream().map(SkillRuntimeCandidate::skillId).collect(java.util.stream.Collectors.toSet()));
        publication.rollback(project,id,"fixture-admin","SYNTHETIC verification rollback");
        assertEquals(Set.of("original"),catalog.findAuthoringMetadata("PROJECT",project).stream().map(SkillCatalogEntry::skillId)
                .collect(java.util.stream.Collectors.toSet()),"Rollback restores the source and excludes retained target archives");
        assertEquals(Set.of("original"),visibleIds());assertFalse(publication.activate(left,MODEL));
        assertTrue(packages.findVersion(new SkillPackageKey("PROJECT",project,"left",1)).isPresent());
        assertTrue(publication.lifecycleVisible(project,List.of(left),Set.of("left")).isEmpty());
        var runtimePolicy=new SkillRuntimePublishedVersionPort() {
            public List<SkillRuntimeCandidate> visible(String p,List<SkillRuntimeCandidate> current) {return publication.visible(p,current,MODEL);}
            public List<SkillRuntimeCandidate> usableFrozen(String p,List<SkillRuntimeCandidate> current) {
                return publication.lifecycleVisible(p,current,current.stream().map(SkillRuntimeCandidate::skillId).collect(java.util.stream.Collectors.toSet()));
            }
        };
        var bodies=new SkillPackageQueryService(read,packages,mock(SkillProjectValidationPort.class));
        var queries=new SkillCatalogQueryService(read,bodies,runtimePolicy);
        assertThrows(SkillRuntimeAccessRevokedException.class,()->queries.getRuntimeSkillVersion(project,"left",1,left.skillHash(),left.packageHash(),"PROJECT"));
        assertNotNull(queries.getSkillVersion(project,"left",1,left.skillHash(),left.packageHash(),"PROJECT")); // audit remains readable
        assertNotNull(queries.getRuntimeSkillVersion(project,"original",1,original.skillHash(),original.packageHash(),"PROJECT"));
        publication.rollback(project,id,"fixture-admin","idempotent repeated request");
    }

    @Test void mergeAndConcurrentProjectionSwitchOnlyOneCompleteMapping() throws Exception {
        var a=automatic(save("source-a",1));var b=automatic(save("source-b",1));
        ready(a);ready(b);assertTrue(publication.activate(a,MODEL));assertTrue(publication.activate(b,MODEL));
        var merged=save("merged",1);ready(merged);
        var plan=plan("MERGE_SKILLS",List.of(a,b),List.of(merged));String id="SYNTHETIC-merge-"+UUID.randomUUID();
        publication=atomic(plan);stage(id,plan,List.of(merged));
        var pool=Executors.newFixedThreadPool(6);
        try {
            List<Callable<Boolean>> tasks=new ArrayList<>();for(int i=0;i<6;i++)tasks.add(()->publication.activate(merged,MODEL));
            for(var result:pool.invokeAll(tasks)) assertTrue(result.get(30,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertEquals(Set.of("merged"),visibleIds());
        assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_atomic_replacement WHERE project_id=?",Integer.class,project));
        publication.rollback(project,id,"fixture-admin","SYNTHETIC merge rollback");assertEquals(Set.of("source-a","source-b"),visibleIds());
    }

    @Test void oneBadBodyOrRevokedSourceCannotExposePartialReplacement() {
        var original=automatic(save("source",1));ready(original);assertTrue(publication.activate(original,MODEL));
        var left=save("first",1);var right=save("second",1);ready(left);ready(right);
        var plan=plan("SPLIT_SKILL",List.of(original),List.of(left,right));var id="SYNTHETIC-bad-"+UUID.randomUUID();
        publication=atomic(plan);stage(id,plan,List.of(left,right));
        String body=packages.findVersion(new SkillPackageKey("PROJECT",project,"second",1)).orElseThrow().content();
        jdbc.update("UPDATE ai_ops_skill_artifact SET content='SYNTHETIC corrupted' WHERE project_id=? AND skill_id='second'",project);
        assertThrows(IllegalStateException.class,()->publication.activate(left,MODEL));assertEquals(Set.of("source"),visibleIds());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_atomic_replacement WHERE project_id=?",Integer.class,project));
        jdbc.update("UPDATE ai_ops_skill_artifact SET content=? WHERE project_id=? AND skill_id='second'",body,project);
        jdbc.update("UPDATE ai_ops_skill SET mutation_mode='MANUAL_ONLY' WHERE project_id=? AND skill_id='source'",project);
        assertThrows(IllegalStateException.class,()->publication.activate(right,MODEL));assertEquals(Set.of("source"),visibleIds());
    }

    @Test void failedStagingRollsBackAllReceiptsAndLaterEditsPreventDestructiveGroupRollback() {
        var old=automatic(save("source",1));ready(old);assertTrue(publication.activate(old,MODEL));
        var left=save("first",1);var right=save("second",1);ready(left);ready(right);
        var plan=plan("SPLIT_SKILL",List.of(old),List.of(left,right));String id="SYNTHETIC-tx-"+UUID.randomUUID();publication=atomic(plan);
        var tx=new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        assertThrows(IllegalStateException.class,()->tx.execute(s->{publication.stage(id,plan,outcomes(List.of(left,right)));throw new IllegalStateException("SYNTHETIC crash");}));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_atomic_publication WHERE candidate_id=?",Integer.class,id));
        stage(id,plan,List.of(left,right));assertTrue(publication.activate(left,MODEL));
        save("first",2);
        assertThrows(IllegalStateException.class,()->publication.rollback(project,id,"fixture-admin","must not overwrite later change"));
        assertTrue(publication.active(id));assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM ai_ops_skill_atomic_replacement WHERE project_id=?",Integer.class,project));
        assertThrows(SecurityException.class,()->publication.rollback("foreign",id,"fixture-admin","wrong project"));
    }

    private SkillRuntimeCandidate automatic(SkillRuntimeCandidate c) {
        jdbc.update("UPDATE ai_ops_skill SET mutation_mode='AUTO' WHERE project_id=? AND skill_id=?",project,c.skillId());return c;
    }
    private SkillAtomicPublicationPlan plan(String operation,List<SkillRuntimeCandidate> sources,List<SkillRuntimeCandidate> targets) {
        return new SkillAtomicPublicationPlan(operation,project,sources.stream().map(c->new SkillAtomicPublicationPlan.Source(c.skillId(),c.version(),c.skillHash(),c.packageHash(),
                SkillEvolutionRelatedSkillPolicy.databaseFence(catalog.find("PROJECT",project,c.skillId(),false).orElseThrow()))).toList(),
                targets.stream().map(c->new SkillAtomicPublicationPlan.Target(c.skillId(),c.skillId(),c.name(),Set.of("SYNTHETIC-a","SYNTHETIC-b","SYNTHETIC-c"),List.of(),List.of())).toList());
    }
    private JdbcSkillRoutePublicationRepository atomic(SkillAtomicPublicationPlan plan) {
        // These tests exercise real storage/PG readiness, not authoring or accepted-task qualification.
        return new JdbcSkillRoutePublicationRepository(jdbc,catalog,packages,index,id->plan);
    }
    private void stage(String id,SkillAtomicPublicationPlan plan,List<SkillRuntimeCandidate> targets) {
        new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()))
                .executeWithoutResult(s->publication.stage(id,plan,outcomes(targets)));
    }
    private List<SkillPublicationOutcome> outcomes(List<SkillRuntimeCandidate> targets) {
        return targets.stream().map(c->new SkillPublicationOutcome(true,"SYNTHETIC",c.skillId(),c.version(),c.skillHash())).toList();
    }
    private Set<String> visibleIds() {return visible().stream().map(SkillRuntimeCandidate::skillId).collect(java.util.stream.Collectors.toSet());}
}
