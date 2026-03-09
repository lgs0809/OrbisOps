package cn.lgs.orbisops.application.memory;

import cn.lgs.orbisops.domain.memory.model.ContextMemorySnapshot;
import cn.lgs.orbisops.domain.memory.service.ContextMemoryDefinitionPolicy;

import java.math.BigDecimal;
import java.util.function.Supplier;

/** Application use case for typed Context Memory create, partial update and lifecycle mutation. */
public class ContextMemoryAdminApplicationService {

    private final ContextMemoryStoreApplicationService storeService;
    private final ContextMemoryDefinitionPolicy definitionPolicy;
    private final Supplier<String> identitySupplier;

    public ContextMemoryAdminApplicationService(ContextMemoryStoreApplicationService storeService,
                                                ContextMemoryDefinitionPolicy definitionPolicy,
                                                Supplier<String> identitySupplier) {
        this.storeService = storeService;
        this.definitionPolicy = definitionPolicy == null
                ? new ContextMemoryDefinitionPolicy()
                : definitionPolicy;
        this.identitySupplier = identitySupplier == null ? () -> "" : identitySupplier;
    }

    public ContextMemorySnapshot create(ContextMemoryMutationCommand command) {
        ContextMemoryMutationCommand required = requireCommand(command);
        String memoryId = hasText(required.memoryId())
                ? required.memoryId().trim()
                : generatedMemoryId();
        return requireStore().create(snapshot(memoryId, required, null));
    }

    public ContextMemorySnapshot update(String memoryId, ContextMemoryMutationCommand command) {
        if (!hasText(memoryId)) throw new IllegalArgumentException("memoryId 不能为空");
        ContextMemorySnapshot before = requireStore().require(memoryId.trim());
        return requireStore().upsert(snapshot(memoryId.trim(), requireCommand(command), before));
    }

    public ContextMemorySnapshot updateStatus(String memoryId, String status) {
        if (!hasText(memoryId)) throw new IllegalArgumentException("memoryId 不能为空");
        String normalized = definitionPolicy.normalizeStatus(status, true);
        return requireStore().updateStatus(memoryId.trim(), normalized);
    }

    private ContextMemorySnapshot snapshot(String memoryId,
                                           ContextMemoryMutationCommand command,
                                           ContextMemorySnapshot before) {
        String scopeType = definitionPolicy.normalizeScope(
                value(command.scopeType(), before == null ? null : before.scopeType()), true);
        String scopeId = required(
                value(command.scopeId(), before == null ? null : before.scopeId()),
                "scopeId 不能为空");
        String memoryType = definitionPolicy.normalizeMemoryType(
                value(command.memoryType(), before == null ? null : before.memoryType()), true);
        definitionPolicy.validateScopeAndType(scopeType, memoryType);
        String title = required(value(command.title(), before == null ? null : before.title()), "title 不能为空");
        String summary = required(value(command.summary(), before == null ? null : before.summary()), "summary 不能为空");
        String content = required(value(command.content(), before == null ? null : before.content()), "content 不能为空");
        String status = definitionPolicy.normalizeStatus(
                value(command.status(), before == null ? "ACTIVE" : before.status()), true);
        BigDecimal confidence = definitionPolicy.normalizeConfidence(
                command.confidencePresent()
                        ? command.confidence()
                        : before == null ? null : before.confidence());
        return new ContextMemorySnapshot(
                before == null ? null : before.id(),
                memoryId,
                scopeType,
                scopeId,
                memoryType,
                title,
                summary,
                content,
                value(command.keywords(), before == null ? "" : before.keywords()),
                status,
                confidence,
                value(command.sourceType(), before == null ? "" : before.sourceType()),
                value(command.sourceId(), before == null ? "" : before.sourceId()),
                value(command.sourceMessageHash(), before == null ? "" : before.sourceMessageHash()),
                value(command.createdBy(), before == null ? "" : before.createdBy()),
                before == null ? "" : before.createTime(),
                before == null ? "" : before.updateTime(),
                before == null ? "" : before.expireTime());
    }

    private String generatedMemoryId() {
        String identity = identitySupplier.get();
        if (!hasText(identity)) throw new IllegalStateException("Context Memory identity generator unavailable");
        return "ctx-mem-" + identity.trim();
    }

    private ContextMemoryMutationCommand requireCommand(ContextMemoryMutationCommand command) {
        if (command == null) throw new IllegalArgumentException("Context Memory 请求不能为空");
        return command;
    }

    private ContextMemoryStoreApplicationService requireStore() {
        if (storeService == null) throw new IllegalStateException("Context Memory 数据库未配置");
        return storeService;
    }

    private String value(String supplied, String fallback) {
        return supplied == null ? fallback : supplied;
    }

    private String required(String value, String message) {
        if (!hasText(value)) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
