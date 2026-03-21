package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.application.runtime.OpsRuntimeContextBundleAdapter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Creates the authoritative runtime context bundle and projects its references. */
final class OpsWorkSessionContextBundleCoordinator {

    private static final List<String> BUNDLE_FIELDS = List.of(
            "contextBundleId",
            "contextBundleHash",
            "memoryContextRefs",
            "memoryContextHash",
            "compressedMemorySummary",
            "memoryInjectionVersion",
            "usedSkillVersionRefs",
            "usedSkillRefsHash",
            "skillCatalogRefs",
            "skillCatalogHash",
            "skillSelectionSummary",
            "toolsetRefs",
            "toolsetBoundaryHash",
            "policyRefs",
            "policyHash",
            "runtimeBoundaryHash",
            "approvalBoundaryHash");

    private final OpsRuntimeContextBundleAdapter runtimeContextBundleService;

    OpsWorkSessionContextBundleCoordinator(
            OpsRuntimeContextBundleAdapter runtimeContextBundleService) {
        if (runtimeContextBundleService == null) {
            throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_SERVICE_REQUIRED");
        }
        this.runtimeContextBundleService = runtimeContextBundleService;
    }

    void create(OpsAgentChatRequest request,
                String memoryContext,
                Map<String, Object> metadata,
                List<OpsRuntimeEvent> events,
                Consumer<OpsRuntimeEvent> eventSink,
                long requestStartedNanos) {
        try {
            boolean pinnedResume = request != null
                    && request.getMetadata() != null
                    && Boolean.TRUE.equals(request.getMetadata().get("resumeContextBundlePinned"));
            Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
            Map<String, Object> bundle;
            if (pinnedResume) {
                bundle = runtimeContextBundleService.requireBundle(
                        value(String.valueOf(request.getMetadata().get("contextBundleId"))),
                        value(String.valueOf(request.getMetadata().get("contextBundleHash"))));
            } else {
                bundle = runtimeContextBundleService.createBundle(
                        request, memoryContext, safeMetadata);
            }
            request.getMetadata().put("_runtimeContextBundle", bundle);
            BUNDLE_FIELDS.forEach(key -> request.getMetadata().put(key, bundle.get(key)));
            request.setTrustedSkillFrame(OpsRuntimeSkillFrame.capture(request));
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType(pinnedResume
                            ? "RUNTIME_CONTEXT_BUNDLE_REUSED"
                            : "RUNTIME_CONTEXT_BUNDLE_CREATED")
                    .status("SUCCEEDED")
                    .summary(pinnedResume
                            ? "恢复运行已复用原始 Runtime Context Bundle，禁止上下文漂移。"
                            : "Runtime Context Bundle 已由后端权威生成并持久化。")
                    .payload(payloadWithElapsed(Map.of(
                            "contextBundleId", value(String.valueOf(bundle.get("contextBundleId"))),
                            "contextBundleHash", value(String.valueOf(bundle.get("contextBundleHash"))),
                            "resumed", pinnedResume),
                            requestStartedNanos))
                    .build());
        } catch (Exception error) {
            record(events, eventSink, OpsRuntimeEvent.builder()
                    .eventType("CONTEXT_BUNDLE_FAILED")
                    .status("FAILED")
                    .summary("Context Bundle 持久化失败，Work Session 已阻断：" + value(error.getMessage()))
                    .payload(payloadWithElapsed(
                            Map.of("error", value(error.getMessage())),
                            requestStartedNanos))
                    .build());
            throw new IllegalStateException(
                    "Runtime Context Bundle 生成失败，禁止启动 Work Session",
                    error);
        }
    }

    private void record(List<OpsRuntimeEvent> events,
                        Consumer<OpsRuntimeEvent> sink,
                        OpsRuntimeEvent event) {
        events.add(event);
        if (sink != null) sink.accept(event);
    }

    private Map<String, Object> payloadWithElapsed(
            Map<String, Object> seed,
            long startedNanos) {
        Map<String, Object> payload = new LinkedHashMap<>(
                seed == null ? Map.of() : seed);
        payload.put("elapsedMs", elapsedMs(startedNanos));
        return payload;
    }

    private long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }
}
