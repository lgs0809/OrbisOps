package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagFeedbackRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring property binding boundary for RAG feedback persistence. */
@Configuration
public class RagFeedbackConfiguration {

    @Bean
    public RagFeedbackSettings ragFeedbackSettings(
            @Value("${orbisops.rag.feedback.auto-init:true}") boolean autoInit) {
        return new RagFeedbackSettings(autoInit);
    }

    @Bean
    public OpsRagFeedbackManagementAssembly opsRagFeedbackManagementAssembly(
            IRagFeedbackRepository repository,
            OpsRagQualityEvalManagementAssembly qualityAssembly,
            RagFeedbackSettings settings) {
        return OpsRagFeedbackManagementAssembly.create(
                repository,
                qualityAssembly,
                settings);
    }
}
