package cn.lgs.orbisops.domain.execution;

import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionRiskLevel;
import cn.lgs.orbisops.domain.execution.model.ExecutionTargetGenerationInput;
import cn.lgs.orbisops.domain.execution.model.ExecutionTargetSpecification;
import cn.lgs.orbisops.domain.execution.service.ExecutionTargetGenerationPolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExecutionTargetGenerationPolicyTest {

    @Test
    void mergesConfigByPrecedenceAndDefaultsApprovalFromRisk() {
        ExecutionAdapterTemplate template = template(ExecutionRiskLevel.CRITICAL);
        ExecutionTargetGenerationInput input = new ExecutionTargetGenerationInput(
                "payment",
                "mysql-template",
                "payment-mysql",
                "Payment MySQL",
                "worker-1",
                List.of("PROD", "prod"),
                Map.of("shared", "request", "requestOnly", true),
                Map.of("shared", "resolved", "resolvedOnly", true),
                List.of("MYSQL_CREATE_INDEX", "MYSQL_CREATE_INDEX"),
                null,
                ExecutionResourceStatus.ENABLED);

        ExecutionTargetSpecification result =
                new ExecutionTargetGenerationPolicy().generate(template, input);

        assertEquals("resolved", result.configuration().get("shared"));
        assertEquals("default", result.configuration().get("defaultOnly"));
        assertEquals(true, result.configuration().get("requestOnly"));
        assertEquals(true, result.configuration().get("resolvedOnly"));
        assertEquals(List.of("MYSQL_CREATE_INDEX"),
                result.configuration().get("allowedActions"));
        assertEquals("mysql-template",
                result.configuration().get("adapterTemplateId"));
        assertEquals("CRITICAL",
                result.configuration().get("templateRiskLevel"));
        assertEquals(true, result.configuration().get("approvalRequired"));
        assertEquals(List.of("prod"), result.environments());
    }

    @Test
    void explicitApprovalOverridesLowRiskDefault() {
        ExecutionAdapterTemplate template = template(ExecutionRiskLevel.LOW);
        ExecutionTargetGenerationInput input = new ExecutionTargetGenerationInput(
                "payment",
                "mysql-template",
                "payment-mysql",
                "",
                "worker-1",
                List.of("prod"),
                Map.of(),
                Map.of(),
                List.of(),
                true,
                ExecutionResourceStatus.DISABLED);

        ExecutionTargetSpecification result =
                new ExecutionTargetGenerationPolicy().generate(template, input);

        assertEquals(true, result.configuration().get("approvalRequired"));
        assertEquals("payment-mysql", result.targetName());
        assertEquals(ExecutionResourceStatus.DISABLED, result.status());
    }

    private ExecutionAdapterTemplate template(ExecutionRiskLevel risk) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        return new ExecutionAdapterTemplate(
                1L,
                "mysql-template",
                "MySQL Template",
                ExecutionAdapterType.MYSQL_CONTROLLED,
                List.of("MYSQL_CREATE_INDEX"),
                Map.of("shared", "default", "defaultOnly", "default"),
                risk,
                false,
                "",
                ExecutionResourceStatus.ENABLED,
                "alice",
                now,
                now);
    }
}
