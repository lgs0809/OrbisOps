package cn.lgs.orbisops.trigger.ops.runtime;

/** Typed bounds for rewriting one conversational query into a standalone question. */
public record OpsMainQuestionRewriteSettings(
        boolean enabled,
        int maxMemoryChars,
        int maxQuestionChars) {

    public OpsMainQuestionRewriteSettings {
        maxMemoryChars = maxMemoryChars < 1 || maxMemoryChars > 100_000
                ? 5_000
                : maxMemoryChars;
        maxQuestionChars = maxQuestionChars < 1 || maxQuestionChars > 20_000
                ? 1_200
                : maxQuestionChars;
    }

    public static OpsMainQuestionRewriteSettings defaults() {
        return new OpsMainQuestionRewriteSettings(true, 5_000, 1_200);
    }

    int memoryPromptLimit() {
        return Math.max(1_200, maxMemoryChars);
    }

    int questionPromptLimit() {
        return Math.max(200, maxQuestionChars);
    }

    int rewrittenQuestionLimit() {
        return Math.max(80, maxQuestionChars);
    }
}
