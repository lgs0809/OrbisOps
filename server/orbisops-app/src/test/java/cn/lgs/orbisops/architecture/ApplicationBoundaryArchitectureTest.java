package cn.lgs.orbisops.architecture;

import cn.lgs.orbisops.application.mcp.McpDiscoveryPort;
import cn.lgs.orbisops.application.mcp.McpPolicyCommandPort;
import cn.lgs.orbisops.application.mcp.McpRuntimeOperationsPort;
import cn.lgs.orbisops.application.mcp.McpSummaryPort;
import cn.lgs.orbisops.application.mcp.SelectRuntimeMcpToolsQuery;
import cn.lgs.orbisops.trigger.ops.runtime.OpsWorkSessionLifecycleCoordinator;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(locations = OrbisOpsArchitectureLocationProvider.class, importOptions = ImportOption.DoNotIncludeTests.class)
class ApplicationBoundaryArchitectureTest {

    @ArchTest
    static final ArchRule controllers_must_not_depend_on_legacy_facades = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.trigger.http..")
            .should().dependOnClassesThat().belongToAnyOf(OpsWorkSessionLifecycleCoordinator.class);

    @ArchTest
    static final ArchRule application_core_must_not_depend_on_trigger_or_infrastructure = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.trigger..",
                    "cn.lgs.orbisops.infrastructure..");

    @ArchTest
    static final ArchRule application_core_must_not_depend_on_frameworks = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.annotation..");

    @ArchTest
    static final ArchRule application_services_must_not_depend_on_runtime_or_legacy_facades = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.trigger.application..")
            .and().haveSimpleNameEndingWith("Service")
            .should().dependOnClassesThat().belongToAnyOf(OpsWorkSessionLifecycleCoordinator.class);

    @ArchTest
    static final ArchRule runtime_mcp_query_must_not_proxy_through_legacy_port = noClasses()
            .that().haveFullyQualifiedName(SelectRuntimeMcpToolsQuery.class.getName())
            .should().dependOnClassesThat().belongToAnyOf(
                    McpSummaryPort.class,
                    McpDiscoveryPort.class,
                    McpRuntimeOperationsPort.class,
                    McpPolicyCommandPort.class);

    @ArchTest
    static final ArchRule channel_trigger_services_must_not_own_schema_initialization = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.trigger.ops.channel..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..",
                    "jakarta.annotation..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_outer_layers = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.application..",
                    "cn.lgs.orbisops.trigger..",
                    "cn.lgs.orbisops.infrastructure..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_frameworks = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.annotation..",
                    "org.apache.tika..",
                    "org.apache.pdfbox..",
                    "org.apache.poi..");

    @ArchTest
    static final ArchRule domain_must_not_depend_on_external_skill_sdk = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springaicommunity.agent.tools..",
                    "org.springaicommunity.agent.utils..");

    @ArchTest
    static final ArchRule rag_neutral_boundaries_must_not_expose_framework_types = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.application.rag..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.ai..",
                    "org.springframework.web.multipart..");

    @ArchTest
    static final ArchRule rag_ingestion_web_adapter_must_not_reclaim_job_orchestration = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.application.rag.RagIngestionJobService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "java.util.concurrent..",
                    "org.springframework.beans.factory.annotation..");

    @ArchTest
    static final ArchRule knowledge_catalog_multipart_adapter_must_remain_thin = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.application.knowledge.OpsKnowledgeCatalogAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.knowledge.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure..",
                    "org.springframework.jdbc..",
                    "org.springframework.beans.factory.annotation..",
                    "jakarta.annotation..",
                    "com.alibaba.fastjson..",
                    "java.util.concurrent..");

    @ArchTest
    static final ArchRule project_workspace_service_must_not_access_project_repositories = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.ops.OpsProjectWorkspaceService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.project.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure.adapter.repository..");

    @ArchTest
    static final ArchRule project_workspace_query_adapter_must_not_reclaim_project_persistence = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.application.project.OpsProjectWorkspaceQueryAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.project.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure..",
                    "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule project_workspace_projection_adapter_must_not_reclaim_project_persistence = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.application.project.OpsProjectWorkspaceProjectionAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.project.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure..",
                    "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule project_access_adapter_must_remain_an_acl = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.application.project.OpsProjectAccessAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.project.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure..",
                    "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule project_resource_preparation_adapter_must_remain_an_acl = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.application.project.OpsProjectResourcePreparationAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.project.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure..",
                    "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule project_mcp_generation_adapter_must_remain_an_acl = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.application.project.OpsProjectMcpGenerationPreparationAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.project.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure..",
                    "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule project_mcp_template_generation_adapter_must_remain_an_acl = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.application.project.OpsProjectMcpTemplateGenerationPreparationAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.project.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure..",
                    "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule project_external_mcp_credential_adapter_must_remain_an_acl = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.application.project.OpsProjectExternalMcpCredentialReferenceAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.project.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure..",
                    "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule project_mcp_update_adapter_must_remain_an_acl = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.application.project.OpsProjectMcpUpdatePreparationAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.project.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure..",
                    "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule project_mcp_runtime_config_service_must_remain_an_acl = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.ops.runtime.OpsProjectMcpRuntimeConfigService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.project.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure..",
                    "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule project_workspace_service_must_not_access_knowledge_authorization_repositories = noClasses()
            .that().haveFullyQualifiedName(
                    "cn.lgs.orbisops.trigger.ops.OpsProjectWorkspaceService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.domain.knowledge.adapter.repository..",
                    "cn.lgs.orbisops.infrastructure.adapter.repository..");
}
