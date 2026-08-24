package cn.lgs.orbisops.trigger.application.episode;

import cn.lgs.orbisops.application.episode.*;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import cn.lgs.orbisops.application.project.AuthorizeProjectAccessUseCase;
import cn.lgs.orbisops.application.security.AdminUserCatalogPort;
import cn.lgs.orbisops.trigger.ops.skill.OpsSkillAuthoringModelClient;
import cn.lgs.orbisops.trigger.ops.OpsSkillEvolutionSettings;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryFacadeSettings;
import cn.lgs.orbisops.trigger.ops.runtime.OpsMemoryExtractionSettings;
import cn.lgs.orbisops.trigger.ops.runtime.OpsContextCompressionSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

@Configuration
public class OpsBackgroundModelRecoveryConfiguration {
    @Bean public cn.lgs.orbisops.application.security.BackgroundTaskOwnerAccessPort backgroundTaskOwnerAccessPolicy(
            AuthorizeProjectAccessUseCase access,AdminUserCatalogPort accounts) {
        return new cn.lgs.orbisops.application.security.BackgroundTaskOwnerAccessPolicy(access,accounts);
    }
    @Bean public BackgroundModelRecoveryService backgroundModelRecoveryService(BackgroundModelRecoveryPort store,
            TaskEpisodeModelPort episodes, OpsSkillAuthoringModelClient authoring, ModelAvailabilityPort availability,
            cn.lgs.orbisops.application.security.BackgroundTaskOwnerAccessPort owners,
            @Value("${orbisops.skill-evolution.enabled:true}") boolean skillEnabled,
            OpsSkillEvolutionSettings evolution, OpsMemoryFacadeSettings memory,
            OpsMemoryExtractionSettings extraction, OpsContextCompressionSettings compression,
            @Value("${orbisops.chat.memory.semantic-enabled:false}") boolean semantic) {
        return new BackgroundModelRecoveryService(store,candidate -> switch(candidate.family()) {
            case EPISODE_TURN, EPISODE_CONSOLIDATION -> episodes.available();
            case SKILL_REVIEW -> skillEnabled && episodes.available();
            case SKILL_EVOLUTION -> skillEnabled && evolution.workerEnabled() && authoring.available();
            case MEMORY -> memory.enabled() && switch(candidate.variant()) {
                case "EXTRACTION" -> extraction.enabled() && extraction.modelEnabled() && availability.isChatAvailable();
                case "COMPRESSION", "COMPRESSION_MODEL" -> compression.enabled() && compression.modelEnabled() && availability.isChatAvailable();
                case "SEMANTIC" -> semantic && availability.isEmbeddingConfigured();
                default -> false;
            };
        },owners,Clock.systemUTC());
    }
}
