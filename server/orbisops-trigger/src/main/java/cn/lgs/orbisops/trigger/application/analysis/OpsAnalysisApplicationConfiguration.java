package cn.lgs.orbisops.trigger.application.analysis;

import cn.lgs.orbisops.api.dto.OpsAgentRunRecordDTO;
import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.application.analysis.AnalysisRunApplicationService;
import cn.lgs.orbisops.application.analysis.AnalysisRunPort;
import cn.lgs.orbisops.application.analysis.AnalysisTaskAuditPort;
import cn.lgs.orbisops.application.analysis.AnalysisTaskFeedbackApplicationService;
import cn.lgs.orbisops.application.analysis.AnalysisTaskOutcomePort;
import cn.lgs.orbisops.application.analysis.AnalysisTaskQueryApplicationService;
import cn.lgs.orbisops.application.analysis.AnalysisTaskSupplementPort;
import cn.lgs.orbisops.application.analysis.AnalysisTaskTransactionPort;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisFeedbackRepository;
import cn.lgs.orbisops.domain.analysis.adapter.repository.IAnalysisTaskReadRepository;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class OpsAnalysisApplicationConfiguration {

    @Bean
    public AnalysisRunApplicationService<OpsAgentRunRequestDTO, OpsAgentRunRecordDTO, GraphEvent>
    analysisRunApplicationService(
            AnalysisRunPort<OpsAgentRunRequestDTO, OpsAgentRunRecordDTO, GraphEvent> port) {
        return new AnalysisRunApplicationService<>(port);
    }

    @Bean
    public AnalysisTaskQueryApplicationService analysisTaskQueryApplicationService(
            IAnalysisTaskReadRepository tasks,
            IAnalysisFeedbackRepository feedback,
            AnalysisTaskSupplementPort supplements) {
        return new AnalysisTaskQueryApplicationService(tasks, feedback, supplements);
    }

    @Bean
    public AnalysisTaskFeedbackApplicationService analysisTaskFeedbackApplicationService(
            AnalysisTaskQueryApplicationService queries,
            IAnalysisFeedbackRepository feedback,
            AnalysisTaskOutcomePort outcome,
            AnalysisTaskAuditPort audit,
            AnalysisTaskTransactionPort transactions) {
        return new AnalysisTaskFeedbackApplicationService(
                queries,
                feedback,
                outcome,
                audit,
                () -> "analysis-feedback-" + UUID.randomUUID(),
                Clock.systemUTC(),
                transactions);
    }
}
