package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.runtime.OpsToolExecutionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpsStructuredReportServiceTest {

    private final OpsStructuredReportService service =
            new OpsStructuredReportService(mock(OpsToolExecutionPolicy.class));

    @Test
    void shouldReportEvidenceGapInsteadOfHealthyConclusion() {
        OpsAnalysisResponseDTO response = new OpsAnalysisResponseDTO();
        response.setInvestigationResults(List.of(
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("prometheus")
                        .status("INSUFFICIENT")
                        .summary("query timed out")
                        .build()));

        Map<String, Object> report = service.compose(response);

        assertThat(report.get("severity")).isEqualTo("WARN");
        assertThat(report.get("summary")).isEqualTo("本次分析未获得足够的实时证据，不能据此判断系统正常。");
    }
    @Test
    @SuppressWarnings("unchecked")
    void shouldPreferAuthoritativeRuntimeEvidenceBindings() {
        OpsAnalysisResponseDTO response = new OpsAnalysisResponseDTO();
        response.setPrometheusStatus(OpsAnalysisResponseDTO.DataSourceStatusDTO.builder()
                .name("Prometheus")
                .available(true)
                .message("ok")
                .build());
        response.setAgentExecutionSteps(List.of(
                OpsAnalysisResponseDTO.AgentExecutionStepDTO.builder()
                        .nodeId("platform-ops-react")
                        .nodeType("AGENTSCOPE")
                        .agent("platform-ops-react")
                        .status("SUCCEEDED")
                        .summary("PROMETHEUS authoritative datasource query succeeded")
                        .sourceType("PROMETHEUS")
                        .resultId("tool-result-auth")
                        .evidenceId("evidence-auth")
                        .outputHash("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
                        .verified(true)
                        .build()));
        response.setInvestigationResults(List.of(
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("investigate")
                        .status("FOUND")
                        .summary("model synthesis")
                        .evidence(List.of("generic synthesis"))
                        .confidence(0.72D)
                        .build()));

        Map<String, Object> report = service.compose(response);
        Map<String, Object> diagnosis = (Map<String, Object>) report.get("diagnosis");
        List<Map<String, Object>> facts = (List<Map<String, Object>>) diagnosis.get("facts");
        List<Map<String, Object>> sourceStatus = (List<Map<String, Object>>) diagnosis.get("sourceStatus");

        assertThat(facts).singleElement().satisfies(fact -> {
            assertThat(fact.get("factId")).isEqualTo("fact-ev-prometheus-1-1");
            List<Map<String, Object>> refs = (List<Map<String, Object>>) fact.get("evidenceRefs");
            assertThat(refs).singleElement().satisfies(ref -> {
                assertThat(ref.get("resultId")).isEqualTo("tool-result-auth");
                assertThat(ref.get("outputHash")).isEqualTo("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");
            });
        });
        assertThat(sourceStatus).anySatisfy(source -> {
            if ("prometheus".equals(source.get("sourceId"))) {
                assertThat(source.get("queryStatus")).isEqualTo("SUCCEEDED");
            }
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldUseReactOutcomeForTypedActionVerificationAndPackageProjection() {
        OpsAnalysisResponseDTO response = new OpsAnalysisResponseDTO();
        response.setAgentExecutionSteps(List.of(
                OpsAnalysisResponseDTO.AgentExecutionStepDTO.builder()
                        .eventType("REACT_OUTCOME")
                        .status("SUCCEEDED")
                        .outcome(Map.of(
                                "requiresAction", true,
                                "verificationStatus", "INSUFFICIENT",
                                "abstained", false,
                                "evidenceCompleteness", "PARTIAL"))
                        .build(),
                OpsAnalysisResponseDTO.AgentExecutionStepDTO.builder()
                        .eventType("CHANGE_PACKAGE_PREPARED")
                        .status("SUCCEEDED")
                        .changePackageBehavior("PROPOSE_ONLY")
                        .build()));
        response.setInvestigationResults(List.of(
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("investigate")
                        .status("FOUND")
                        .evidence(List.of("model narrative is not datasource evidence"))
                        .build()));

        Map<String, Object> report = service.compose(response);
        Map<String, Object> diagnosis = (Map<String, Object>) report.get("diagnosis");

        assertThat(report).containsEntry("verificationStatus", "INSUFFICIENT")
                .containsEntry("changePackageBehavior", "PROPOSE_ONLY");
        assertThat(diagnosis).containsEntry("requiresAction", true)
                .containsEntry("evidenceCompleteness", "INSUFFICIENT");
        assertThat((List<Map<String, Object>>) diagnosis.get("facts")).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExposeUnifiedDiagnosisWithEvidenceBindingsAndSourceStates() {
        OpsAnalysisResponseDTO response = new OpsAnalysisResponseDTO();
        response.setPrometheusStatus(OpsAnalysisResponseDTO.DataSourceStatusDTO.builder()
                .name("Prometheus")
                .available(true)
                .message("ok")
                .build());
        response.setElasticsearchStatus(OpsAnalysisResponseDTO.DataSourceStatusDTO.builder()
                .name("Elasticsearch")
                .available(false)
                .message("connection refused")
                .build());
        response.setInvestigationResults(List.of(
                OpsAnalysisResponseDTO.InvestigationResultDTO.builder()
                        .source("prometheus")
                        .status("FOUND")
                        .summary("5xx rate elevated")
                        .evidence(List.of("5xx_rate=0.12"))
                        .confidence(0.92D)
                        .build()));

        Map<String, Object> report = service.compose(response);
        Map<String, Object> diagnosis = (Map<String, Object>) report.get("diagnosis");
        List<Map<String, Object>> facts = (List<Map<String, Object>>) diagnosis.get("facts");
        List<Map<String, Object>> sourceStatus = (List<Map<String, Object>>) diagnosis.get("sourceStatus");

        assertThat(diagnosis.get("evidenceCompleteness")).isEqualTo("COMPLETE");
        assertThat(diagnosis.get("confidence")).isEqualTo("HIGH");
        assertThat(facts).hasSize(1);
        assertThat((List<Map<String, Object>>) facts.get(0).get("evidenceRefs"))
                .singleElement()
                .satisfies(ref -> {
                    assertThat(ref.get("evidenceRef")).isNotNull();
                    assertThat(ref.get("resultId")).isNotNull();
                    assertThat(String.valueOf(ref.get("outputHash"))).hasSize(64);
                });
        assertThat(sourceStatus).anySatisfy(source -> {
            if ("prometheus".equals(source.get("sourceId"))) {
                assertThat(source.get("queryStatus")).isEqualTo("SUCCEEDED");
                assertThat(source.get("assessment")).isEqualTo("UNKNOWN");
                assertThat(source.get("state")).isEqualTo("UNKNOWN");
            }
        });
        assertThat(sourceStatus).anySatisfy(source -> {
            if ("elasticsearch".equals(source.get("sourceId"))) {
                assertThat(source.get("queryStatus")).isEqualTo("FAILED");
                assertThat(source.get("assessment")).isEqualTo("UNKNOWN");
                assertThat(source.get("state")).isEqualTo("UNAVAILABLE");
            }
        });
    }

}
