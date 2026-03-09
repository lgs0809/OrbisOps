package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutionArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java/cn/lgs/orbisops/domain/toolexecution/";
    private static final String APPLICATION = "orbisops-application/src/main/java/cn/lgs/orbisops/application/toolexecution/";
    private static final String TRIGGER = "orbisops-trigger/src/main/java/cn/lgs/orbisops/trigger/";
    private static final String SERVICE = TRIGGER + "ops/toolset/OpsToolExecutionService.java";
    private static final String RECORD_ADAPTER = TRIGGER + "application/toolexecution/OpsToolExecutionRecordAdapter.java";

    @Test
    void domainAndApplicationMustRemainFrameworkFreeAndTyped() throws IOException {
        String domain = readTree(DOMAIN);
        String application = readTree(APPLICATION);
        String combined = domain + application;
        assertAll(
                () -> assertTrue(domain.contains("class ToolExecutionPolicy")),
                () -> assertTrue(application.contains("class ToolExecutionApplicationService")),
                () -> assertTrue(application.contains("ToolExecutionDispatchPort")),
                () -> assertFalse(combined.contains("org.springframework")),
                () -> assertFalse(combined.contains("cn.lgs.orbisops.trigger")),
                () -> assertFalse(combined.contains("cn.lgs.orbisops.api.dto")),
                () -> assertFalse(combined.contains("com.alibaba.fastjson")),
                () -> assertFalse(combined.contains("landingRuntimeToken")),
                () -> assertFalse(combined.contains("LANDING_RUNTIME_TOKEN")));
    }

    @Test
    void publicEntriesMustDelegateWhileCompositeOwnsTypedDispatch() throws IOException {
        String service = read(SERVICE);
        String trigger = readTree(TRIGGER);
        String composite = read(TRIGGER
                + "application/toolexecution/dispatch/OpsCompositeToolExecutionDispatchAdapter.java");
        String local = read(TRIGGER
                + "application/toolexecution/dispatch/OpsLocalToolExecutionDispatchHandler.java");
        assertAll(
                () -> assertTrue(service.contains("toolMapper.view(toolExecution.execute(")),
                () -> assertFalse(service.contains("dispatchLegacy(")),
                () -> assertTrue(composite.contains("implements ToolExecutionDispatchPort")),
                () -> assertTrue(local.contains("target.adapterType().startsWith(\"LOCAL_\")")),
                () -> assertFalse(Files.exists(projectRoot().resolve(TRIGGER
                        + "application/toolexecution/dispatch/OpsLegacyToolExecutionDispatchHandler.java"))),
                () -> assertFalse(service.contains("implements ToolExecutionDispatchPort")),
                () -> assertFalse(service.contains("executeInternal(")),
                () -> assertFalse(service.contains("dispatchCode(")),
                () -> assertFalse(service.contains("dispatchChangePackage(")),
                () -> assertFalse(service.contains("dispatchToolResult(")),
                () -> assertFalse(service.contains("dispatchInspectionTask(")),
                () -> assertFalse(service.contains("scheduleRequest(")),
                () -> assertFalse(service.contains("dispatchAlertTrigger(")),
                () -> assertFalse(service.contains("alertRuleRequest(")),
                () -> assertFalse(service.contains("assertAlertRuleProject(")),
                () -> assertFalse(service.contains("dispatchChannel(")),
                () -> assertFalse(service.contains("ChannelModels.Send")),
                () -> assertFalse(service.contains("dispatchSkillCatalog(")),
                () -> assertFalse(service.contains("SelectRuntimeSkillsQuery.FrozenRequest")),
                () -> assertFalse(service.contains("mapList(")),
                () -> assertFalse(service.contains("LOCAL_")),
                () -> assertFalse(service.contains("localOpsAdapterService")),
                () -> assertFalse(service.contains("checkpointToolExecution(Map<String, Object> request")),
                () -> assertEquals(1, occurrences(trigger, "implements ToolExecutionDispatchPort")));
    }

    @Test
    void generalRecordPathMustUseTypedEvidenceApplications() throws IOException {
        String adapter = read(RECORD_ADAPTER);
        String service = read(SERVICE);
        String publicEntryPrefix = service.substring(
                service.indexOf("public Map<String, Object> execute("),
                service.indexOf("public Map<String, Object> executeMcp("));
        assertAll(
                () -> assertTrue(adapter.contains("ToolResultApplicationService")),
                () -> assertTrue(adapter.contains("EvidenceApplicationService")),
                () -> assertFalse(adapter.contains("OpsToolResultStore")),
                () -> assertFalse(adapter.contains("OpsEvidenceStore")),
                () -> assertFalse(publicEntryPrefix.contains("catalogService.listEffectiveToolsets")),
                () -> assertFalse(publicEntryPrefix.contains("router.decide")),
                () -> assertFalse(publicEntryPrefix.contains("toolResultStore.record")),
                () -> assertFalse(publicEntryPrefix.contains("evidenceStore.record")),
                () -> assertFalse(publicEntryPrefix.contains("auditService.record")));
    }

    @Test
    void facadeMustOnlyOwnTypedApplicationsAndBoundaryMappers() throws IOException {
        String service = read(SERVICE);
        assertAll(
                () -> assertTrue(service.contains("private final ToolExecutionApplicationService toolExecution;")),
                () -> assertTrue(service.contains("private final McpExecutionApplicationService mcpExecution;")),
                () -> assertTrue(service.contains("private final OpsToolExecutionMapper toolMapper;")),
                () -> assertTrue(service.contains("private final OpsMcpExecutionMapper mcpMapper;")),
                () -> assertFalse(service.contains("ObjectProvider<")),
                () -> assertFalse(service.contains("@Autowired")),
                () -> assertFalse(service.contains("requireDependency(")),
                () -> assertFalse(service.contains("recordEnvelope(")),
                () -> assertFalse(service.contains("executeMcpInternal(")));
    }

    private int occurrences(String source, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = source.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private String readTree(String relativeRoot) throws IOException {
        try (var stream = Files.walk(projectRoot().resolve(relativeRoot))) {
            StringBuilder source = new StringBuilder();
            for (Path file : stream.filter(path -> path.toString().endsWith(".java")).toList()) {
                source.append(Files.readString(file)).append('\n');
            }
            return source.toString();
        }
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
