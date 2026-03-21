package cn.lgs.orbisops.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(locations = OrbisOpsArchitectureLocationProvider.class, importOptions = ImportOption.DoNotIncludeTests.class)
class OpsLayerArchitectureTest {

    @ArchTest
    static final ArchRule APPLICATION_MUST_NOT_DEPEND_ON_ADAPTERS = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.trigger..",
                    "cn.lgs.orbisops.infrastructure..")
            .because("the application layer owns use cases and ports, not inbound or outbound adapters");

    @ArchTest
    static final ArchRule APPLICATION_MUST_REMAIN_FRAMEWORK_NEUTRAL = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.servlet..",
                    "io.modelcontextprotocol..")
            .because("application orchestration must be testable without Spring, HTTP, or MCP runtimes");

    @ArchTest
    static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_OUTER_LAYERS = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "cn.lgs.orbisops.trigger..",
                    "cn.lgs.orbisops.infrastructure..")
            .because("domain rules must not depend on delivery mechanisms or persistence implementations");

    @ArchTest
    static final ArchRule DOMAIN_MUST_REMAIN_RUNTIME_NEUTRAL = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..")
            .because("domain rules must not depend on runtime containers or persistence APIs; general-purpose utility libraries are allowed");

    @ArchTest
    static final ArchRule MCP_DOMAIN_MUST_REMAIN_RUNTIME_NEUTRAL = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.domain.mcp..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..")
            .because("MCP domain rules must remain independent of runtime containers and persistence APIs");

    @ArchTest
    static final ArchRule TRIGGER_APPLICATION_SERVICES_MUST_NOT_USE_JDBC = noClasses()
            .that().resideInAPackage("cn.lgs.orbisops.trigger.application..")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
            .because("new use-case orchestration must use repositories instead of embedding SQL");

    @ArchTest
    static final ArchRule CHANNEL_OUTBOX_ORCHESTRATION_MUST_USE_REPOSITORY_PORT = noClasses()
            .that().haveSimpleName("OpsChannelNotificationService")
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.jdbc.core.JdbcTemplate")
            .because("Channel retry and dead-letter policy must use a repository port instead of embedded SQL");
}
