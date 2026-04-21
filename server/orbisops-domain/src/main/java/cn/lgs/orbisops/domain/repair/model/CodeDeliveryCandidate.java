package cn.lgs.orbisops.domain.repair.model;

public record CodeDeliveryCandidate(
        CodeDeliveryMode mode,
        String title,
        String baseBranch) {

    public CodeDeliveryCandidate {
        mode = mode == null ? CodeDeliveryMode.LOCAL_BRANCH : mode;
        title = value(title);
        baseBranch = value(baseBranch);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
