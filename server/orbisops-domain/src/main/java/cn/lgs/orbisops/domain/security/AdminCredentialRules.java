package cn.lgs.orbisops.domain.security;

/** Domain rules for account credential ingress. */
public record AdminCredentialRules(
        int minimumLength,
        boolean preencodedAllowed) {

    public AdminCredentialRules {
        minimumLength = Math.max(8, Math.min(minimumLength, 256));
    }

    public void validate(String credential, boolean encoded) {
        if (encoded) {
            if (!preencodedAllowed) {
                throw new IllegalArgumentException("不允许通过接口提交已加密密码");
            }
            return;
        }
        if (credential.length() < minimumLength) {
            throw new IllegalArgumentException("密码长度不能少于 " + minimumLength + " 位");
        }
        boolean hasLetter = credential.chars().anyMatch(Character::isLetter);
        boolean hasDigit = credential.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new IllegalArgumentException("密码必须同时包含字母和数字");
        }
    }
}
