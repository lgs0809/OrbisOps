package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.domain.runtime.tool.model.ToolExposureSettings;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsToolOperationBoundaryProjectorTest {

    @Test
    void preservesStablePublicBoundaryProjection() {
        Map<String, Object> boundary = new OpsToolOperationBoundaryProjector()
                .project(ToolExposureSettings.defaults());

        assertEquals("analysis-only", boundary.get("mode"));
        assertEquals(Boolean.TRUE, boundary.get("analysisOnly"));
        assertEquals(Boolean.FALSE, boundary.get("aiCanExecuteRecovery"));
        assertEquals(Boolean.TRUE, boundary.get("aiCanCreateChangePackage"));
        assertTrue(boundary.containsKey("toolCapabilityPolicy"));
        assertFalse(boundary.isEmpty());
    }
}
