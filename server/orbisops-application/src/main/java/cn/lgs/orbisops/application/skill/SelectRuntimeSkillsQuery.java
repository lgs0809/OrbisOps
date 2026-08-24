package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.skill.model.SkillRuntimeCandidate;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeSelection;
import cn.lgs.orbisops.domain.skill.model.SkillRuntimeSelectionRequest;
import cn.lgs.orbisops.domain.skill.service.SkillCatalogFingerprint;
import cn.lgs.orbisops.domain.skill.service.SkillRuntimeSelectionPolicy;
import cn.lgs.orbisops.domain.skill.service.SkillRuntimeRecallPolicy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Application query that assembles, scores and maps authoritative runtime Skill selection. */
public final class SelectRuntimeSkillsQuery {

    private final SkillCatalogPort catalogPort;
    private final SkillSemanticScorePort semanticScorePort;
    private final SkillRerankPort rerankPort;
    private final SkillRuntimeSelectionSettings settings;
    private final SkillRuntimeCandidateAssembler candidateAssembler;
    private final SkillRuntimeSelectionPolicy selectionPolicy;
    private final SkillRuntimeRecallPolicy recallPolicy;
    private final SkillRuntimeSelectionViewMapper selectionViews;
    private final SkillRuntimePublishedVersionPort publishedVersions;
    private final SkillApplicabilityPort applicability;

    public SelectRuntimeSkillsQuery(SkillCatalogPort catalogPort,
                                    SkillSemanticScorePort semanticScorePort,
                                    SkillRuntimeSelectionSettings settings) {
        this(catalogPort, semanticScorePort, (query, candidates) -> Map.of(), settings,
                new SkillRuntimeCandidateAssembler(), new SkillRuntimeSelectionPolicy(),
                new SkillRuntimeRecallPolicy(),
                new SkillRuntimeSelectionViewMapper(new SkillBoundReferenceMapper()));
    }

    public SelectRuntimeSkillsQuery(SkillCatalogPort catalogPort, SkillSemanticScorePort semanticScorePort,
                                   SkillRerankPort rerankPort, SkillRuntimeSelectionSettings settings,
                                   SkillRuntimePublishedVersionPort publishedVersions) {
        this(catalogPort,semanticScorePort,rerankPort,settings,publishedVersions,SkillApplicabilityPort.UNAVAILABLE);
    }

    public SelectRuntimeSkillsQuery(SkillCatalogPort catalogPort, SkillSemanticScorePort semanticScorePort,
                                   SkillRerankPort rerankPort, SkillRuntimeSelectionSettings settings,
                                   SkillRuntimePublishedVersionPort publishedVersions, SkillApplicabilityPort applicability) {
        this(catalogPort, semanticScorePort, rerankPort, settings, new SkillRuntimeCandidateAssembler(),
                new SkillRuntimeSelectionPolicy(), new SkillRuntimeRecallPolicy(),
                new SkillRuntimeSelectionViewMapper(new SkillBoundReferenceMapper()), publishedVersions, applicability);
    }

    public SelectRuntimeSkillsQuery(SkillCatalogPort catalogPort,
                                    SkillSemanticScorePort semanticScorePort,
                                    SkillRerankPort rerankPort,
                                    SkillRuntimeSelectionSettings settings) {
        this(catalogPort, semanticScorePort, rerankPort, settings,
                new SkillRuntimeCandidateAssembler(), new SkillRuntimeSelectionPolicy(),
                new SkillRuntimeRecallPolicy(),
                new SkillRuntimeSelectionViewMapper(new SkillBoundReferenceMapper()));
    }

    SelectRuntimeSkillsQuery(SkillCatalogPort catalogPort,
                             SkillSemanticScorePort semanticScorePort,
                             SkillRerankPort rerankPort,
                             SkillRuntimeSelectionSettings settings,
                             SkillRuntimeCandidateAssembler candidateAssembler,
                             SkillRuntimeSelectionPolicy selectionPolicy,
                             SkillRuntimeRecallPolicy recallPolicy) {
        this(catalogPort, semanticScorePort, rerankPort, settings,
                candidateAssembler, selectionPolicy, recallPolicy,
                new SkillRuntimeSelectionViewMapper(new SkillBoundReferenceMapper()));
    }

    SelectRuntimeSkillsQuery(SkillCatalogPort catalogPort,
                             SkillSemanticScorePort semanticScorePort,
                             SkillRerankPort rerankPort,
                             SkillRuntimeSelectionSettings settings,
                             SkillRuntimeCandidateAssembler candidateAssembler,
                             SkillRuntimeSelectionPolicy selectionPolicy,
                             SkillRuntimeRecallPolicy recallPolicy,
                             SkillRuntimeSelectionViewMapper selectionViews) {
        this(catalogPort, semanticScorePort, rerankPort, settings, candidateAssembler,
                selectionPolicy, recallPolicy, selectionViews, (project, candidates) -> candidates);
    }

    private SelectRuntimeSkillsQuery(SkillCatalogPort catalogPort,
                             SkillSemanticScorePort semanticScorePort,
                             SkillRerankPort rerankPort,
                             SkillRuntimeSelectionSettings settings,
                             SkillRuntimeCandidateAssembler candidateAssembler,
                             SkillRuntimeSelectionPolicy selectionPolicy,
                             SkillRuntimeRecallPolicy recallPolicy,
                             SkillRuntimeSelectionViewMapper selectionViews,
                             SkillRuntimePublishedVersionPort publishedVersions) {
        this(catalogPort,semanticScorePort,rerankPort,settings,candidateAssembler,selectionPolicy,recallPolicy,
                selectionViews,publishedVersions,SkillApplicabilityPort.UNAVAILABLE);
    }

    private SelectRuntimeSkillsQuery(SkillCatalogPort catalogPort, SkillSemanticScorePort semanticScorePort,
                             SkillRerankPort rerankPort, SkillRuntimeSelectionSettings settings,
                             SkillRuntimeCandidateAssembler candidateAssembler, SkillRuntimeSelectionPolicy selectionPolicy,
                             SkillRuntimeRecallPolicy recallPolicy, SkillRuntimeSelectionViewMapper selectionViews,
                             SkillRuntimePublishedVersionPort publishedVersions, SkillApplicabilityPort applicability) {
        if (catalogPort == null) throw new IllegalArgumentException("SKILL_CATALOG_PORT_REQUIRED");
        if (semanticScorePort == null) throw new IllegalArgumentException("SKILL_SEMANTIC_SCORE_PORT_REQUIRED");
        if (rerankPort == null) throw new IllegalArgumentException("SKILL_RERANK_PORT_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("SKILL_RUNTIME_SETTINGS_REQUIRED");
        if (candidateAssembler == null) throw new IllegalArgumentException("SKILL_RUNTIME_ASSEMBLER_REQUIRED");
        if (selectionPolicy == null) throw new IllegalArgumentException("SKILL_RUNTIME_POLICY_REQUIRED");
        if (recallPolicy == null) throw new IllegalArgumentException("SKILL_RUNTIME_RECALL_POLICY_REQUIRED");
        if (selectionViews == null) throw new IllegalArgumentException("SKILL_RUNTIME_VIEW_MAPPER_REQUIRED");
        this.catalogPort = catalogPort;
        this.semanticScorePort = semanticScorePort;
        this.rerankPort = rerankPort;
        this.settings = settings;
        this.candidateAssembler = candidateAssembler;
        this.selectionPolicy = selectionPolicy;
        this.recallPolicy = recallPolicy;
        this.selectionViews = selectionViews;
        this.publishedVersions = java.util.Objects.requireNonNull(publishedVersions);
        this.applicability = java.util.Objects.requireNonNull(applicability);
    }

    public Result select(Request request) {
        if (request == null) throw new IllegalArgumentException("SKILL_RUNTIME_SELECTION_REQUEST_REQUIRED");
        if (request.requestedSkillIds().size() > settings.maxExplicitSkills()) {
            throw new IllegalArgumentException("TOO_MANY_EXPLICIT_SKILLS: 最多显式选择 "
                    + settings.maxExplicitSkills() + " 个 Skill");
        }
        SkillRuntimeCatalogAccess access = new SkillRuntimeCatalogAccess(catalogPort);
        List<SkillRuntimeCandidate> candidates = publishedVersions.visible(request.projectId(), access.active(request.projectId()),new LinkedHashSet<>(request.requestedSkillIds()));
        SkillRuntimeSelection selection = new SkillHybridRetrieval(semanticScorePort,rerankPort,settings,applicability).select(
                request.projectId(),request.query(),candidates,new LinkedHashSet<>(request.requestedSkillIds()),Math.min(3,Math.min(settings.selectedLimit(),request.maxSelected())));
        selection=retainCurrent(selection,publishedVersions.visible(request.projectId(), access.active(request.projectId()),new LinkedHashSet<>(request.requestedSkillIds())));
        return selectionViews.result(selection);
    }

    public Result selectFrozen(FrozenRequest request) {
        if (request == null) throw new IllegalArgumentException("SKILL_FROZEN_SELECTION_REQUEST_REQUIRED");
        if(request.projectId().isBlank()) throw new IllegalArgumentException("SKILL_FROZEN_PROJECT_CONTEXT_REQUIRED");
        List<SkillRuntimeCandidate> candidates = request.catalogRefs().stream().map(candidateAssembler::fromView)
                .map(this::requireRoutingReady).toList();
        if(publishedVersions.usableFrozen(request.projectId(),candidates).size()!=candidates.size())
            throw new SkillRuntimeAccessRevokedException();
        if(candidates.stream().map(SkillRuntimeCandidate::skillId).distinct().count()!=candidates.size())
            throw new IllegalArgumentException("SKILL_FROZEN_DUPLICATE_ID");
        if(candidates.stream().anyMatch(c->!"GLOBAL".equals(c.scope()) && !request.projectId().equals(c.projectId())))
            throw new SecurityException("SKILL_FROZEN_PROJECT_CONTEXT_MISMATCH");
        SkillRuntimeCatalogAccess access=new SkillRuntimeCatalogAccess(catalogPort);
        candidates=access.retain(request.projectId(),candidates,false);
        var selection=new SkillHybridRetrieval(semanticScorePort,rerankPort,settings,applicability).select(
                request.projectId(),request.query(),candidates,Set.of(),Math.min(3,request.maxSelected()));
        selection=retainCurrent(selection,publishedVersions.usableFrozen(request.projectId(),access.retain(request.projectId(),candidates,false)));
        return selectionViews.frozenResult(selection);
    }

    private SkillRuntimeSelection retainCurrent(SkillRuntimeSelection selection,List<SkillRuntimeCandidate> current) {
        Map<String,SkillRuntimeCandidate> identities=new LinkedHashMap<>();current.forEach(c->identities.put(c.skillId(),c));
        java.util.function.Predicate<SkillRuntimeCandidate> retained=c->{
            var now=identities.get(c.skillId());
            return now!=null && now.scope().equals(c.scope()) && now.projectId().equals(c.projectId())
                    && now.version()==c.version() && now.skillHash().equals(c.skillHash()) && now.packageHash().equals(c.packageHash());
        };
        if(selection.selected().stream().anyMatch(r->r.explicit() && !retained.test(r.candidate())))
            throw new SecurityException("SKILL_RUNTIME_ACCESS_CHANGED_DURING_RETRIEVAL");
        return new SkillRuntimeSelection(selection.catalog().stream().filter(r->retained.test(r.candidate())).toList(),
                selection.selected().stream().filter(r->retained.test(r.candidate())).toList(),
                selection.suppressed().stream().filter(r->retained.test(r.rankedSkill().candidate())).toList(),current.size());
    }

    private SkillRuntimeCandidate requireRoutingReady(SkillRuntimeCandidate candidate) {
        if (candidate == null || !candidate.routingReady()) {
            String skillId = candidate == null ? "" : candidate.skillId();
            throw new IllegalStateException(
                    "SKILL_ROUTING_PROFILE_REQUIRED_AT_RUNTIME:" + skillId);
        }
        return candidate;
    }

    public record Request(String projectId,
                          String agentId,
                          List<String> requestedSkillIds,
                          String query,
                          int maxSelected) {
        public Request {
            projectId = text(projectId);
            agentId = text(agentId);
            requestedSkillIds = requestedSkillIds == null ? List.of() : requestedSkillIds.stream()
                    .map(SelectRuntimeSkillsQuery::normalizeId)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
            query = text(query);
            maxSelected = limit(maxSelected);
        }
    }

    public record FrozenRequest(String projectId, List<Map<String, Object>> catalogRefs,
                                String query,
                                int maxSelected) {
        public FrozenRequest {
            projectId = text(projectId);
            catalogRefs = copyMaps(catalogRefs);
            query = text(query);
            maxSelected = limit(maxSelected);
        }
    }

    public record Result(List<Map<String, Object>> catalogRefs,
                         List<Map<String, Object>> selectedRefs,
                         List<Map<String, Object>> suppressedRefs,
                         int activeCount,
                         int catalogCount,
                         int selectedCount) {
        public Result {
            catalogRefs = copyMaps(catalogRefs);
            selectedRefs = copyMaps(selectedRefs);
            suppressedRefs = copyMaps(suppressedRefs);
            if (activeCount < 0 || catalogCount < 0 || selectedCount < 0) {
                throw new IllegalArgumentException("SKILL_RUNTIME_SELECTION_COUNT_INVALID");
            }
            if (selectedCount != selectedRefs.size()) {
                throw new IllegalArgumentException("SKILL_RUNTIME_SELECTED_COUNT_MISMATCH");
            }
        }
    }

    private static int limit(int value) {
        if (value <= 0 || value > 100) throw new IllegalArgumentException("SKILL_SELECTION_LIMIT_INVALID");
        return value;
    }

    private static String normalizeId(Object value) {
        return SkillCatalogFingerprint.normalizeId(text(value));
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static List<Map<String, Object>> copyMaps(List<Map<String, Object>> source) {
        if (source == null || source.isEmpty()) return List.of();
        return source.stream()
                .map(item -> item == null ? Map.<String, Object>of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(item)))
                .toList();
    }
}
