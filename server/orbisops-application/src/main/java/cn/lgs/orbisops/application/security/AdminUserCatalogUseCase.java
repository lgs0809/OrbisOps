package cn.lgs.orbisops.application.security;

import cn.lgs.orbisops.domain.security.AdminCredentialRules;
import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.domain.security.AdminUserStatus;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Application process manager for administrator/user account catalog operations. */
public final class AdminUserCatalogUseCase {

    private static final int USER_ID_MAX_LENGTH = 64;
    private static final int USERNAME_MAX_LENGTH = 50;

    private final AdminUserCatalogPort catalogPort;
    private final AdminUserAuthenticationPort authenticationPort;
    private final AdminCredentialRules credentialRules;
    private final Clock clock;

    public AdminUserCatalogUseCase(
            AdminUserCatalogPort catalogPort,
            AdminUserAuthenticationPort authenticationPort,
            AdminCredentialRules credentialRules) {
        this(catalogPort, authenticationPort, credentialRules, Clock.systemDefaultZone());
    }

    public AdminUserCatalogUseCase(
            AdminUserCatalogPort catalogPort,
            AdminUserAuthenticationPort authenticationPort,
            AdminCredentialRules credentialRules,
            Clock clock) {
        if (catalogPort == null) {
            throw new IllegalArgumentException("ADMIN_USER_CATALOG_PORT_REQUIRED");
        }
        if (authenticationPort == null) {
            throw new IllegalArgumentException("ADMIN_USER_AUTHENTICATION_PORT_REQUIRED");
        }
        if (credentialRules == null) {
            throw new IllegalArgumentException("ADMIN_CREDENTIAL_RULES_REQUIRED");
        }
        if (clock == null) {
            throw new IllegalArgumentException("ADMIN_USER_CLOCK_REQUIRED");
        }
        this.catalogPort = catalogPort;
        this.authenticationPort = authenticationPort;
        this.credentialRules = credentialRules;
        this.clock = clock;
    }

    public boolean create(AdminUserCommand requested) {
        AdminUserCommand command = requireCommand(requested);
        if (!hasText(command.userId())
                || !hasText(command.username())
                || !hasText(command.credential())) {
            throw new IllegalArgumentException("用户ID、用户名和密码不能为空");
        }
        validateIdentifierLength(command.userId(), USER_ID_MAX_LENGTH, "用户ID");
        validateIdentifierLength(command.username(), USERNAME_MAX_LENGTH, "用户名");
        AdminUserRole role = AdminUserRole.parse(command.userRole(), AdminUserRole.ADMIN);
        Integer status = AdminUserStatus.validate(command.status());
        boolean encoded = authenticationPort.isEncoded(command.credential());
        credentialRules.validate(command.credential(), encoded);
        String storedCredential = authenticationPort.encodeForStorage(command.credential());
        LocalDateTime now = LocalDateTime.now(clock);
        AdminUserAccount account = new AdminUserAccount(
                command.id(),
                command.userId(),
                command.username(),
                storedCredential,
                role,
                status == null ? AdminUserStatus.ENABLED : status,
                now,
                now);
        return catalogPort.insert(account);
    }

    public boolean updateById(AdminUserCommand requested) {
        AdminUserCommand command = requireCommand(requested);
        if (command.id() == null) {
            throw new IllegalArgumentException("ID不能为空");
        }
        return catalogPort.updateById(updateAccount(command));
    }

    public boolean updateByUserId(AdminUserCommand requested) {
        AdminUserCommand command = requireCommand(requested);
        if (!hasText(command.userId())) {
            throw new IllegalArgumentException("用户ID不能为空");
        }
        return catalogPort.updateByUserId(updateAccount(command));
    }

    public boolean deleteById(Long id) {
        return catalogPort.deleteById(id);
    }

    public boolean deleteByUserId(String userId) {
        return catalogPort.deleteByUserId(userId);
    }

    public AdminUserAccount findById(Long id) {
        return catalogPort.findById(id);
    }

    public AdminUserAccount findByUserId(String userId) {
        return catalogPort.findByUserId(userId);
    }

    public AdminUserAccount findByUsername(String username) {
        return catalogPort.findByUsername(username);
    }

    public List<AdminUserAccount> listEnabled() {
        return immutable(catalogPort.listEnabled());
    }

    public List<AdminUserAccount> listByStatus(Integer status) {
        return immutable(catalogPort.listByStatus(status));
    }

    public List<AdminUserAccount> listAll() {
        return immutable(catalogPort.listAll());
    }

    public List<AdminUserAccount> query(AdminUserCatalogQuery requested) {
        AdminUserCatalogQuery query = requested == null ? AdminUserCatalogQuery.all() : requested;
        List<AdminUserAccount> filtered = immutable(catalogPort.listAll()).stream()
                .filter(account -> !hasText(query.userId()) || query.userId().equals(account.userId()))
                .filter(account -> !hasText(query.username())
                        || (account.username() != null && account.username().contains(query.username())))
                .filter(account -> query.status() == null || query.status().equals(account.status()))
                .toList();
        int pageNum = Math.max(1, query.pageNum() == null ? 1 : query.pageNum());
        int pageSize = Math.max(1, Math.min(query.pageSize() == null ? 10 : query.pageSize(), 100));
        int startIndex = (pageNum - 1) * pageSize;
        if (startIndex >= filtered.size()) {
            return List.of();
        }
        int endIndex = Math.min(startIndex + pageSize, filtered.size());
        return List.copyOf(filtered.subList(startIndex, endIndex));
    }

    private AdminUserAccount updateAccount(AdminUserCommand command) {
        AdminUserRole role = AdminUserRole.parse(command.userRole(), null);
        Integer status = AdminUserStatus.validate(command.status());
        String storedCredential = null;
        if (hasText(command.credential())) {
            boolean encoded = authenticationPort.isEncoded(command.credential());
            credentialRules.validate(command.credential(), encoded);
            storedCredential = authenticationPort.encodeForStorage(command.credential());
        }
        return new AdminUserAccount(
                command.id(),
                command.userId(),
                command.username(),
                storedCredential,
                role,
                status,
                null,
                LocalDateTime.now(clock));
    }

    private AdminUserCommand requireCommand(AdminUserCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        return command;
    }

    private List<AdminUserAccount> immutable(List<AdminUserAccount> values) {
        return values == null || values.isEmpty() ? List.of() : List.copyOf(values);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private void validateIdentifierLength(String value, int maxLength, String label) {
        if (value.trim().length() > maxLength) {
            throw new IllegalArgumentException(label + "长度不能超过 " + maxLength + " 个字符");
        }
    }
}
