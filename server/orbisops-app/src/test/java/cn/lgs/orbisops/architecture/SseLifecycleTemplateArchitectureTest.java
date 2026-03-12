package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseLifecycleTemplateArchitectureTest {

    private static final String TRIGGER = "orbisops-trigger/src/main/java/"
            + "cn/lgs/orbisops/trigger/";

    @Test
    void sharedTemplateMustOwnMechanicalSseLifecycle() throws IOException {
        String template = read(TRIGGER + "http/sse/OpsSseExecutionTemplate.java");
        String session = read(TRIGGER + "http/sse/OpsSseStreamSession.java");
        String writer = read(TRIGGER + "http/sse/OpsSseEventWriter.java");
        String guard = read(TRIGGER + "http/sse/OpsSseCompletionGuard.java");

        assertAll(
                () -> assertTrue(template.contains("response.setContentType(\"text/event-stream\")")),
                () -> assertTrue(template.contains("response.setHeader(\"X-Accel-Buffering\", \"no\")")),
                () -> assertTrue(template.contains("emitter.onCompletion")),
                () -> assertTrue(template.contains("emitter.onTimeout")),
                () -> assertTrue(template.contains("emitter.onError")),
                () -> assertTrue(template.contains("executor.submit")),
                () -> assertTrue(template.contains("session.reject")),
                () -> assertTrue(session.contains("registerResource(")),
                () -> assertTrue(session.contains("trySendData(")),
                () -> assertTrue(session.contains("SSE_SESSION_CLOSED")),
                () -> assertTrue(writer.contains("发送 SSE 事件失败")),
                () -> assertTrue(guard.contains("compareAndSet(State.OPEN")),
                () -> assertFalse(template.contains("extends OpsSse")),
                () -> assertFalse(session.contains("extends OpsSse")));
    }

    @Test
    void targetControllersMustComposeTemplateWithoutManualEmitterLifecycle() throws IOException {
        String adminChat = read(TRIGGER + "http/admin/OpsAgentChatAdminController.java");
        String projectChat = read(TRIGGER + "http/agent/OpsChatController.java");
        String userChat = read(TRIGGER + "http/agent/OpsUserChatController.java");
        String run = read(TRIGGER + "http/admin/OpsAgentRunAdminController.java");
        String graph = read(TRIGGER + "http/admin/OpsGraphEventSseService.java");
        String combined = adminChat + projectChat + userChat + run + graph;

        assertAll(
                () -> assertTrue(adminChat.contains("OpsSseExecutionTemplate sseTemplate")),
                () -> assertTrue(projectChat.contains("OpsSseExecutionTemplate sseTemplate")),
                () -> assertTrue(userChat.contains("OpsSseExecutionTemplate sseTemplate")),
                () -> assertTrue(graph.contains("OpsSseExecutionTemplate sseTemplate")),
                () -> assertTrue(graph.contains("session.registerResource(subscription)")),
                () -> assertTrue(graph.contains("Set<Long> sentSequences")),
                () -> assertTrue(graph.contains("matches(runId, event)")),
                () -> assertTrue(projectChat.contains("prepareStreamRequest")),
                () -> assertTrue(projectChat.contains("ensureForChat")),
                () -> assertTrue(projectChat.contains("executeStream")),
                () -> assertTrue(userChat.contains("assertProjectAccess")),
                () -> assertTrue(adminChat.contains("executeAdminStream")),
                () -> assertTrue(run.contains("graphEventSseService.stream(runId, response)")),
                () -> assertFalse(combined.contains("new SseEmitter(")),
                () -> assertFalse(combined.contains(".onCompletion(")),
                () -> assertFalse(combined.contains(".onTimeout(")),
                () -> assertFalse(combined.contains(".onError(")),
                () -> assertFalse(combined.contains("sendSseEvent(")),
                () -> assertFalse(combined.contains("setSseHeaders(")),
                () -> assertFalse(combined.contains("flushResponse(")),
                () -> assertFalse(combined.contains("AtomicBoolean")));
    }

    @Test
    void chatCancellationPoliciesMustRemainExplicitAndDifferent() throws IOException {
        String adminChat = read(TRIGGER + "http/admin/OpsAgentChatAdminController.java");
        String projectChat = read(TRIGGER + "http/agent/OpsChatController.java");
        String userChat = read(TRIGGER + "http/agent/OpsUserChatController.java");

        assertAll(
                () -> assertTrue(adminChat.contains("true,\n                        true,")),
                () -> assertTrue(projectChat.contains("false,\n                        false,")),
                () -> assertTrue(userChat.contains("false,\n                        false,")),
                () -> assertTrue(projectChat.contains("Work Session 继续后台执行")),
                () -> assertTrue(userChat.contains("Work Session 继续后台执行")),
                () -> assertTrue(adminChat.contains("已停止继续执行")));
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
