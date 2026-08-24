package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OpsPlatformRuntimeDefinitionSourceTest {
    @Test void resolvesOnlyTheExactPlatformVersionAndHash() {
        var source = new OpsPlatformRuntimeDefinitionSource();
        var definition = new OpsPlatformLandingRuntimeDefinitionFactory().create("project");
        assertTrue(source.find(definition.getAgentId(), definition.getVersion(), definition.getDefinitionHash()).isPresent());
        assertTrue(source.find("user-workflow", definition.getVersion(), definition.getDefinitionHash()).isEmpty());
        assertTrue(source.find(definition.getAgentId(), definition.getVersion() + 1, definition.getDefinitionHash()).isEmpty());
        assertTrue(source.find(definition.getAgentId(), definition.getVersion(), "forged").isEmpty());
    }
}
