package cn.lgs.orbisops.trigger.ops.runtime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpsChatSessionUpdateRequest {

    private String title;
    private String status;
    private Long expectedStateVersion;

    @Builder.Default
    private Map<String, Object> metadata = new LinkedHashMap<>();

}
