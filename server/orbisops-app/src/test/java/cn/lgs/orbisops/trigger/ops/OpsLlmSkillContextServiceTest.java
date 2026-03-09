package cn.lgs.orbisops.trigger.ops;

import cn.lgs.orbisops.trigger.ops.skill.OpsSkillToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OpsLlmSkillContextServiceTest {

    @Test
    void disabledOrMissingSkillsKeepOriginalPromptWithoutLoadingProvider() {
        OpsSkillToolProvider provider = mock(OpsSkillToolProvider.class);
        OpsLlmSkillContextService service = new OpsLlmSkillContextService(() -> provider);
        String prompt = "system";

        assertSame(prompt, service.withLazyContext(prompt, List.of("skill-a"), false, 1000));
        assertSame(prompt, service.withLazyContext(prompt, List.of(), true, 1000));
        assertSame(prompt, service.withEagerContext(prompt, List.of(), 1000));
        assertTrue(service.skillTool(List.of()).isEmpty());
        verifyNoInteractions(provider);
    }

    @Test
    void lazyContextAppendsBoundSkillSummaryAndUsesConfiguredLimit() {
        OpsSkillToolProvider provider = mock(OpsSkillToolProvider.class);
        when(provider.renderSkillSummaryContext(List.of("skill-a"), 1200))
                .thenReturn("- skill-a: summary");
        OpsLlmSkillContextService service = new OpsLlmSkillContextService(() -> provider);

        String result = service.withLazyContext(
                "system",
                List.of("skill-a"),
                true,
                1200);

        assertTrue(result.startsWith("system"));
        assertTrue(result.contains("### 可用 Skill 与使用边界"));
        assertTrue(result.contains("- skill-a: summary"));
        assertTrue(result.contains("Skill 是操作规程，不是事实证据"));
        verify(provider).renderSkillSummaryContext(List.of("skill-a"), 1200);
    }

    @Test
    void blankOrFailedLazyRenderingKeepsOriginalPrompt() {
        OpsSkillToolProvider blankProvider = mock(OpsSkillToolProvider.class);
        when(blankProvider.renderSkillSummaryContext(anyCollection(), anyInt()))
                .thenReturn(" ");
        String prompt = "system";
        assertSame(prompt, new OpsLlmSkillContextService(() -> blankProvider)
                .withLazyContext(prompt, List.of("skill-a"), true, 1000));

        OpsSkillToolProvider failedProvider = mock(OpsSkillToolProvider.class);
        when(failedProvider.renderSkillSummaryContext(anyCollection(), anyInt()))
                .thenThrow(new IllegalStateException("load failed"));
        assertSame(prompt, new OpsLlmSkillContextService(() -> failedProvider)
                .withLazyContext(prompt, List.of("skill-a"), true, 1000));
    }

    @Test
    void eagerContextEnforcesLegacyMinimumAndAppendsFullSkillBody() {
        OpsSkillToolProvider provider = mock(OpsSkillToolProvider.class);
        when(provider.renderSkillContext(List.of("skill-a"), 2000))
                .thenReturn("## Skill: skill-a\nbody");
        OpsLlmSkillContextService service = new OpsLlmSkillContextService(() -> provider);

        String result = service.withEagerContext(
                "system",
                List.of("skill-a"),
                500);

        assertTrue(result.contains("### 本次已加载 Skill 正文"));
        assertTrue(result.contains("## Skill: skill-a"));
        verify(provider).renderSkillContext(List.of("skill-a"), 2000);
    }

    @Test
    void skillToolUsesBoundNamesAndMissingProviderReturnsEmpty() {
        ToolCallback callback = mock(ToolCallback.class);
        OpsSkillToolProvider provider = mock(OpsSkillToolProvider.class);
        when(provider.buildSkillToolCallback(List.of("skill-a")))
                .thenReturn(Optional.of(callback));
        OpsLlmSkillContextService service = new OpsLlmSkillContextService(() -> provider);

        assertSame(callback, service.skillTool(List.of("skill-a")).orElseThrow());
        verify(provider).buildSkillToolCallback(List.of("skill-a"));
        assertTrue(new OpsLlmSkillContextService(() -> null)
                .skillTool(List.of("skill-a"))
                .isEmpty());
    }

    @Test
    void normalizedNamesTrimDeduplicatePreserveOrderAndRemainImmutable() {
        OpsLlmSkillContextService service = new OpsLlmSkillContextService(() -> null);

        List<String> names = service.normalizedNames(List.of(
                " skill-b ",
                "",
                "skill-a",
                "skill-b",
                "   "));

        assertEquals(List.of("skill-b", "skill-a"), names);
        assertThrows(UnsupportedOperationException.class, () -> names.add("skill-c"));
        assertTrue(service.normalizedNames(null).isEmpty());
        assertFalse(names.isEmpty());
    }
}
