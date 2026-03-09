package cn.lgs.orbisops.trigger.ops.toolset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpsToolOutputBudget {

    @Builder.Default
    private int maxBytes = 32 * 1024;
    @Builder.Default
    private int maxLines = 400;
    @Builder.Default
    private int maxRows = 200;
    @Builder.Default
    private int maxPoints = 1000;
    @Builder.Default
    private int maxTimeRangeMinutes = 60;
}
