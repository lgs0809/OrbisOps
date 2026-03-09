package cn.lgs.orbisops.trigger.application.audit;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.api.dto.OpsAuditRecordDTO;
import cn.lgs.orbisops.domain.audit.model.AnalysisAuditRecord;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class OpsAnalysisAuditMapper {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public AnalysisAuditRecord success(
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response,
            long durationMs) {
        if (response == null) throw new IllegalArgumentException("ANALYSIS_AUDIT_RESPONSE_REQUIRED");
        OpsAnalysisResponseDTO.OpsInvestigationPlanDTO plan = response.getInvestigationPlan();
        List<OpsAnalysisResponseDTO.InvestigationResultDTO> results =
                Optional.ofNullable(response.getInvestigationResults()).orElse(List.of());
        Map<String, String> statuses = results.stream()
                .filter(Objects::nonNull)
                .filter(result -> StringUtils.hasText(result.getSource()))
                .collect(Collectors.toMap(
                        result -> result.getSource().trim(),
                        result -> Optional.ofNullable(result.getStatus()).orElse("UNKNOWN"),
                        (left, right) -> right,
                        LinkedHashMap::new));
        return new AnalysisAuditRecord(
                response.getAnalysisId(),
                true,
                request == null ? null : request.getQuestion(),
                plan == null ? null : plan.getIntent(),
                response.getRangeMinutes(),
                response.getPromWindow(),
                response.getGeneratedAt(),
                durationMs,
                taskSources(plan == null ? null : plan.getTasks()),
                results.stream()
                        .filter(Objects::nonNull)
                        .filter(result -> StringUtils.hasText(result.getSource()))
                        .filter(result -> !"SKIPPED".equalsIgnoreCase(
                                Optional.ofNullable(result.getStatus()).orElse("")))
                        .map(result -> result.getSource().trim())
                        .distinct()
                        .toList(),
                taskSources(plan == null ? null : plan.getSkippedTasks()),
                statuses,
                Optional.ofNullable(response.getInsights()).orElse(List.of()).stream()
                        .filter(Objects::nonNull)
                        .map(OpsAnalysisResponseDTO.InsightDTO::getLevel)
                        .filter(StringUtils::hasText)
                        .map(String::trim)
                        .distinct()
                        .toList(),
                firstConclusion(response),
                "");
    }

    public AnalysisAuditRecord failure(
            OpsAgentRunRequestDTO request,
            Exception error,
            long durationMs) {
        return new AnalysisAuditRecord(
                "ops_failed_" + System.currentTimeMillis(),
                false,
                request == null ? null : request.getQuestion(),
                "",
                request == null ? null : request.getRangeMinutes(),
                request == null ? null : request.getPromWindow(),
                LocalDateTime.now().format(DATE_TIME_FORMATTER),
                durationMs,
                List.of(),
                List.of(),
                List.of(),
                Map.of(),
                List.of("ERROR"),
                "",
                error == null ? null : error.getMessage());
    }

    public List<OpsAuditRecordDTO> views(List<AnalysisAuditRecord> records) {
        return records == null ? List.of() : records.stream().map(this::view).toList();
    }

    public OpsAuditRecordDTO view(AnalysisAuditRecord record) {
        if (record == null) return null;
        return OpsAuditRecordDTO.builder()
                .analysisId(record.analysisId())
                .success(record.success())
                .question(record.question())
                .intent(record.intent())
                .rangeMinutes(record.rangeMinutes())
                .promWindow(record.promWindow())
                .generatedAt(record.generatedAt())
                .durationMs(record.durationMs())
                .selectedSources(record.selectedSources())
                .executedSources(record.executedSources())
                .skippedSources(record.skippedSources())
                .resultStatuses(record.resultStatuses())
                .insightLevels(record.insightLevels())
                .conclusion(record.conclusion())
                .errorMessage(record.errorMessage())
                .build();
    }

    private List<String> taskSources(List<OpsAnalysisResponseDTO.InvestigationTaskDTO> tasks) {
        return Optional.ofNullable(tasks).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .map(OpsAnalysisResponseDTO.InvestigationTaskDTO::getSource)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String firstConclusion(OpsAnalysisResponseDTO response) {
        return Optional.ofNullable(response.getInsights()).orElse(List.of()).stream()
                .filter(Objects::nonNull)
                .findFirst()
                .map(insight -> text(insight.getTitle()) + "：" + text(insight.getDetail()))
                .orElse("未生成系统规则结论");
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
