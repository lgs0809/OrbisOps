package cn.lgs.orbisops.application.skill;

import cn.lgs.orbisops.domain.shared.service.CanonicalObjectHasher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** One proposal by default; explicitly requested comparison directions remain bounded and audited. */
public final class SkillEvolutionMultiCandidateAuthoringService {

    private static final int DEFAULT_BUDGET = 1;

    private final SkillEvolutionAuthoringPort port;

    public SkillEvolutionMultiCandidateAuthoringService(
            SkillEvolutionAuthoringPort port) {
        if (port == null) throw new IllegalArgumentException("SKILL_AUTHORING_PORT_REQUIRED");
        this.port = port;
    }

    public List<SkillEvolutionAuthoredCandidateOption> author(
            Map<String, Object> input,
            int requestedBudget) {
        Map<String, Object> safeInput = input == null ? Map.of() : Map.copyOf(input);
        int budget = boundedBudget(requestedBudget);
        List<SkillAuthoringDirection> directions = directions(safeInput, budget);
        Map<String, Object> request = new LinkedHashMap<>(safeInput);
        request.put("candidateDirections", directions.stream().map(Enum::name).toList());
        request.put("candidateBudget", budget);
        String inputHash = CanonicalObjectHasher.sha256(safeInput);

        List<SkillEvolutionAuthoredCandidate> authored = port.authorCandidates(
                Map.copyOf(request), budget);
        List<SkillEvolutionAuthoredCandidate> raw = authored == null
                ? List.of() : authored;
        List<SkillEvolutionAuthoredCandidateOption> result = new ArrayList<>();
        for (int index = 0; index < budget; index++) {
            SkillAuthoringDirection direction = directions.get(index);
            SkillEvolutionAuthoredCandidate candidate = index < raw.size()
                    ? raw.get(index) : SkillEvolutionAuthoredCandidate.from(Map.of());
            if (candidate == null) candidate = SkillEvolutionAuthoredCandidate.from(Map.of());
            result.add(audited(candidate, safeInput, direction, budget, index, inputHash));
        }
        return List.copyOf(result);
    }

    public int boundedBudget(int requestedBudget) {
        if (requestedBudget <= 0) return DEFAULT_BUDGET;
        return Math.max(1, Math.min(4, requestedBudget));
    }

    private SkillEvolutionAuthoredCandidateOption audited(
            SkillEvolutionAuthoredCandidate candidate,
            Map<String, Object> input,
            SkillAuthoringDirection direction,
            int budget,
            int index,
            String inputHash) {
        Map<String, Object> payload = new LinkedHashMap<>(candidate.payload());
        String modelId = firstText(
                payload.get("modelId"), payload.get("authoringModel"),
                input.get("modelId"), input.get("authoringModel"), "unspecified-model");
        String promptVersion = firstText(
                payload.get("promptVersion"), payload.get("authoringPromptVersion"),
                input.get("promptVersion"), input.get("authoringPromptVersion"), "legacy-v1");
        long seed = longValue(firstValue(
                payload.get("seed"), payload.get("authoringSeed"),
                input.get("seed"), input.get("authoringSeed"), 0L));
        SkillEvolutionAuthoringAudit audit = new SkillEvolutionAuthoringAudit(
                modelId, promptVersion, seed, inputHash, budget, index, direction);
        payload.put("candidateDirection", direction.name());
        payload.put("authoringAudit", Map.of(
                "modelId", audit.modelId(),
                "promptVersion", audit.promptVersion(),
                "seed", audit.seed(),
                "inputHash", audit.inputHash(),
                "candidateBudget", audit.candidateBudget(),
                "candidateIndex", audit.candidateIndex(),
                "direction", audit.direction().name()));
        return new SkillEvolutionAuthoredCandidateOption(
                SkillEvolutionAuthoredCandidate.from(Map.copyOf(payload)), audit);
    }

    private List<SkillAuthoringDirection> directions(
            Map<String, Object> input,
            int budget) {
        // Normal evolution selects the operation from evidence; a legacy patch direction
        // must not preselect the decision before the author reviews related methods.
        if (budget == 1) return List.of(SkillAuthoringDirection.MAINTENANCE_REVIEW);
        Set<SkillAuthoringDirection> ordered = new LinkedHashSet<>();
        for (String layer : diagnosisLayers(input.get("defectDiagnoses"))) {
            switch (layer) {
                case "ROUTING" -> ordered.add(SkillAuthoringDirection.ROUTING_PATCH);
                case "EVIDENCE" -> ordered.add(SkillAuthoringDirection.EVIDENCE_PATCH);
                case "PROCEDURE", "TOOL_USAGE", "RECOVERY", "OUTPUT_CONTRACT",
                        "ARTIFACT", "SECURITY_BOUNDARY" ->
                        ordered.add(SkillAuthoringDirection.PROCEDURE_PATCH);
                default -> {
                }
            }
        }
        ordered.add(SkillAuthoringDirection.MINIMAL_PATCH);
        ordered.add(SkillAuthoringDirection.ROUTING_PATCH);
        ordered.add(SkillAuthoringDirection.PROCEDURE_PATCH);
        ordered.add(SkillAuthoringDirection.EVIDENCE_PATCH);
        ordered.add(SkillAuthoringDirection.COMPRESSION_PATCH);
        ordered.add(SkillAuthoringDirection.SPLIT_PROPOSAL);
        ordered.add(SkillAuthoringDirection.MERGE_PROPOSAL);
        return ordered.stream().limit(budget).toList();
    }

    private List<String> diagnosisLayers(Object value) {
        if (!(value instanceof Iterable<?> iterable)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : iterable) {
            if (item instanceof Map<?, ?> map) {
                result.add(text(map.get("layer")).toUpperCase(Locale.ROOT));
            } else {
                result.add(text(item).toUpperCase(Locale.ROOT));
            }
        }
        return List.copyOf(result);
    }

    private Object firstValue(Object... values) {
        if (values == null) return null;
        for (Object value : values) {
            if (value != null && !text(value).isBlank()) return value;
        }
        return null;
    }

    private String firstText(Object... values) {
        Object value = firstValue(values);
        return value == null ? "" : text(value);
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
