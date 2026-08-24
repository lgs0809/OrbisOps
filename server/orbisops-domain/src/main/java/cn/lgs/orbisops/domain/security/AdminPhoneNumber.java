package cn.lgs.orbisops.domain.security;

/** Canonical mainland mobile login identity. Empty values preserve legacy accounts. */
public final class AdminPhoneNumber {

    private AdminPhoneNumber() {
    }

    public static String optional(String submitted) {
        if (submitted == null || submitted.isBlank()) {
            return null;
        }
        return required(submitted);
    }

    public static String required(String submitted) {
        String value = submitted == null ? "" : submitted.trim();
        if (value.startsWith("+86")) {
            value = value.substring(3).trim();
        } else if (value.length() == 13 && value.startsWith("86")) {
            value = value.substring(2);
        }
        if (!value.matches("1[3-9][0-9]{9}")) {
            throw new IllegalArgumentException("请输入有效的中国大陆手机号");
        }
        return value;
    }
}
