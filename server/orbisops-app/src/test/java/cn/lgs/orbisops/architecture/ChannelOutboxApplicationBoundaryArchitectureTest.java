package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelOutboxApplicationBoundaryArchitectureTest {

    private static final String APPLICATION =
            "orbisops-application/src/main/java/"
                    + "cn/lgs/orbisops/application/channel/";
    private static final String CHANNEL =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/channel/";

    @Test
    void notificationFacadeDelegatesOutboxCommandsQueriesAndBatchWork() throws IOException {
        String useCase = read(APPLICATION + "ChannelNotificationUseCase.java");
        String deliveryPort = read(APPLICATION + "ChannelNotificationDeliveryPort.java");
        String service = read(CHANNEL + "OpsChannelNotificationService.java");
        String deliveryAdapter = read(CHANNEL + "OpsChannelNotificationDeliveryAdapter.java");
        String assembly = read(CHANNEL + "OpsChannelNotificationAssembly.java");
        String enqueue = read(CHANNEL + "OpsChannelOutboxEnqueueService.java");
        String query = read(CHANNEL + "OpsChannelOutboxQueryService.java");
        String management = read(CHANNEL + "OpsChannelOutboxManagementService.java");
        String batch = read(CHANNEL + "OpsChannelOutboxBatchProcessor.java");

        assertAll(
                () -> assertTrue(deliveryPort.contains("interface ChannelNotificationDeliveryPort")),
                () -> assertTrue(useCase.contains("class ChannelNotificationUseCase")),
                () -> assertTrue(useCase.contains("deliveryPort.enqueueAnalysis(command)")),
                () -> assertTrue(useCase.contains("deliveryPort.enqueueReply(command)")),
                () -> assertTrue(useCase.contains("deliveryPort.dispatch(outboxId)")),
                () -> assertFalse(useCase.contains("org.springframework")),
                () -> assertFalse(useCase.contains("IChannelOutboxRepository")),
                () -> assertTrue(service.contains("notificationUseCase.notifyIfNeeded(")),
                () -> assertTrue(service.contains("notificationUseCase.enqueueChatReply(")),
                () -> assertTrue(service.contains("queryService.list(")),
                () -> assertTrue(service.contains("queryService.status(")),
                () -> assertTrue(service.contains("queryService.statusAll(")),
                () -> assertTrue(service.contains("managementService.requeue(")),
                () -> assertTrue(service.contains("managementService.cancel(")),
                () -> assertTrue(service.contains("batchProcessor.process(limit)")),
                () -> assertFalse(service.contains("recoverExpiredLeases(")),
                () -> assertFalse(service.contains("findDispatchableIds(")),
                () -> assertFalse(service.contains("findByProjectAndId(")),
                () -> assertFalse(service.contains("recordRuntimeEvent(")),
                () -> assertFalse(service.contains("IChannelOutboxRepository")),
                () -> assertFalse(service.contains("OpsConfigAuditService")),
                () -> assertFalse(service.contains("ChannelOutboundContentPolicy")),
                () -> assertTrue(service.lines().count() <= 140),
                () -> assertTrue(deliveryAdapter.contains("OpsChannelOutboxEnqueueService enqueueService")),
                () -> assertTrue(deliveryAdapter.contains("OpsChannelOutboxDispatcher dispatcher")),
                () -> assertTrue(deliveryAdapter.contains("ChannelOutboundContentPolicy contentPolicy")),
                () -> assertTrue(assembly.contains("ChannelNotificationUseCase channelNotificationUseCase")),
                () -> assertTrue(assembly.contains("OpsChannelOutboxManagementService opsChannelOutboxManagementService")),
                () -> assertTrue(enqueue.contains("channelQueryService.get(")),
                () -> assertTrue(enqueue.contains("repository.enqueue(record)")),
                () -> assertTrue(enqueue.contains("CHANNEL_REPLY_ENQUEUED")),
                () -> assertTrue(enqueue.contains("repository.cancel(record.projectId(), id)")),
                () -> assertFalse(enqueue.contains("tryAcquireLease(")),
                () -> assertTrue(query.contains("repository.findByProject(")),
                () -> assertTrue(query.contains("repository.findByProjectAndId(")),
                () -> assertTrue(query.contains("repository.status(")),
                () -> assertTrue(query.contains("repository.statusAll(")),
                () -> assertFalse(query.contains("repository.enqueue(")),
                () -> assertFalse(query.contains("auditService")),
                () -> assertTrue(management.contains("repository.requeue(")),
                () -> assertTrue(management.contains("repository.cancel(")),
                () -> assertTrue(management.contains("auditService.record(")),
                () -> assertFalse(management.contains("repository.enqueue(")),
                () -> assertTrue(batch.contains("repository.recoverExpiredLeases(")),
                () -> assertTrue(batch.contains("repository.findDispatchableIds(")),
                () -> assertTrue(batch.contains("dispatcher.dispatch(")),
                () -> assertFalse(batch.contains("ChannelQueryService")),
                () -> assertFalse(batch.contains("OpsConfigAuditService")));
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
