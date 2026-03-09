package cn.lgs.orbisops.domain.memory.service;

/** Domain policy for resolving the runtime memory scene from explicit metadata and query intent signals. */
public class MemorySceneClassificationPolicy {

    public static final String OPS_TROUBLESHOOTING = "OPS_TROUBLESHOOTING";
    public static final String DOCUMENT_WRITING = "DOCUMENT_WRITING";
    public static final String DESIGN_DISCUSSION = "DESIGN_DISCUSSION";
    public static final String CHAT = "CHAT";

    public String classify(String explicitScene, String taskType, String query) {
        if (hasText(explicitScene)) return explicitScene.trim();
        if (hasText(taskType)) return taskType.trim();
        String text = value(query);
        if (containsAny(text, "告警", "故障", "排障", "错误", "异常")) {
            return OPS_TROUBLESHOOTING;
        }
        if (containsAny(text, "文档", "报告", "简历", "润色")) {
            return DOCUMENT_WRITING;
        }
        if (containsAny(text, "方案", "设计", "规划")) {
            return DESIGN_DISCUSSION;
        }
        return CHAT;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) return true;
        }
        return false;
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
