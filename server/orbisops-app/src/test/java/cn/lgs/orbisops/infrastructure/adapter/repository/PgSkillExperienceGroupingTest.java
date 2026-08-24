package cn.lgs.orbisops.infrastructure.adapter.repository;
import cn.lgs.orbisops.domain.skill.model.SkillMethodMemory.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

/** Real pgvector, deliberately synthetic vectors; never counted as model-quality evaluation. */
@Testcontainers(disabledWithoutDocker=true)
class PgSkillExperienceGroupingTest {
    @Container static final GenericContainer<?> PG=new GenericContainer<>("pgvector/pgvector:pg16")
            .withEnv("POSTGRES_DB","group_test").withEnv("POSTGRES_USER","fixture").withEnv("POSTGRES_PASSWORD","fixture")
            .withExposedPorts(5432).withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withPortBindings(
                    new com.github.dockerjava.api.model.PortBinding(com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",0),new com.github.dockerjava.api.model.ExposedPort(5432))))
            .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n",2).withStartupTimeout(Duration.ofSeconds(60)));
    PgSkillExperienceGroupingIndex index;String project;
    @BeforeEach void setup() {
        var jdbc=new JdbcTemplate(new DriverManagerDataSource("jdbc:postgresql://"+PG.getHost()+":"+PG.getMappedPort(5432)+"/group_test","fixture","fixture"));
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");index=new PgSkillExperienceGroupingIndex(jdbc);index.initialize();project="p-"+UUID.randomUUID();
    }
    Group group(String id,String scope,long version,String goal) {
        var method=new Method(goal,List.of("只读，足够样本"),List.of("检查指标和原始回执"),List.of("交叉核对采样与错误率"),List.of("指标采集"));
        return new Group(id,scope,version,"hash-"+version,method,List.of());
    }
    float[] vector(int dimension) {var v=new float[1024];v[dimension]=1;return v;}
    @Test void realHybridSearchIsProjectAndModelBoundCappedAndVersionMonotonic() {
        for(int i=0;i<16;i++) index.put(group("eg-"+i,project,1,"只读巡检错误率窗口"),"test-model",vector(i));
        index.put(group("foreign","foreign-project",1,"只读巡检错误率窗口"),"test-model",vector(0));
        var refs=index.search(project,"检查巡检错误率","test-model",vector(0));
        assertEquals(5,refs.size());assertTrue(refs.stream().allMatch(r->r.groupId().startsWith("eg-")));assertEquals("eg-0",refs.get(0).groupId());
        assertTrue(index.search(project,"只读巡检","different-model",vector(0)).isEmpty());
        var newest=group("eg-0",project,2,"新方法");index.put(newest,"test-model",vector(0));
        index.put(group("eg-0",project,1,"旧方法"),"test-model",vector(1));
        assertTrue(index.contains(newest,"test-model"));assertFalse(index.contains(group("eg-0",project,1,"旧方法"),"test-model"));
    }
    @Test void factVectorsAreImmutableAndBoundToSourceContentModelAndProject() {
        var method=group("g",project,1,"巡检").method();var fact=new Fact("source","source-hash","episode",1,method);
        index.cacheFactEmbedding(project,fact,"model",vector(0));
        index.cacheFactEmbedding(project,fact,"model",vector(1));
        assertArrayEquals(vector(0),index.factEmbedding(project,fact,"model").orElseThrow());
        assertTrue(index.factEmbedding(project,fact,"other-model").isEmpty());
        assertTrue(index.factEmbedding("other-project",fact,"model").isEmpty());
        assertTrue(index.factEmbedding(project,new Fact("source","changed-source","episode",2,method),"model").isEmpty());
        assertTrue(index.factEmbedding(project,new Fact("source","source-hash","episode",1,group("g",project,1,"修复").method()),"model").isEmpty());
    }
    @Test void unavailableOrMalformedVectorsCannotPretendToHaveNoSimilarGroup() {
        var absent=new PgSkillExperienceGroupingIndex((JdbcTemplate)null);
        assertThrows(IllegalStateException.class,()->absent.search(project,"巡检","m",vector(0)));
        assertThrows(IllegalArgumentException.class,()->index.search(project,"巡检","m",new float[1024]));
        assertThrows(IllegalArgumentException.class,()->index.search(project,"巡检","m",new float[512]));
    }
}
