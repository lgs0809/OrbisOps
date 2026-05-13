package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.MemoryCandidate;
import cn.lgs.orbisops.domain.memory.model.MemoryScope;
import cn.lgs.orbisops.domain.memory.model.MemoryType;

import java.util.regex.Pattern;

/** Pure classification strategy for explicit user memory requests. */
public final class MemoryClassificationPolicy {

    private static final Pattern SESSION = Pattern.compile("(这次|本次|当前会话|今天临时|临时记住)");
    private static final Pattern PROJECT_FACT = Pattern.compile("(索引|服务名|仓库|分支|环境|域名|组件|项目).{0,18}(是|为|叫|使用|位于)");
    private static final Pattern PROCEDURE = Pattern.compile("(先.+再|不能只|排查.+先|流程|步骤|规程)");
    private static final Pattern PREFERENCE = Pattern.compile("(回答|输出|格式|风格|称呼|语言|默认|优先|不要)");

    public MemoryCandidate classify(String query,
                                    String userId,
                                    String projectId,
                                    String sessionId) {
        String content = normalizeRememberContent(value(query));
        String normalized = normalizeContent(content);
        String logicalKey = logicalKey(content);
        if (SESSION.matcher(content).find()) {
            return candidate(MemoryType.SESSION_CONTEXT, MemoryScope.SESSION, sessionId,
                    content, normalized, logicalKey, 0.75D);
        }
        if (PROJECT_FACT.matcher(content).find() && !value(projectId).isBlank()) {
            return candidate(MemoryType.PROJECT_FACT, MemoryScope.PROJECT, projectId,
                    content, normalized, logicalKey, 0.55D);
        }
        if (PROCEDURE.matcher(content).find()) {
            return candidate(MemoryType.USER_WORKFLOW, MemoryScope.USER, userId,
                    content, normalized, logicalKey, 0.75D);
        }
        if (PREFERENCE.matcher(content).find()) {
            return candidate(MemoryType.USER_PREFERENCE, MemoryScope.USER, userId,
                    content, normalized, logicalKey, 0.75D);
        }
        if (!value(projectId).isBlank()) {
            return candidate(MemoryType.PROJECT_HINT, MemoryScope.PROJECT, projectId,
                    content, normalized, logicalKey, 0.75D);
        }
        return new MemoryCandidate(MemoryType.UNCLASSIFIED, null, "", content,
                normalized, logicalKey, false, 0.0D);
    }

    private MemoryCandidate candidate(MemoryType type,
                                      MemoryScope scope,
                                      String scopeId,
                                      String content,
                                      String normalized,
                                      String logicalKey,
                                      double confidence) {
        return new MemoryCandidate(type, scope, value(scopeId), content, normalized,
                logicalKey, false, confidence);
    }

    private String normalizeRememberContent(String query) {
        String content = query.replaceFirst("^(请)?(帮我)?(记住|记下来|别忘了)[，,:：\\s]*", "").trim();
        return content.replaceFirst("[，,；;]?\\s*(然后|并且随后|接着)\\s*(帮我|请)?\\s*(查|查询|排查|诊断|分析|修复|生成|执行).*$", "").trim();
    }

    private String normalizeContent(String content) {
        return value(content).replaceAll("\\s+", " ").trim();
    }

    private String logicalKey(String content) {
        String normalized = value(content).replaceAll("[，。；;].*$", "")
                .replaceAll("\\s+", " ").trim();
        return normalized.length() <= 96 ? normalized : normalized.substring(0, 96);
    }

    private String value(String input) {
        return input == null ? "" : input.trim();
    }
}
