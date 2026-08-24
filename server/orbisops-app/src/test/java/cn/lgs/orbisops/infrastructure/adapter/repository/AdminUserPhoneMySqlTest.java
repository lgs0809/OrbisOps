package cn.lgs.orbisops.infrastructure.adapter.repository;

import cn.lgs.orbisops.domain.security.AdminUserAccount;
import cn.lgs.orbisops.domain.security.AdminUserRole;
import cn.lgs.orbisops.application.security.JwtRevocationUnavailableException;
import cn.lgs.orbisops.infrastructure.dao.IAdminUserDao;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthSettings;
import com.auth0.jwt.JWT;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Exact migration and MyBatis adapter against a disposable native MySQL; no business DB is touched. */
@Testcontainers(disabledWithoutDocker = true)
class AdminUserPhoneMySqlTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("phone_identity").withUsername("fixture").withPassword("fixture");
    private static JdbcTemplate jdbc;
    private static AdminUserRepository repository;
    private static Path root;

    @BeforeAll
    static void initializeOriginalSchemaAndExactForwardMigration() throws Exception {
        root = Path.of("").toAbsolutePath();
        while (root != null && !Files.exists(root.resolve("db/migrations/sql/ops-admin-phone-login.sql"))) {
            root = root.getParent();
        }
        assertNotNull(root);
        var ds = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        jdbc = new JdbcTemplate(ds);
        var schema = Pattern.compile("CREATE TABLE `admin_user` \\(.*?;", Pattern.DOTALL)
                .matcher(Files.readString(root.resolve("db/migrations/sql/orbisops.sql")));
        assertTrue(schema.find());
        jdbc.execute(schema.group());
        jdbc.update("INSERT INTO admin_user(user_id,username,password,user_role,status) VALUES ('legacy-owner','legacy-name','legacy-fixture-credential','admin',1)");
        var before = jdbc.queryForMap("SELECT id,user_id,username,password,user_role,status,create_time,update_time FROM admin_user WHERE user_id='legacy-owner'");
        applyExactMigration();
        applyExactMigration();
        applyCredentialMigration();
        applyCredentialMigration();
        assertEquals(before, jdbc.queryForMap("SELECT id,user_id,username,password,user_role,status,create_time,update_time FROM admin_user WHERE user_id='legacy-owner'"));
        assertNull(jdbc.queryForObject("SELECT phone FROM admin_user WHERE user_id='legacy-owner'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT credential_version FROM admin_user WHERE user_id='legacy-owner'", Integer.class));
        assertFalse(jdbc.queryForObject("SELECT password_change_required FROM admin_user WHERE user_id='legacy-owner'", Boolean.class));
        assertNull(jdbc.queryForObject("SELECT initial_password FROM admin_user WHERE user_id='legacy-owner'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT NON_UNIQUE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin_user' AND INDEX_NAME='uk_admin_user_phone'", Integer.class));
        var factory = new SqlSessionFactoryBean();
        factory.setDataSource(ds);
        factory.setMapperLocations(new FileSystemResource(root.resolve("orbisops-app/src/main/resources/mybatis/mapper/admin_user_mapper.xml")));
        repository = new AdminUserRepository(new SqlSessionTemplate(factory.getObject()).getMapper(IAdminUserDao.class));
    }

    @BeforeEach
    void cleanOnlyDisposableSyntheticRows() {
        jdbc.update("DELETE FROM admin_user WHERE user_id <> 'legacy-owner'");
    }

    @Test
    void exactMigrationIsRepeatableAndKeepsOriginalAccountCredentialRoleAndOwner() {
        var legacy = repository.findByUsername("legacy-name");
        assertEquals("legacy-owner", legacy.userId());
        assertEquals("legacy-fixture-credential", legacy.credential());
        assertEquals(AdminUserRole.ADMIN, legacy.role());
        assertNull(legacy.phone());
    }

    @Test
    void phoneRoundTripAndPartialUpdatesKeepImmutableOwnerCredentialAndPhone() {
        assertTrue(repository.insert(account("phone-owner", "13800138000")));
        var saved = repository.findByPhone("13800138000");
        assertNotNull(saved.id());
        assertEquals("phone-owner", saved.userId());
        assertEquals("13800138000", saved.phone());
        assertTrue(repository.updateById(new AdminUserAccount(saved.id(), "attempted-owner-rewrite",
                "new-display", null, null, 2, null, null)));
        var updated = repository.findById(saved.id());
        assertEquals("phone-owner", updated.userId());
        assertEquals("encoded-fixture-value", updated.credential());
        assertEquals("13800138000", updated.phone());
        assertEquals("new-display", updated.username());
        assertEquals(2, updated.status());
        assertTrue(repository.updateByUserId(new AdminUserAccount(null, "phone-owner", null,
                null, null, 1, null, null, "13900139000")));
        assertNull(repository.findByPhone("13800138000"));
        assertEquals("phone-owner", repository.findByPhone("13900139000").userId());
    }

    @Test
    void databaseUniqueKeyRejectsConcurrentDuplicateWithoutLeakingDriverText() throws Exception {
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Object>> calls = List.of(() -> raceInsert("race-one", barrier), () -> raceInsert("race-two", barrier));
            var futures = pool.invokeAll(calls, 20, TimeUnit.SECONDS);
            var results = List.of(futures.get(0).get(), futures.get(1).get());
            assertEquals(1, results.stream().filter(Boolean.TRUE::equals).count());
            var error = results.stream().filter(IllegalArgumentException.class::isInstance).findFirst().orElseThrow();
            assertEquals("账号标识或手机号已被使用", ((IllegalArgumentException) error).getMessage());
            assertNull(((IllegalArgumentException) error).getCause());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM admin_user WHERE phone='13800138000'", Integer.class));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void nullablePhoneAllowsMultipleUnchangedLegacyAccounts() {
        assertTrue(repository.insert(account("legacy-second", null)));
        assertTrue(repository.insert(account("legacy-third", null)));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM admin_user WHERE phone IS NULL", Integer.class));
    }

    @Test
    void actualRevocationSelectFailureRejectsJwtEvenWhenAccountDatabaseIsHealthy() {
        var store = revocationStore(true);
        jdbc.execute("DROP TABLE IF EXISTS admin_auth_revoked_token");
        var account = account("revocation-owner", "13800138000");
        assertTrue(repository.insert(account));
        var auth = auth(store);
        String token = auth.issueToken(account);
        jdbc.execute("DROP TABLE admin_auth_revoked_token");
        assertEquals("revocation-owner", repository.findByUserId("revocation-owner").userId());
        var unavailable = assertThrows(JwtRevocationUnavailableException.class,
                () -> store.isRevoked(JWT.decode(token).getId()));
        assertNull(unavailable.getCause());
        assertEquals("JWT_REVOCATION_UNAVAILABLE", unavailable.getMessage());
        assertTrue(auth.verifyAuthorization(token, "user").isEmpty());
        assertEquals("登录服务暂不可用，请稍后重试", assertThrows(
                cn.lgs.orbisops.application.security.AdminUserAuthenticationException.class,
                () -> auth.issueToken(account)).getMessage());
        assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
    }

    @Test
    void actualRevocationInsertFailureDoesNotClaimLogoutSucceeded() {
        jdbc.execute("DROP TABLE IF EXISTS admin_auth_revoked_token");
        jdbc.execute("CREATE TABLE admin_auth_revoked_token(jwt_id VARCHAR(64) PRIMARY KEY,subject VARCHAR(2),expires_at DATETIME,update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        var store = revocationStore(false);
        var account = account("revocation-owner", "13800138000");
        assertTrue(repository.insert(account));
        var auth = auth(store);
        String token = auth.issueToken(account);
        assertTrue(auth.verifyAuthorization(token, "user").isPresent());
        assertFalse(auth.revokeAuthorization("Bearer " + token));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM admin_auth_revoked_token", Integer.class));
        assertTrue(auth.verifyAuthorization(token, "user").isPresent());
        assertEquals(1, jdbc.queryForObject("SELECT 1", Integer.class));
    }

    @Test
    void normalLogoutPersistsOneRevocationAndRejectsTheOriginalJwt() {
        jdbc.execute("DROP TABLE IF EXISTS admin_auth_revoked_token");
        var store = revocationStore(true);
        var account = account("revocation-owner", "13800138000");
        assertTrue(repository.insert(account));
        var auth = auth(store);
        String token = auth.issueToken(account);
        assertTrue(auth.verifyAuthorization(token, "user").isPresent());
        assertTrue(auth.revokeAuthorization("Bearer " + token));
        assertTrue(auth.verifyAuthorization(token, "user").isEmpty());
        assertTrue(store.isRevoked(JWT.decode(token).getId()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM admin_auth_revoked_token WHERE jwt_id=?", Integer.class,
                JWT.decode(token).getId()));
        assertTrue(auth.revokeAuthorization("Bearer " + token));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM admin_auth_revoked_token", Integer.class));
    }

    private JwtRevocationRepository revocationStore(boolean autoInit) {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("mysqlJdbcTemplate", jdbc);
        var store = new JwtRevocationRepository(beans.getBeanProvider(JdbcTemplate.class));
        ReflectionTestUtils.setField(store, "jdbcEnabled", true);
        ReflectionTestUtils.setField(store, "autoInit", autoInit);
        return store;
    }

    private AdminAuthService auth(JwtRevocationRepository store) {
        return new AdminAuthService(store, new AdminAuthSettings(
                "synthetic-test-signing-material-for-native-logout", 1L, "", true), repository);
    }

    private Object raceInsert(String userId, CyclicBarrier barrier) throws Exception {
        barrier.await(10, TimeUnit.SECONDS);
        try {
            return repository.insert(account(userId, "13800138000"));
        } catch (IllegalArgumentException conflict) {
            return conflict;
        }
    }

    private AdminUserAccount account(String userId, String phone) {
        return new AdminUserAccount(null, userId, "fixture-display", "encoded-fixture-value",
                AdminUserRole.USER, 1, null, null, phone);
    }

    private static void applyExactMigration() throws Exception {
        MYSQL.copyFileToContainer(MountableFile.forHostPath(root.resolve("db/migrations/sql/ops-admin-phone-login.sql")),
                "/tmp/phone-migration.sql");
        var result = MYSQL.execInContainer("sh", "-c", "mysql -ufixture -pfixture phone_identity < /tmp/phone-migration.sql");
        assertEquals(0, result.getExitCode(), "Exact forward migration failed in disposable MySQL");
    }

    private static void applyCredentialMigration() throws Exception {
        MYSQL.copyFileToContainer(MountableFile.forHostPath(root.resolve("db/migrations/sql/ops-account-credential-lifecycle.sql")), "/tmp/credential-migration.sql");
        var result = MYSQL.execInContainer("sh", "-c", "mysql -ufixture -pfixture phone_identity < /tmp/credential-migration.sql");
        assertEquals(0, result.getExitCode(), "Exact credential migration failed in disposable MySQL");
    }

    @Test
    void actualPasswordChangeResetVersionedJwtAndInitialHashLifecycle() {
        var authentication = auth(revocationStore(true));
        String initial = authentication.hashPasswordForStorage("InitialPass9");
        assertTrue(repository.insert(new AdminUserAccount(null, "lifecycle", "fixture-lifecycle", initial,
                AdminUserRole.USER, 1, null, null, "13800138000", initial, true, 0)));
        var adapter = new cn.lgs.orbisops.trigger.application.security.OpsAdminUserAuthenticationAdapter(authentication);
        var useCase = new cn.lgs.orbisops.application.security.AdminCredentialLifecycleUseCase(repository, repository,
                adapter, new cn.lgs.orbisops.domain.security.AdminCredentialRules(8, false));
        String old = authentication.issueToken(repository.findByUserId("lifecycle"));
        assertTrue(authentication.verifyAuthorization(old, "user").orElseThrow().passwordChangeRequired());
        assertThrows(IllegalArgumentException.class, () -> useCase.changePassword("lifecycle", "wrong", "PersonalPass8"));
        assertThrows(IllegalArgumentException.class, () -> useCase.changePassword("lifecycle", "InitialPass9", "InitialPass9"));
        var changed = useCase.changePassword("lifecycle", "InitialPass9", "PersonalPass8");
        assertFalse(changed.account().passwordChangeRequired());
        assertEquals(1, changed.account().credentialVersion());
        assertEquals(initial, changed.account().initialCredential());
        assertFalse(changed.account().credential().contains("PersonalPass8"));
        assertTrue(authentication.verifyAuthorization(old, "user").isEmpty());
        assertTrue(authentication.verifyAuthorization(changed.token(), "user").isPresent());
        assertTrue(useCase.resetPassword("lifecycle"));
        var reset = repository.findByUserId("lifecycle");
        assertTrue(reset.passwordChangeRequired());
        assertEquals(2, reset.credentialVersion());
        assertEquals(initial, reset.credential());
        assertTrue(authentication.verifyAuthorization(changed.token(), "user").isEmpty());
        assertTrue(authentication.passwordMatches("InitialPass9", reset.credential()));
        assertEquals("13800138000", reset.phone());
        assertFalse(repository.resetToInitialCredential("lifecycle", 1));
        assertFalse(repository.changeCredential("lifecycle", 1, "must-not-commit"));
        assertEquals(reset, repository.findByUserId("lifecycle"));
    }

    @Test
    void simultaneousChangeAndResetCompareTheSameVersionAndOnlyOneCommits() throws Exception {
        assertTrue(repository.insert(new AdminUserAccount(null,"credential-race","fixture-race","initial-hash",
                AdminUserRole.USER,1,null,null,"13800138000","initial-hash",true,0)));
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var changes = pool.invokeAll(List.<Callable<Boolean>>of(
                    () -> { barrier.await(10, TimeUnit.SECONDS); return repository.changeCredential("credential-race",0,"personal-hash"); },
                    () -> { barrier.await(10, TimeUnit.SECONDS); return repository.resetToInitialCredential("credential-race",0); }));
            assertEquals(1, (changes.get(0).get() ? 1 : 0) + (changes.get(1).get() ? 1 : 0));
            assertEquals(1, repository.findByUserId("credential-race").credentialVersion());
        } finally { pool.shutdownNow(); }
    }
}
