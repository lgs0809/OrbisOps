package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.worksession.WorkSessionMetadataKeys;
import cn.lgs.orbisops.infrastructure.dao.IAdminUserDao;
import cn.lgs.orbisops.trigger.ops.toolset.OpsExternalLocalProviderSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "orbisops.elasticsearch-index=order-service-log-*")
class OpsRuntimeToolContributorSpringWiringTest {

    @MockitoBean
    private IAdminUserDao adminUserDao;

    @MockitoBean(name = "mysqlTransactionManager")
    private PlatformTransactionManager mysqlTransactionManager;

    @MockitoBean(name = "mysqlJdbcTemplate")
    private JdbcTemplate mysqlJdbcTemplate;

    @MockitoBean
    private cn.lgs.orbisops.application.skill.SkillRuntimeBudgetPort skillRuntimeBudget;

    @MockitoBean
    private cn.lgs.orbisops.application.skill.SkillFileProjectionPort skillFileProjection;

    @MockitoBean
    private cn.lgs.orbisops.infrastructure.adapter.repository.JdbcSkillRoutePublicationRepository skillRoutePublication;

    @Autowired
    private OpsRuntimeToolContributorRegistry registry;

    @Autowired
    private OpsExternalLocalProviderSettings providerSettings;

    @Autowired
    private OpsRuntimeBuiltInToolContributor builtInToolContributor;

    @Test
    void genericReactDatasourceContributorMustBeWiredIntoProductionRegistry() {
        assertThat(registry.orderedContributorIds())
                .contains("datasource")
                .containsSubsequence("datasource", "repair");
        assertThat(providerSettings.allowsAdapter("LOCAL_PROMETHEUS")).isTrue();
        assertThat(providerSettings.allowsAdapter("LOCAL_ELASTICSEARCH")).isTrue();
    }

    @Test
    void normalChatRequestMustExposeDatasourceCallbacksWithoutAnalysisDtoMetadata() {
        OpsRuntimeResourceContext context = OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("generic-ops-react-agent").build())
                .agentScope(OpsAgentScopeConfig.builder().agentId("demo-ops-agent").build())
                .request(OpsAgentChatRequest.builder()
                        .userId("tester")
                        .runId("chat-run-datasource-wiring")
                        .projectId("demo-project")
                        .query("check metrics")
                        .build())
                .projectId("demo-project")
                .repairEnabled(false)
                .events(new ArrayList<>())
                .build();

        builtInToolContributor.contribute(context);

        assertThat(context.getTools())
                .extracting(callback -> callback.getToolDefinition().name())
                .contains("prometheus_query", "elasticsearch_search");
        assertThat(context.getEvents())
                .allSatisfy(event -> assertThat(event.getSummary())
                        .doesNotContain("analysisRequest"));
    }

    @Test
    void registryMustExposeDatasourceCallbacksForFreshAgentScope() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(WorkSessionMetadataKeys.OPS_ANALYSIS_REQUEST, Map.of(
                "runId", "run-datasource-wiring",
                "requestedBy", "tester",
                "projectId", "demo-project",
                "query", "check metrics",
                "subAgentMaxIterations", 2,
                "nodeTimeoutSeconds", 300));
        OpsRuntimeResourceContext context = OpsRuntimeResourceContext.builder()
                .definition(OpsAgentDefinition.builder().agentId("generic-ops-react-agent").build())
                .agentScope(OpsAgentScopeConfig.builder().agentId("demo-ops-agent").build())
                .request(OpsAgentChatRequest.builder()
                        .userId("tester")
                        .runId("run-datasource-wiring")
                        .projectId("demo-project")
                        .metadata(metadata)
                        .build())
                .projectId("demo-project")
                .repairEnabled(false)
                .events(new ArrayList<>())
                .build();

        builtInToolContributor.contribute(context);

        assertThat(context.getTools())
                .extracting(callback -> callback.getToolDefinition().name())
                .contains("prometheus_query", "elasticsearch_search");
        assertThat(context.getEvents())
                .extracting(OpsRuntimeEvent::getEventType)
                .contains("DATASOURCE_TOOLS_EXPOSED");
    }
}
