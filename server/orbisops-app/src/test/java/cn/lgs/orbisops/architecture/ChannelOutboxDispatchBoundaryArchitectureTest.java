package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelOutboxDispatchBoundaryArchitectureTest {

    private static final String CHANNEL =
            "orbisops-trigger/src/main/java/"
                    + "cn/lgs/orbisops/trigger/ops/channel/";

    @Test
    void notificationServiceDelegatesSingleRecordDurableDelivery()
            throws IOException {
        String service = read(CHANNEL + "OpsChannelNotificationService.java");
        String deliveryAdapter = read(CHANNEL + "OpsChannelNotificationDeliveryAdapter.java");
        String dispatcher = read(CHANNEL + "OpsChannelOutboxDispatcher.java");
        String commandFactory = read(CHANNEL + "OpsChannelOutboxDeliveryCommandFactory.java");
        String failurePolicy = read(CHANNEL + "OpsChannelOutboxDeliveryFailurePolicy.java");

        assertAll(
                () -> assertTrue(service.contains("ChannelNotificationUseCase notificationUseCase")),
                () -> assertTrue(service.contains("notificationUseCase.enqueueChatReply(")),
                () -> assertTrue(service.contains("outcome.appendExecutionNote()")),
                () -> assertFalse(service.contains("OpsChannelOutboxDispatcher")),
                () -> assertTrue(deliveryAdapter.contains("OpsChannelOutboxDispatcher dispatcher")),
                () -> assertTrue(deliveryAdapter.contains("dispatcher.dispatch(")),
                () -> assertTrue(deliveryAdapter.contains("new OpsChannelOutboxDispatcher.Settings(")),
                () -> assertTrue(deliveryAdapter.contains("result.appendExecutionNote()")),
                () -> assertTrue(service.contains("enqueueChatReply(")),
                () -> assertTrue(service.contains("processPending(")),
                () -> assertTrue(service.contains("batchProcessor.process(limit)")),
                () -> assertFalse(service.contains("enqueueService")),
                () -> assertTrue(deliveryAdapter.contains("enqueueService.enqueueReply(")),
                () -> assertTrue(service.contains("requeueDeadLetter(")),
                () -> assertTrue(service.contains("cancelDeadLetter(")),
                () -> assertTrue(service.contains("managementService.requeue(")),
                () -> assertTrue(service.contains("managementService.cancel(")),
                () -> assertFalse(service.contains("ChannelModels.Send")),
                () -> assertFalse(service.contains("new ChannelOutboxRecord(")),
                () -> assertFalse(service.contains("JSON.")),
                () -> assertFalse(service.contains("UUID.randomUUID()")),
                () -> assertFalse(service.contains("LocalDateTime.now()")),
                () -> assertFalse(service.contains("tryAcquireLease(")),
                () -> assertFalse(service.contains("markSucceeded(")),
                () -> assertFalse(service.contains("markFailed(")),
                () -> assertFalse(service.contains("containsReason(")),
                () -> assertFalse(service.contains(
                        "CHANNEL_DELIVERY_AUDIT_FAILED_AFTER_SEND")),
                () -> assertFalse(service.contains("@Slf4j")),
                () -> assertFalse(service.contains("log.warn(")),
                () -> assertTrue(service.lines().count() <= 200),
                () -> assertTrue(dispatcher.contains("record Settings(")),
                () -> assertTrue(dispatcher.contains("record Result(")),
                () -> assertTrue(dispatcher.contains(
                        "Supplier<String> leaseKeySupplier")),
                () -> assertTrue(dispatcher.contains(
                        "Supplier<LocalDateTime> nowSupplier")),
                () -> assertTrue(dispatcher.contains("tryAcquireLease(")),
                () -> assertTrue(dispatcher.contains("findById(")),
                () -> assertTrue(dispatcher.contains("commandFactory.create(task)")),
                () -> assertTrue(dispatcher.contains("failurePolicy.assess(")),
                () -> assertTrue(dispatcher.contains(
                        "channelChatProcessManager.send(")),
                () -> assertTrue(dispatcher.contains("markSucceeded(")),
                () -> assertTrue(dispatcher.contains("markFailed(")),
                () -> assertTrue(dispatcher.contains("log.warn(")),
                () -> assertTrue(dispatcher.contains(
                        "appendExecutionNote")),
                () -> assertFalse(dispatcher.contains("JSON.parseObject(")),
                () -> assertFalse(dispatcher.contains("ChannelModels.Send")),
                () -> assertFalse(dispatcher.contains(
                        "CHANNEL_DELIVERY_AUDIT_FAILED_AFTER_SEND")),
                () -> assertFalse(dispatcher.contains("containsReason(")),
                () -> assertFalse(dispatcher.contains("sanitize(")),
                () -> assertFalse(dispatcher.contains("@Service")),
                () -> assertFalse(dispatcher.contains("@Component")),
                () -> assertFalse(dispatcher.contains("@Value")),
                () -> assertFalse(dispatcher.contains("@Autowired")),
                () -> assertFalse(dispatcher.contains("OpsAgentRunRequestDTO")),
                () -> assertFalse(dispatcher.contains("OpsAnalysisResponseDTO")),
                () -> assertFalse(dispatcher.contains("ChannelQueryService")),
                () -> assertFalse(dispatcher.contains("OpsConfigAuditService")),
                () -> assertFalse(dispatcher.contains("enqueueChatReply(")),
                () -> assertFalse(dispatcher.contains("processPending(")),
                () -> assertFalse(dispatcher.contains("requeueDeadLetter(")),
                () -> assertFalse(dispatcher.contains("cancelDeadLetter(")),
                () -> assertTrue(dispatcher.lines().count() <= 150),
                () -> assertTrue(commandFactory.contains("new ChannelModels.Send(")),
                () -> assertTrue(commandFactory.contains("metadata.put(\"outboxId\"")),
                () -> assertTrue(commandFactory.contains(
                        "CHANNEL_NOTIFICATION_MESSAGE_MISSING")),
                () -> assertFalse(commandFactory.contains("IChannelOutboxRepository")),
                () -> assertFalse(commandFactory.contains("ChannelChatProcessManager")),
                () -> assertTrue(failurePolicy.contains(
                        "CHANNEL_DELIVERY_AUDIT_FAILED_AFTER_SEND")),
                () -> assertTrue(failurePolicy.contains(
                        "30 * (1 << Math.min(retry, 6))")),
                () -> assertTrue(failurePolicy.contains("containsReason(")),
                () -> assertTrue(failurePolicy.contains("sanitize(")),
                () -> assertFalse(failurePolicy.contains("IChannelOutboxRepository")),
                () -> assertFalse(failurePolicy.contains("ChannelChatProcessManager")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(parent.resolve("orbisops-trigger"))) {
            return parent;
        }
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) {
            return nested;
        }
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
