package cn.lgs.orbisops.trigger.application.statistics;

import cn.lgs.orbisops.api.dto.DataStatisticsResponseDTO;
import cn.lgs.orbisops.domain.statistics.model.DataStatisticsSnapshot;
import org.springframework.stereotype.Component;

@Component
public class OpsDataStatisticsMapper {

    public DataStatisticsResponseDTO response(DataStatisticsSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("DATA_STATISTICS_SNAPSHOT_REQUIRED");
        return DataStatisticsResponseDTO.builder()
                .activeAgentCount(snapshot.activeAgentCount())
                .mcpToolCount(snapshot.mcpToolCount())
                .ragOrderCount(snapshot.ragOrderCount())
                .modelCount(snapshot.modelCount())
                .todayRequestCount(snapshot.todayRequestCount())
                .successRate(snapshot.successRate())
                .runningTaskCount(snapshot.runningTaskCount())
                .build();
    }
}
