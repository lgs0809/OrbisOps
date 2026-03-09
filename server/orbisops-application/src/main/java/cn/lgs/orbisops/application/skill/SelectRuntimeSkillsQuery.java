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

    public SelectRuntimeSkillsQuery(SkillCatalogPort catalogPort,
                                    SkillSemanticScorePort semanticScorePort,
                                    SkillRuntimeSelectionSettings settings) {
        this(catalogPort, semanticScorePort, (query, candidates) -> Map.of(), settings,
                new SkillRuntimeCandidateAssembler(), new SkillRuntimeSelectionPolicy(),
                new SkillRuntimeRecallPolicy(),
                new SkillRuntimeSelectionViewMapper(new SkillBoundReferenceMapper()));
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
    }

    public Result select(Request request) {
        if (request == null) throw new IllegalArgumentException("SKILL_RUNTIME_SELECTION_REQUEST_REQUIRED");
        if (request.requestedSkillIds().size() > settings.maxExplicitSkills()) {
            throw new IllegalArgumentException("TOO_MANY_EXPLICIT_SKILLS: 最多显式选择 "
                    + settings.maxExplicitSkills() + " 个 Skill");
        }
        List<SkillRuntimeCandidate> candidates = runtimeCandidates(request.projectId());
        List<SkillRuntimeCandidate> recalled = recallPolicy.recall(
                request.query(), candidates, new LinkedHashSet<>(request.requestedSkillIds()),
                settings.semanticRecallLimit());
        Map<String, Double> semanticScores = semanticScorePort.scores(request.query(), recalled);
        Map<String, Double> rerankScores = settings.llmRerankEnabled()
                ? rerankPort.scores(
                        request.query(),
                        recalled.stream().limit(settings.llmRerankCandidateLimit()).toList())
                : Map.of();
        Map<String, Double> retrievalScores = retrievalScores(
                semanticScores, rerankScores, settings.llmRerankWeight());
        int selectedLimit = Math.min(settings.selectedLimit(), request.maxSelected());
        SkillRuntimeSelection selection = selectionPolicy.select(
                new SkillRuntimeSelectionRequest(
                        request.projectId(), request.agentId(), request.query(),
                        new LinkedHashSet<>(request.requestedSkillIds()),
                        settings.catalogCandidateLimit(), selectedLimit, settings.maxExplicitSkills(),
                        settings.minRelevanceScore(), settings.similarSuppressionThreshold(), settings.semanticWeight(),
                        settings.semanticRecallLimit(), settings.categoryBoost(), settings.negativePenaltyWeight()),
                candidates, retrievalScores);
        return selectionViews.result(selection);
    }

    public Result selectFrozen(FrozenRequest request) {
        if (request == null) throw new IllegalArgumentException("SKILL_FROZEN_SELECTION_REQUEST_REQUIRED");
        List<SkillRuntimeCandidate> candidates = request.catalogRefs().stream()
                .map(candidateAssembler::fromView)
                .map(this::requireRoutingReady)
                .toList();
        List<SkillRuntimeSelection.RankedSkill> ranked = selectionPolicy.rankFrozenCatalog(
                candidates, request.query(), request.maxSelected());
        return selectionViews.frozenResult(request.catalogRefs(), ranked);
    }

    private SkillRuntimeCandidate requireRoutingReady(SkillRuntimeCandidate candidate) {
        if (candidate == null || !candidate.routingReady()) {
            String skillId = candidate == null ? "" : candidate.skillId();
            throw new IllegalStateException(
                    "SKILL_ROUTING_PROFILE_REQUIRED_AT_RUNTIME:" + skillId);
        }
        return candidate;
    }

    private List<SkillRuntimeCandidate> runtimeCandidates(String projectId) {
        Map<String, SkillRuntimeCandidate> unique = new LinkedHashMap<>();
        Set<String> projectOwnedSkillIds = new LinkedHashSet<>();
        if (!projectId.isBlank()) {
            for (SkillCatalogSnapshot skill : catalogPort.listProjectEntries(projectId)) {
                String skillId = normalizeId(skill == null ? null : skill.skillId());
                if (skillId.isBlank()) continue;
                projectOwnedSkillIds.add(skillId);
                if (skill.activeAtUse() && skill.runtimeCandidate().routingReady()) {
                    unique.put(skillId, skill.runtimeCandidate());
                }
            }
        }
        for (SkillCatalogSnapshot skill : catalogPort.listGlobalEntries()) {
            String skillId = normalizeId(skill == null ? null : skill.skillId());
            if (skillId.isBlank()
                    || projectOwnedSkillIds.contains(skillId)
                    || !skill.activeAtUse()
                    || !skill.runtimeCandidate().routingReady()) continue;
            unique.putIfAbsent(skillId, skill.runtimeCandidate());
        }
        return List.copyOf(unique.values());
    }

    private Map<String, Double> retrievalScores(Map<String, Double> semantic,
                                                Map<String, Double> rerank,
                                                double rerankWeight) {
        Map<String, Double> safeSemantic = semantic == null ? Map.of() : semantic;
        Map<String, Double> safeRerank = rerank == null ? Map.of() : rerank;
        if (safeRerank.isEmpty()) return safeSemantic;
        Map<String, Double> combined = new LinkedHashMap<>();
        Set<String> ids = new LinkedHashSet<>(safeSemantic.keySet());
        ids.addAll(safeRerank.keySet());
        for (String id : ids) {
            Double semanticScore = validScore(safeSemantic.get(id));
            Double rerankScore = validScore(safeRerank.get(id));
            if (semanticScore == null && rerankScore == null) continue;
            double score = semanticScore == null ? rerankScore
                    : rerankScore == null ? semanticScore
                    : semanticScore * (1D - rerankWeight) + rerankScore * rerankWeight;
            combined.put(id, Math.max(0D, Math.min(1D, score)));
        }
        return Map.copyOf(combined);
    }

    private Double validScore(Double value) {
        return value == null || value.isNaN() || value.isInfinite()
                ? null : Math.max(0D, Math.min(1D, value));
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

    public record FrozenRequest(List<Map<String, Object>> catalogRefs,
                                String query,
                                int maxSelected) {
        public FrozenRequest {
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
