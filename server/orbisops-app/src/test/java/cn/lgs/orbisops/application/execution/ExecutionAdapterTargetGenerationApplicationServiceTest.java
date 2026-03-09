package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionAdapterTemplateRepository;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionRiskLevel;
import cn.lgs.orbisops.domain.execution.model.ExecutionTargetGenerationInput;
import cn.lgs.orbisops.domain.execution.model.ExecutionTargetSpecification;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionAdapterTargetGenerationApplicationServiceTest {

    @Test
    void loadsTemplateGeneratesSpecificationAndProvisionsTarget() {
        ExecutionAdapterTemplate template = template();
        IExecutionAdapterTemplateRepository repository = repository(template);
        AtomicReference<ExecutionTargetSpecification> captured = new AtomicReference<>();
        ExecutionTargetProvisioningPort<String> provisioningPort = new ExecutionTargetProvisioningPort<>() {
            @Override
            public String upsert(ExecutionTargetSpecification specification) {
                captured.set(specification);
                return specification.targetId();
            }

            @Override
            public String targetId(String target) {
                return target;
            }
        };
        ExecutionAdapterTargetGenerationApplicationService<String> service =
                new ExecutionAdapterTargetGenerationApplicationService<>(
                        repository, provisioningPort);

        String result = service.generate(input("mysql-template"));

        assertEquals("payment-mysql", result);
        assertEquals("payment", captured.get().projectId());
        assertEquals(ExecutionAdapterType.MYSQL_CONTROLLED,
                captured.get().adapterType());
        assertEquals("mysql-template", captured.get().templateId());
        assertEquals("payment-mysql", service.targetId(result));
    }

    @Test
    void missingTemplateFailsBeforeProvisioning() {
        IExecutionAdapterTemplateRepository repository = repository(null);
        ExecutionTargetProvisioningPort<String> provisioningPort = new ExecutionTargetProvisioningPort<>() {
            @Override
            public String upsert(ExecutionTargetSpecification specification) {
                throw new AssertionError("must not provision");
            }

            @Override
            public String targetId(String target) {
                return target;
            }
        };
        ExecutionAdapterTargetGenerationApplicationService<String> service =
                new ExecutionAdapterTargetGenerationApplicationService<>(
                        repository, provisioningPort);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.generate(input("missing-template")));

        assertEquals("执行适配器模板不存在：missing-template", error.getMessage());
    }

    private ExecutionTargetGenerationInput input(String templateId) {
        return new ExecutionTargetGenerationInput(
                "payment",
                templateId,
                "payment-mysql",
                "Payment MySQL",
                "worker-1",
                List.of("prod"),
                Map.of(),
                Map.of(),
                List.of(),
                null,
                ExecutionResourceStatus.ENABLED);
    }

    private ExecutionAdapterTemplate template() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        return new ExecutionAdapterTemplate(
                1L,
                "mysql-template",
                "MySQL Template",
                ExecutionAdapterType.MYSQL_CONTROLLED,
                List.of("MYSQL_CREATE_INDEX"),
                Map.of(),
                ExecutionRiskLevel.HIGH,
                false,
                "",
                ExecutionResourceStatus.ENABLED,
                "alice",
                now,
                now);
    }

    private IExecutionAdapterTemplateRepository repository(
            ExecutionAdapterTemplate template) {
        return new IExecutionAdapterTemplateRepository() {
            @Override public List<ExecutionAdapterTemplate> list() {
                return template == null ? List.of() : List.of(template);
            }
            @Override public Optional<ExecutionAdapterTemplate> find(String templateId) {
                return template != null && template.templateId().equals(templateId)
                        ? Optional.of(template)
                        : Optional.empty();
            }
            @Override public boolean exists(String templateId) {
                return find(templateId).isPresent();
            }
            @Override public ExecutionAdapterTemplate insert(ExecutionAdapterTemplate value) {
                return value;
            }
            @Override public ExecutionAdapterTemplate update(ExecutionAdapterTemplate value) {
                return value;
            }
            @Override public ExecutionAdapterTemplate updateStatus(
                    String templateId,
                    ExecutionResourceStatus status) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
