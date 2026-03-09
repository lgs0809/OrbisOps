package cn.lgs.orbisops.trigger.ops.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsMainQuestionRewriteSettingsTest {

    @Test
    void normalizesUnsafeLimitsAndKeepsPromptMinimums() {
        OpsMainQuestionRewriteSettings settings =
                new OpsMainQuestionRewriteSettings(true, 0, 40);

        assertTrue(settings.enabled());
        assertEquals(5_000, settings.maxMemoryChars());
        assertEquals(40, settings.maxQuestionChars());
        assertEquals(5_000, settings.memoryPromptLimit());
        assertEquals(200, settings.questionPromptLimit());
        assertEquals(80, settings.rewrittenQuestionLimit());
    }
}
