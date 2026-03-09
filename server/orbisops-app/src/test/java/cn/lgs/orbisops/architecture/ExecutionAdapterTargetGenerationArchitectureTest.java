package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionAdapterTargetGenerationArchitectureTest {

    private static final String DOMAIN_ROOT = "orbisops-domain/src/main/java/"
            + "cn/lgs/orbisops/domain/execution/";
    private static final String APPLICATION_ROOT = "orbisops-application/src/main/java/"
            + "cn/lgs/orbisops/application/execution/";
    private static final String TRIGGER_ROOT = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/application/execution/";
    private static final String LEGACY_SERVICE = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/ops/change/OpsExecutionAdapterTemplateService.java";

    @Test
    void domainOwnsGenerationInputSpecificationAndMergePolicy() throws IOException {
        String input = read(DOMAIN_ROOT + "model/ExecutionTargetGenerationInput.java");
        String specification = read(DOMAIN_ROOT + "model/ExecutionTargetSpecification.java");
        String policy = read(DOMAIN_ROOT + "service/ExecutionTargetGenerationPolicy.java");

        assertAll(
                () -> assertTrue(input.contains("record ExecutionTargetGenerationInput")),
                () -> assertTrue(input.contains("EXECUTION_ENVIRONMENTS_REQUIRED")),
                () -> assertTrue(specification.contains("record ExecutionTargetSpecification")),
                () -> assertTrue(policy.contains("configuration.putAll(input.configuration())")),
                () -> assertTrue(policy.contains("configuration.putAll(input.resolvedConfig())")),
                () -> assertTrue(policy.contains("template.riskLevel() != ExecutionRiskLevel.LOW")),
                () -> assertFalse(input.contains("org.springframework")),
                () -> assertFalse(policy.contains("OpsExecutionResourceRequestDTO")),
                () -> assertFalse(policy.contains("fastjson")));
    }

    @Test
    void applicationCoordinatesTypedProvisioningAndTriggerOnlyMapsProtocols() throws IOException {
        String application = read(APPLICATION_ROOT
                + "ExecutionAdapterTargetGenerationApplicationService.java");
        String port = read(APPLICATION_ROOT + "ExecutionTargetProvisioningPort.java");
        String mapper = read(TRIGGER_ROOT + "OpsExecutionTargetGenerationMapper.java");
        String provisioning = read(TRIGGER_ROOT
                + "OpsExecutionTargetProvisioningAdapter.java");
        String adapter = read(TRIGGER_ROOT + "OpsExecutionAdapterTemplateAdapter.java");

        assertAll(
                () -> assertTrue(application.contains("IExecutionAdapterTemplateRepository")),
                () -> assertTrue(application.contains("ExecutionTargetGenerationPolicy")),
                () -> assertTrue(application.contains("provisioningPort.upsert(specification)")),
                () -> assertTrue(port.contains("ExecutionTargetSpecification")),
                () -> assertFalse(application.contains("org.springframework")),
                () -> assertFalse(application.contains("OpsExecutionResourceDTO")),
                () -> assertTrue(mapper.contains("ExecutionTargetGenerationInput")),
                () -> assertTrue(mapper.contains("JSON.parseObject")),
                () -> assertTrue(provisioning.contains("new ExecutionResourceDraft(")),
                () -> assertTrue(provisioning.contains("commands.upsert(draft, SYSTEM_ACTOR)")),
                () -> assertTrue(adapter.contains("targetGenerationMapper.input")),
                () -> assertFalse(Files.exists(projectRoot().resolve(LEGACY_SERVICE))));
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
