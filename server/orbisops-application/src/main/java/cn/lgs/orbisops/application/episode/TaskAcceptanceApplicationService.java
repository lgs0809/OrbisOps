package cn.lgs.orbisops.application.episode;

import cn.lgs.orbisops.domain.skill.model.TaskAcceptanceRequest;
import cn.lgs.orbisops.domain.skill.service.TaskAcceptancePolicy;
import cn.lgs.orbisops.domain.skill.service.TaskAcceptanceObservabilityProjection;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import cn.lgs.orbisops.domain.shared.json.CanonicalJson;

public class TaskAcceptanceApplicationService {
    private final TaskAcceptancePort port;
    private final TaskAcceptanceDraftModelPort model;
    private final TaskAcceptancePolicy policy = new TaskAcceptancePolicy();
    public TaskAcceptanceApplicationService(TaskAcceptancePort port) { this(port, null); }
    public TaskAcceptanceApplicationService(TaskAcceptancePort port, TaskAcceptanceDraftModelPort model) {
        this.port = port; this.model = model;
    }
    public record NaturalRequest(long revision, String instruction) { }
    @SuppressWarnings("unchecked")
    public Map<String,Object> draft(String project, String episode, NaturalRequest input, String actor, boolean admin) {
        if (input == null || input.revision() < 1 || input.instruction() == null
                || input.instruction().trim().isEmpty() || input.instruction().length() > 4000)
            throw new IllegalArgumentException("TASK_ACCEPTANCE_DESCRIPTION_REQUIRED");
        var evidence = port.draftEvidence(project, episode, actor, admin);
        requireRevision(evidence, input.revision());
        var receipts = ((List<Map<String,Object>>) evidence.get("receipts")).stream().map(receipt -> {
            var view = new java.util.LinkedHashMap<>(receipt);
            var content = new TaskAcceptanceObservabilityProjection().content((Map<String,Object>)receipt.get("content"));
            view.put("content", content);
            return view;
        }).toList();
        if (receipts.isEmpty()) return Map.of("status", "INSUFFICIENT_EVIDENCE", "explanation", "当前任务还没有可核验的真实回执。");
        // Thousands of raw samples remain in the immutable receipt. Supply their recomputed business
        // aggregates to the model without letting early array entries crowd out reachability/latency.
        var promptReceipts = receipts.stream().map(receipt -> {
            var view = new java.util.LinkedHashMap<>(receipt);
            var content = new java.util.LinkedHashMap<>((Map<String,Object>)receipt.get("content"));
            if (content.containsKey(TaskAcceptanceObservabilityProjection.KEY)) content.remove("series");
            view.put("content", content);
            return view;
        }).toList();
        var promptEvidence = new java.util.LinkedHashMap<>(evidence);
        promptEvidence.put("receipts", promptReceipts);
        var checkableFields = promptReceipts.stream().map(r -> {
            var pointers = new java.util.ArrayList<String>();
            collectBusinessFields(r.get("content"), "", pointers, 0, (Map<String,Object>)r.get("content"));
            return Map.of("resultId", r.get("resultId"), "allowedPointers", pointers);
        }).toList();
        String frozen = CanonicalJson.stringify(Map.of("task", promptEvidence, "reviewerInstruction", input.instruction(),
                "checkableFields", checkableFields));
        if (frozen.length() > 64000) throw new IllegalStateException("TASK_ACCEPTANCE_EVIDENCE_TOO_LARGE");
        if (model == null) throw new IllegalStateException("TASK_ACCEPTANCE_DRAFT_UNAVAILABLE");
        var draft = model.propose(frozen);
        // No session/database lock is held while the provider is responding.
        requireRevision(port.inspect(project, episode, actor, admin), input.revision());
        if (draft == null || draft.explanation() == null || draft.explanation().isBlank() || draft.explanation().length() > 2000
                || draft.checks() == null) throw new IllegalStateException("TASK_ACCEPTANCE_DRAFT_INVALID");
        if ("INSUFFICIENT_EVIDENCE".equals(draft.status()) && draft.checks().isEmpty())
            return Map.of("status", draft.status(), "explanation", draft.explanation());
        if (!"READY".equals(draft.status())) throw new IllegalStateException("TASK_ACCEPTANCE_DRAFT_INVALID");
        var criteria = draft.checks().stream().map(check -> {
            if (check == null || check.label() == null || check.label().isBlank() || check.label().length() > 300)
                throw new IllegalStateException("TASK_ACCEPTANCE_DRAFT_INVALID");
            var receipt = receipts.stream().filter(r -> r.get("resultId").equals(check.resultId())).findFirst()
                    .orElseThrow(() -> new SecurityException("TASK_ACCEPTANCE_EVIDENCE_SCOPE_FORBIDDEN"));
            return new TaskAcceptanceRequest.Criterion(check.resultId(), (String)receipt.get("outputHash"),
                    check.pointer(), check.operator(), check.expected());
        }).toList();
        var request = new TaskAcceptanceRequest(UUID.randomUUID().toString(), input.revision(), draft.goalReview(), criteria);
        policy.validate(request);
        for (var criterion : criteria) {
            var receipt = receipts.stream().filter(r -> r.get("resultId").equals(criterion.resultId())).findFirst().orElseThrow();
            if ("UNKNOWN".equals(policy.check(criterion, (Map<String,Object>)receipt.get("content")).get("verdict")))
                throw new IllegalStateException("TASK_ACCEPTANCE_DRAFT_FIELD_UNAVAILABLE");
        }
        return Map.of("status", "READY", "explanation", draft.explanation(), "request", request,
                "checks", draft.checks().stream().map(c -> Map.of("label", c.label(), "operator", c.operator(), "expected", c.expected())).toList());
    }
    private void collectBusinessFields(Object value, String path, List<String> pointers, int depth, Map<String,Object> root) {
        if (depth > 20 || path.length() > 400 || pointers.size() >= 1000 || policy.isReceiptMetadata(path, root)) return;
        if (value instanceof Map<?,?> map) map.forEach((key, child) -> collectBusinessFields(child,
                path + "/" + String.valueOf(key).replace("~", "~0").replace("/", "~1"), pointers, depth + 1, root));
        else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size() && pointers.size() < 1000; i++) collectBusinessFields(list.get(i), path + "/" + i, pointers, depth + 1, root);
        } else if (!path.isEmpty() && (value instanceof String || value instanceof Number || value instanceof Boolean)) pointers.add(path);
    }
    private void requireRevision(Map<String,Object> evidence, long revision) {
        if (((Number)evidence.get("revision")).longValue() != revision)
            throw new IllegalStateException("TASK_ACCEPTANCE_REVISION_CHANGED");
        if (((Number)evidence.get("pendingTurns")).longValue() > 0)
            throw new IllegalStateException("TASK_ACCEPTANCE_PENDING_TURNS");
    }
    public Map<String,Object> inspect(String project, String episode, String actor, boolean admin) {
        return port.inspect(project, episode, actor, admin);
    }
    public Map<String,Object> verify(String project, String episode, TaskAcceptanceRequest request, String actor, boolean admin) {
        policy.validate(request);
        return port.verify(project, episode, request, actor, admin);
    }
}
