package cn.lgs.orbisops.domain.memory.service;

import cn.lgs.orbisops.domain.memory.model.ColdMemoryMessageSnapshot;
import cn.lgs.orbisops.domain.memory.model.MemoryExtractionDraft;
import cn.lgs.orbisops.domain.memory.model.MemoryItemCandidate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic long-term memory classification, normalization and filtering policy. */
public class MemoryExtractionPolicy {

    private static final Set<String> MEMORY_TYPES = Set.of(
            "USER_PREFERENCE",
            "USER_WORKFLOW",
            "USER_DOMAIN_FOCUS",
            "PROJECT_CONTEXT",
            "PROJECT_CONVENTION",
            "PROJECT_GLOSSARY");
    private static final Pattern STABLE_USER_CONTEXT_PATTERN = Pattern.compile(
            "以后|今后|从现在起|从今以后|默认|每次|长期|通常|习惯|偏好|我喜欢|我不喜欢|请记住|记住我|固定为|一律|总是");
    private static final Pattern PREFERENCE_PATTERN = Pattern.compile("以后|今后|从现在起|从今以后|默认|每次|长期|通常|习惯|偏好|喜欢|不喜欢|请记住|记住我|固定为|一律|总是");
    private static final Pattern WORKFLOW_PATTERN = Pattern.compile("先.+再|通常|习惯|流程|步骤|每次.+先|讨论方案|改文档|整理最终");
    private static final Pattern DOMAIN_FOCUS_PATTERN = Pattern.compile("长期关注|关注方向|主要关注|重点关注|方向是");
    private static final Pattern PROJECT_CONTEXT_PATTERN = Pattern.compile("项目目标|项目定位|目标是|不是.+而是|设计思想|整体边界|平台目标");
    private static final Pattern PROJECT_CONVENTION_PATTERN = Pattern.compile("统一称|统一叫|不要写|不叫|约定|规范|口径|边界是");
    private static final Pattern PROJECT_GLOSSARY_PATTERN = Pattern.compile("术语|指的是|意思是|定义为|叫做|是什么");
    private static final Pattern HARD_POLICY_PATTERN = Pattern.compile(
            "(?i)(权限|审批|沙箱|执行中心|硬策略|只读分析|只读检查|不要生成变更|不要生成执行包|不要生成\\s*change\\s*package|不要执行生产写|不要执行生产变更|生产写|生产变更|必须审计|必须验证|HTTP\\s*status|HTTP状态|状态码|401|403|404|409|500|policy|sandbox|change\\s*center|landing\\s*runtime|鉴权|脱敏|api[_-]?key|token|secret|password|passwd|access[_-]?key)");
    private static final Pattern TRANSIENT_EVIDENCE_PATTERN = Pattern.compile(
            "(?i)(traceId|trace_id|orderId|order_id|订单号|临时错误码|错误码|单次|Prometheus\\s*指标|指标值|ELK\\s*日志|日志命中|Redis\\s*key|redis-key|containerId|container_id|Docker\\s*container|工具结果|tool\\s*result|排障过程|排查步骤|SOP|run\\s*trace|metric\\s*value|log\\s*hit)");

    public List<MemoryExtractionDraft> ruleDrafts(ColdMemoryMessageSnapshot message) {
        if (message == null || !"user".equalsIgnoreCase(value(message.role())) || !hasText(message.content())) {
            return List.of();
        }
        String content = message.content();
        if (rejectAsLongTermMemory(content)) {
            return List.of();
        }
        List<MemoryExtractionDraft> drafts = new ArrayList<>();
        MemoryExtractionDraft project = projectDraft(content);
        if (project != null) {
            drafts.add(project);
        }
        MemoryExtractionDraft user = userDraft(content);
        if (user != null) {
            drafts.add(user);
        }
        return drafts;
    }

    public MemoryItemCandidate materialize(ColdMemoryMessageSnapshot message,
                                           MemoryExtractionDraft draft,
                                           String sourceHash,
                                           String createdAt) {
        if (message == null || draft == null || !hasText(draft.content())) {
            return null;
        }
        String memoryType = normalizeType(draft.memoryType());
        if (!hasText(memoryType)) {
            return null;
        }
        if (memoryType.startsWith("USER_") && !stableUserContext(message.content())) {
            return null;
        }
        String content = draft.content().trim();
        if (rejectAsLongTermMemory(content)) {
            return null;
        }
        Map<String, Object> sourceMetadata = message.metadata() == null ? Map.of() : message.metadata();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", text(draft.source(), "rule_extractor"));
        metadata.put("scopeType", normalizeScope(draft.scopeType(), memoryType));
        metadata.put("title", text(draft.title(), ""));
        metadata.put("summary", text(draft.summary(), ""));
        metadata.put("reason", text(draft.reason(), ""));
        metadata.put("projectId", sourceMetadata.getOrDefault("projectId", ""));
        metadata.put("agentId", sourceMetadata.get("agentId"));
        metadata.put("turn_index", sourceMetadata.get("turn_index"));
        metadata.put("created_at_epoch_ms", sourceMetadata.get("created_at_epoch_ms"));
        metadata.put("memory_status", sourceMetadata.getOrDefault("memory_status", "ACTIVE"));
        metadata.put("tokenEstimate", estimateTokens(message.content()));
        return new MemoryItemCandidate(
                value(message.sessionId()),
                value(message.userId()),
                memoryType,
                content,
                normalizeImportance(draft.importance()),
                text(draft.tagsJson(), "[]"),
                value(message.role()),
                value(sourceHash),
                metadata,
                value(createdAt));
    }

    public List<MemoryItemCandidate> distinctAndLimit(List<MemoryItemCandidate> candidates, int maxItems) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, maxItems);
        Set<String> seen = new LinkedHashSet<>();
        List<MemoryItemCandidate> result = new ArrayList<>();
        for (MemoryItemCandidate candidate : candidates) {
            if (candidate == null || rejectAsLongTermMemory(candidate.content())) {
                continue;
            }
            if (seen.add(candidate.dedupKey())) {
                result.add(candidate);
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    public String normalizeType(String type) {
        if (!hasText(type)) {
            return "";
        }
        String normalized = type.trim().toUpperCase();
        return MEMORY_TYPES.contains(normalized) ? normalized : "";
    }

    public String normalizeScope(String scopeType, String memoryType) {
        if (hasText(scopeType)) {
            String normalized = scopeType.trim().toUpperCase();
            if ("USER".equals(normalized) || "PROJECT".equals(normalized)) {
                return normalized;
            }
        }
        return hasText(memoryType) && memoryType.startsWith("USER_") ? "USER" : "PROJECT";
    }

    public BigDecimal normalizeImportance(BigDecimal importance) {
        if (importance == null) {
            return BigDecimal.valueOf(0.72D);
        }
        if (importance.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        if (importance.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return importance;
    }

    public boolean rejectAsLongTermMemory(String content) {
        return hasText(content)
                && (HARD_POLICY_PATTERN.matcher(content).find()
                || TRANSIENT_EVIDENCE_PATTERN.matcher(content).find());
    }

    private MemoryExtractionDraft projectDraft(String content) {
        if (PROJECT_GLOSSARY_PATTERN.matcher(content).find()) {
            return new MemoryExtractionDraft(
                    "PROJECT_GLOSSARY",
                    "项目术语：" + abbreviate(content, 240),
                    BigDecimal.valueOf(0.72D),
                    "[\"project\",\"glossary\"]",
                    "rule_extractor",
                    "PROJECT",
                    "项目术语",
                    abbreviate(content, 180),
                    "用户表达了项目术语或概念定义");
        }
        if (PROJECT_CONTEXT_PATTERN.matcher(content).find()) {
            return new MemoryExtractionDraft(
                    "PROJECT_CONTEXT",
                    "项目背景：" + abbreviate(content, 260),
                    BigDecimal.valueOf(0.78D),
                    "[\"project\",\"context\"]",
                    "rule_extractor",
                    "PROJECT",
                    "项目背景",
                    abbreviate(content, 180),
                    "用户表达了项目定位、目标或边界");
        }
        if (PROJECT_CONVENTION_PATTERN.matcher(content).find() && content.contains("项目")) {
            return new MemoryExtractionDraft(
                    "PROJECT_CONVENTION",
                    "项目约定：" + abbreviate(content, 240),
                    BigDecimal.valueOf(0.74D),
                    "[\"project\",\"convention\"]",
                    "rule_extractor",
                    "PROJECT",
                    "项目约定",
                    abbreviate(content, 180),
                    "用户表达了项目口径或软约定");
        }
        return null;
    }

    private MemoryExtractionDraft userDraft(String content) {
        if (!stableUserContext(content) || !PREFERENCE_PATTERN.matcher(content).find()) {
            return null;
        }
        String type = WORKFLOW_PATTERN.matcher(content).find() ? "USER_WORKFLOW" : "USER_PREFERENCE";
        if (DOMAIN_FOCUS_PATTERN.matcher(content).find()) {
            type = "USER_DOMAIN_FOCUS";
        }
        return new MemoryExtractionDraft(
                type,
                "用户语境：" + abbreviate(content, 220),
                BigDecimal.valueOf(0.72D),
                "[\"user\",\"context\"]",
                "rule_extractor",
                "USER",
                userMemoryTitle(type),
                abbreviate(content, 180),
                "用户表达了稳定偏好或工作习惯");
    }

    private String userMemoryTitle(String type) {
        return switch (type) {
            case "USER_WORKFLOW" -> "用户工作流";
            case "USER_DOMAIN_FOCUS" -> "用户关注方向";
            default -> "用户偏好";
        };
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength)) + "...";
    }

    private int estimateTokens(String text) {
        if (!hasText(text)) {
            return 0;
        }
        int chinese = 0;
        int other = 0;
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (Character.UnicodeScript.of(current) == Character.UnicodeScript.HAN) {
                chinese++;
            } else if (!Character.isWhitespace(current)) {
                other++;
            }
        }
        return chinese + Math.max(1, other / 4);
    }

    private String text(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean stableUserContext(String content) {
        return hasText(content) && STABLE_USER_CONTEXT_PATTERN.matcher(content).find();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
