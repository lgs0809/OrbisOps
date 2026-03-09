package cn.lgs.orbisops.application.execution;

import cn.lgs.orbisops.domain.execution.adapter.repository.IExecutionAdapterTemplateRepository;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterTemplate;
import cn.lgs.orbisops.domain.execution.model.ExecutionAdapterType;
import cn.lgs.orbisops.domain.execution.model.ExecutionResourceStatus;
import cn.lgs.orbisops.domain.execution.model.ExecutionRiskLevel;
import cn.lgs.orbisops.domain.execution.service.ExecutionAdapterTemplatePolicy;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionAdapterTemplateCatalogApplicationServiceTest {

    @Test
    void createsTypedTemplateAndProjectsGeneratedTargets() {
        InMemoryRepository repository = new InMemoryRepository();
        ExecutionAdapterGeneratedTargetQueryPort targets = templateId -> List.of(
                new ExecutionAdapterGeneratedTarget(
                        "payment", "payment-mysql", "Payment MySQL", templateId,
                        "mysql-controlled", "worker-1", List.of("prod"), "ENABLED",
                        "2026-07-22T12:00:00"));
        ExecutionAdapterTemplateCatalogApplicationService service = service(repository, targets, "copy001");

        Map<String, Object> result = service.create(mutation(
                supplied("mysql-template"), supplied("MySQL Template"),
                supplied(ExecutionAdapterType.MYSQL_CONTROLLED),
                supplied(List.of("MYSQL_CREATE_INDEX")),
                supplied(Map.of("approvalRequired", true)),
                supplied(ExecutionRiskLevel.CRITICAL), supplied(false), absentText(),
                supplied(ExecutionResourceStatus.ENABLED), "alice"));

        assertEquals("mysql-template", result.get("adapterTemplateId"));
        assertEquals("mysql-controlled", result.get("adapterType"));
        assertEquals("CRITICAL", result.get("riskLevel"));
        assertEquals(1, result.get("generatedTargetCount"));
        List<?> generated = (List<?>) result.get("generatedTargets");
        assertEquals("payment-mysql", ((Map<?, ?>) generated.get(0)).get("resourceId"));
        assertTrue(repository.find("mysql-template").isPresent());
    }

    @Test
    void updatesWithoutChangingIdentityOrCreatorAndChangesStatus() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.insert(template("template-1", "Template 1", "alice"));
        ExecutionAdapterTemplateCatalogApplicationService service = service(
                repository, templateId -> List.of(), "copy001");

        Map<String, Object> updated = service.update("template-1", mutation(
                absentText(), supplied("Updated"), supplied(ExecutionAdapterType.DEPLOYMENT_HTTP),
                absentList(), absentMap(), supplied(ExecutionRiskLevel.LOW), absentBoolean(),
                absentText(), supplied(ExecutionResourceStatus.ENABLED), "mallory"));
        Map<String, Object> disabled = service.updateStatus(
                "template-1", ExecutionResourceStatus.DISABLED);

        assertEquals("template-1", updated.get("adapterTemplateId"));
        assertEquals("alice", updated.get("createBy"));
        assertEquals("Updated", updated.get("templateName"));
        assertEquals("deployment-http", updated.get("adapterType"));
        assertEquals("LOW", updated.get("riskLevel"));
        assertEquals("DISABLED", disabled.get("status"));
    }

    @Test
    void copyUsesDeterministicSuffixAndAuthenticatedCreatorFromCommand() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.insert(template("template-1", "Template 1", "alice"));
        ExecutionAdapterTemplateCatalogApplicationService service = service(
                repository, templateId -> List.of(), "copy001");

        Map<String, Object> copied = service.copy("template-1", mutation(
                absentText(), absentText(), absentType(), absentList(), absentMap(), absentRisk(),
                absentBoolean(), absentText(), absentStatus(), "bob"));

        assertEquals("template-1-copy-copy001", copied.get("adapterTemplateId"));
        assertEquals("Template 1 Copy", copied.get("templateName"));
        assertEquals("bob", copied.get("createBy"));
    }

    @Test
    void rejectsDuplicateAndImmutableIdentityChange() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.insert(template("template-1", "Template 1", "alice"));
        ExecutionAdapterTemplateCatalogApplicationService service = service(
                repository, templateId -> List.of(), "copy001");

        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> service.create(mutation(
                        supplied("template-1"), absentText(), supplied(ExecutionAdapterType.LOCAL_JAVA_SERVICE),
                        absentList(), absentMap(), absentRisk(), absentBoolean(), absentText(), absentStatus(), "bob")));
        IllegalArgumentException immutable = assertThrows(IllegalArgumentException.class,
                () -> service.update("template-1", mutation(
                        supplied("template-2"), absentText(), absentType(), absentList(), absentMap(), absentRisk(),
                        absentBoolean(), absentText(), absentStatus(), "bob")));

        assertTrue(duplicate.getMessage().contains("已存在"));
        assertEquals("EXECUTION_TEMPLATE_ID_IMMUTABLE", immutable.getMessage());
    }

    private ExecutionAdapterTemplateCatalogApplicationService service(
            IExecutionAdapterTemplateRepository repository,
            ExecutionAdapterGeneratedTargetQueryPort targets,
            String suffix) {
        return new ExecutionAdapterTemplateCatalogApplicationService(
                repository, targets, new ExecutionAdapterTemplatePolicy(), () -> suffix,
                () -> LocalDateTime.of(2026, 7, 22, 12, 0));
    }

    private ExecutionAdapterTemplateCommands.Mutation mutation(
            ExecutionAdapterTemplateCommands.Field<String> id,
            ExecutionAdapterTemplateCommands.Field<String> name,
            ExecutionAdapterTemplateCommands.Field<ExecutionAdapterType> type,
            ExecutionAdapterTemplateCommands.Field<List<String>> actions,
            ExecutionAdapterTemplateCommands.Field<Map<String, Object>> config,
            ExecutionAdapterTemplateCommands.Field<ExecutionRiskLevel> risk,
            ExecutionAdapterTemplateCommands.Field<Boolean> readOnly,
            ExecutionAdapterTemplateCommands.Field<String> description,
            ExecutionAdapterTemplateCommands.Field<ExecutionResourceStatus> status,
            String actor) {
        return new ExecutionAdapterTemplateCommands.Mutation(
                id, name, type, actions, config, risk, readOnly, description, status, actor);
    }

    private ExecutionAdapterTemplate template(String id, String name, String creator) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 22, 12, 0);
        return new ExecutionAdapterTemplate(
                1L, id, name, ExecutionAdapterType.LOCAL_JAVA_SERVICE,
                List.of("ARTIFACT_DEPLOY"), Map.of("adapterTemplateId", id),
                ExecutionRiskLevel.HIGH, false, "", ExecutionResourceStatus.ENABLED,
                creator, now, now);
    }

    private static <T> ExecutionAdapterTemplateCommands.Field<T> supplied(T value) {
        return ExecutionAdapterTemplateCommands.Field.supplied(value);
    }

    private static ExecutionAdapterTemplateCommands.Field<String> absentText() {
        return ExecutionAdapterTemplateCommands.Field.absent();
    }

    private static ExecutionAdapterTemplateCommands.Field<ExecutionAdapterType> absentType() {
        return ExecutionAdapterTemplateCommands.Field.absent();
    }

    private static ExecutionAdapterTemplateCommands.Field<List<String>> absentList() {
        return ExecutionAdapterTemplateCommands.Field.absent();
    }

    private static ExecutionAdapterTemplateCommands.Field<Map<String, Object>> absentMap() {
        return ExecutionAdapterTemplateCommands.Field.absent();
    }

    private static ExecutionAdapterTemplateCommands.Field<ExecutionRiskLevel> absentRisk() {
        return ExecutionAdapterTemplateCommands.Field.absent();
    }

    private static ExecutionAdapterTemplateCommands.Field<Boolean> absentBoolean() {
        return ExecutionAdapterTemplateCommands.Field.absent();
    }

    private static ExecutionAdapterTemplateCommands.Field<ExecutionResourceStatus> absentStatus() {
        return ExecutionAdapterTemplateCommands.Field.absent();
    }

    private static final class InMemoryRepository implements IExecutionAdapterTemplateRepository {
        private final Map<String, ExecutionAdapterTemplate> values = new LinkedHashMap<>();
        private long sequence;

        @Override public List<ExecutionAdapterTemplate> list() { return new ArrayList<>(values.values()); }
        @Override public Optional<ExecutionAdapterTemplate> find(String templateId) {
            return Optional.ofNullable(values.get(templateId));
        }
        @Override public boolean exists(String templateId) { return values.containsKey(templateId); }

        @Override
        public ExecutionAdapterTemplate insert(ExecutionAdapterTemplate template) {
            if (values.containsKey(template.templateId())) throw new IllegalArgumentException("duplicate");
            ExecutionAdapterTemplate stored = new ExecutionAdapterTemplate(
                    ++sequence, template.templateId(), template.templateName(), template.adapterType(),
                    template.supportedActions(), template.defaultConfig(), template.riskLevel(),
                    template.readOnly(), template.description(), template.status(), template.createBy(),
                    template.createdAt(), template.updatedAt());
            values.put(stored.templateId(), stored);
            return stored;
        }

        @Override public ExecutionAdapterTemplate update(ExecutionAdapterTemplate template) {
            values.put(template.templateId(), template);
            return template;
        }

        @Override
        public ExecutionAdapterTemplate updateStatus(String templateId, ExecutionResourceStatus status) {
            ExecutionAdapterTemplate current = values.get(templateId);
            ExecutionAdapterTemplate updated = current.withStatus(
                    status, LocalDateTime.of(2026, 7, 22, 13, 0));
            values.put(templateId, updated);
            return updated;
        }
    }
}
