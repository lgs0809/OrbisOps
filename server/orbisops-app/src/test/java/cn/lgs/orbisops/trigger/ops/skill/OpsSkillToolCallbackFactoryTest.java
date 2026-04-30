package cn.lgs.orbisops.trigger.ops.skill;

import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.SkillsTool;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpsSkillToolCallbackFactoryTest {

    @Test
    void concurrentSchemaBuildsAreSerializedAcrossFactoryInstances() {
        SkillsTool.Skill skill = mock(SkillsTool.Skill.class);
        when(skill.name()).thenReturn("order-recovery");
        when(skill.toXml()).thenReturn("<skill name=\"order-recovery\" />");

        IntStream.range(0, 32).parallel().forEach(index -> {
            ToolCallback callback = new OpsSkillToolCallbackFactory()
                    .build(List.of(skill))
                    .orElseThrow();
            assertEquals("Skill", callback.getToolDefinition().name());
        });
    }

    @Test
    void returnsEmptyForNoSkillsAndBuildsStableSkillToolProtocol() {
        OpsSkillToolCallbackFactory factory = new OpsSkillToolCallbackFactory();
        SkillsTool.Skill skill = mock(SkillsTool.Skill.class);
        when(skill.name()).thenReturn("order-recovery");
        when(skill.toXml()).thenReturn("<skill name=\"order-recovery\" />");

        Optional<ToolCallback> callback = factory.build(List.of(skill));

        assertTrue(factory.build(List.of()).isEmpty());
        assertTrue(callback.isPresent());
        assertEquals("Skill", callback.orElseThrow().getToolDefinition().name());
        assertTrue(callback.orElseThrow().getToolDefinition().description()
                .contains("order-recovery"));
        assertTrue(callback.orElseThrow().getToolDefinition().description()
                .contains("do not treat the skill itself as runtime evidence"));
    }
}
