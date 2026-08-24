package cn.lgs.orbisops.application.security;

/** Credentials submitted to the account authentication flow. */
public record AdminUserLoginCommand(
        String username,
        String credential,
        String phone) {

    public AdminUserLoginCommand(String username, String credential) {
        this(username, credential, null);
    }

    @Override
    public String toString() {
        return "AdminUserLoginCommand[credentials=REDACTED]";
    }
}
