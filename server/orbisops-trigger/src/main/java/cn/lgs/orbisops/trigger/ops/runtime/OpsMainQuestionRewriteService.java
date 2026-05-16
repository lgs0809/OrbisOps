package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.trigger.ops.OpsAgentLlmClient;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** Rewrites the latest turn into a standalone question before planning. */
@Service
public class OpsMainQuestionRewriteService {

    private final OpsAgentLlmClient llmClient;
    private final OpsMainQuestionRewriteProtocol protocol;
    private final OpsMainQuestionRewriteSettings settings;

    public OpsMainQuestionRewriteService(OpsAgentLlmClient llmClient) {
        this(llmClient, OpsMainQuestionRewriteSettings.defaults());
    }

    @Autowired
    public OpsMainQuestionRewriteService(
            OpsAgentLlmClient llmClient,
            OpsMainQuestionRewriteSettings settings) {
        this.llmClient = llmClient;
        this.protocol = new OpsMainQuestionRewriteProtocol();
        this.settings = settings == null
                ? OpsMainQuestionRewriteSettings.defaults()
                : settings;
    }

    OpsMainQuestionRewriteService(
            OpsAgentLlmClient llmClient,
            OpsMainQuestionRewriteProtocol protocol,
            OpsMainQuestionRewriteSettings settings) {
        this.llmClient = llmClient;
        this.protocol = protocol;
        this.settings = settings == null
                ? OpsMainQuestionRewriteSettings.defaults()
                : settings;
    }

    public RewriteResult rewrite(String query, String memoryContext) {
        String original = protocol.normalize(query);
        if (!settings.enabled()
                || !StringUtils.hasText(original)
                || !StringUtils.hasText(memoryContext)) {
            return RewriteResult.unchanged(
                    original,
                    "query rewrite skipped: disabled, blank query, or no conversation context");
        }
        JSONObject json = llmClient.chatJsonObject(
                "ops-main-query-rewriter",
                protocol.systemPrompt(),
                protocol.userPrompt(original, memoryContext, settings));
        return protocol.project(original, json, settings);
    }

    public record RewriteResult(
            String originalQuestion,
            String rewrittenQuestion,
            boolean changed,
            String reason,
            List<String> resolvedReferences) {

        static RewriteResult unchanged(String query, String reason) {
            return new RewriteResult(query, query, false, reason, List.of());
        }
    }
}
