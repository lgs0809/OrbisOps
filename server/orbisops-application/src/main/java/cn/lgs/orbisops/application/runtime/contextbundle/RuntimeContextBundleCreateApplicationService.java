package cn.lgs.orbisops.application.runtime.contextbundle;

import cn.lgs.orbisops.domain.runtime.contextbundle.adapter.repository.IRuntimeContextBundleRepository;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleLayerInput;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextSkillSelection;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextToolsetSnapshot;
import cn.lgs.orbisops.domain.runtime.contextbundle.service.RuntimeContextBundlePolicy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RuntimeContextBundleCreateApplicationService {

    private final IRuntimeContextBundleRepository repository;
    private final RuntimeContextSkillSelectionPort skillSelection;
    private final RuntimeContextCanarySkillPort canarySkills;
    private final RuntimeContextToolsetPort toolsets;
    private final RuntimeContextBundleAuditPort audit;
    private final RuntimeContextBundlePolicy policy;

    public RuntimeContextBundleCreateApplicationService(
            IRuntimeContextBundleRepository repository,
            RuntimeContextSkillSelectionPort skillSelection,
            RuntimeContextCanarySkillPort canarySkills,
            RuntimeContextToolsetPort toolsets,
            RuntimeContextBundleAuditPort audit) {
        this(repository, skillSelection, canarySkills, toolsets, audit, new RuntimeContextBundlePolicy());
    }

    RuntimeContextBundleCreateApplicationService(
            IRuntimeContextBundleRepository repository,
            RuntimeContextSkillSelectionPort skillSelection,
            RuntimeContextCanarySkillPort canarySkills,
            RuntimeContextToolsetPort toolsets,
            RuntimeContextBundleAuditPort audit,
            RuntimeContextBundlePolicy policy) {
        if (repository == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_REPOSITORY_REQUIRED");
        if (skillSelection == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_SKILL_SELECTION_REQUIRED");
        if (canarySkills == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_CANARY_SKILLS_REQUIRED");
        if (toolsets == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_TOOLSETS_REQUIRED");
        if (audit == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_AUDIT_REQUIRED");
        if (policy == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_POLICY_REQUIRED");
        this.repository = repository;
        this.skillSelection = skillSelection;
        this.canarySkills = canarySkills;
        this.toolsets = toolsets;
        this.audit = audit;
        this.policy = policy;
    }

    public RuntimeContextBundleSnapshot create(RuntimeContextBundleCreateCommand command) {
        if (command == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_COMMAND_REQUIRED");
        RuntimeContextBundleLayerInput input = command.layerInput();
        Map<String, Object> bundle = new LinkedHashMap<>(policy.assembleBase(input));

        List<Map<String, Object>> memoryRefs = command.memoryRefs();
        String memoryHash = input.memoryContext().isBlank()
                ? ""
                : policy.hashObject(Map.of("memoryContext", input.memoryContext()));
        bundle.put("memoryContextRefs", memoryRefs);
        bundle.put("memoryContextHash", memoryHash);

        RuntimeContextSkillSelection selection = skillSelection.select(
                new RuntimeContextSkillSelectionRequest(
                        input.projectId(), input.agentId(), command.requestedSkillIds(),
                        input.query(), command.selectedSkillLimit()));
        if (selection == null) {
            throw new IllegalStateException("Skill Catalog 未返回后端权威选择结果");
        }
        List<Map<String, Object>> selectedSkills = policy.selectSkillRefs(
                selection,
                canarySkills.resolve(input.projectId(), input.agentId(), input.runId(), input.query()),
                command.selectedSkillLimit());
        bundle.put("skillCatalogRefs", selection.catalogRefs());
        bundle.put("skillCatalogHash", policy.hashObject(selection.catalogRefs()));
        bundle.put("skillSelectionSummary", map(
                "activeCount", selection.activeCount(),
                "catalogCount", selection.catalogCount(),
                "selectedCount", selectedSkills.size(),
                "suppressedSimilarCount", selection.suppressedRefs().size(),
                "suppressedRefs", selection.suppressedRefs()));
        bundle.put("usedSkillVersionRefs", selectedSkills);
        String skillRefsHash = policy.hashObject(selectedSkills);
        bundle.put("usedSkillRefsHash", skillRefsHash);

        List<RuntimeContextToolsetSnapshot> effectiveToolsets = List.copyOf(
                toolsets.listEffective(input.projectId(), input.actor()));
        List<Map<String, Object>> toolsetRefs = policy.toolsetRefs(effectiveToolsets);
        List<Map<String, Object>> policyRefs = policy.policyRefs(effectiveToolsets);
        bundle.put("policyRefs", policyRefs);
        bundle.put("policyHash", policy.hashObject(policyRefs));
        bundle.put("toolsetRefs", toolsetRefs);
        String toolsetBoundaryHash = policy.hashObject(map(
                "toolsetRefs", toolsetRefs,
                "policyRefs", policyRefs));
        bundle.put("toolsetBoundaryHash", toolsetBoundaryHash);
        String runtimeBoundaryHash = policy.hashObject(map(
                "projectId", input.projectId(),
                "agentId", input.agentId(),
                "policy", bundle.getOrDefault("policyContext", Map.of()),
                "changePackage", bundle.getOrDefault("changePackageContext", Map.of())));
        bundle.put("runtimeBoundaryHash", runtimeBoundaryHash);
        bundle.put("approvalBoundaryHash", policy.hashObject(firstNonNull(
                input.metadata().get("approvalBoundary"),
                bundle.get("approvalBoundary"),
                bundle.get("approvalBoundaryJson"),
                Map.of())));

        String bundleHash = policy.hashObject(bundle);
        bundle.put("contextBundleHash", bundleHash);
        RuntimeContextBundleSnapshot candidate = new RuntimeContextBundleSnapshot(
                null,
                input.bundleId(),
                bundleHash,
                input.sessionId(),
                input.runId(),
                input.projectId(),
                input.agentId(),
                input.actor(),
                memoryHash,
                memoryRefs,
                selectedSkills,
                skillRefsHash,
                toolsetBoundaryHash,
                runtimeBoundaryHash,
                bundle,
                input.createdAt());
        RuntimeContextBundleSnapshot saved = repository.save(candidate);
        audit.recordCreated(saved);
        return saved;
    }

    private Map<String, Object> map(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) return null;
        for (Object value : values) if (value != null) return value;
        return null;
    }
}
