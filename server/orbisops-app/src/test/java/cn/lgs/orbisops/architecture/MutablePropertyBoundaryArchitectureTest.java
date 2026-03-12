package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutablePropertyBoundaryArchitectureTest {

    private static final String TRIGGER =
            "orbisops-trigger/src/main/java/";

    @Test
    void landingGateMustUseTypedConstructorBoundSettings() throws IOException {
        String adapter = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/changepackage/"
                + "OpsChangePackageLandingGateAdapter.java");
        String settings = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/changepackage/"
                + "OpsChangePackageLandingSettings.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/changepackage/"
                + "OpsChangePackageLandingConfiguration.java");

        assertAll(
                () -> assertTrue(adapter.contains(
                        "private final OpsChangePackageLandingSettings settings")),
                () -> assertTrue(adapter.contains("legacyConstructorDefaults()")),
                () -> assertTrue(adapter.contains("settings.enabled()")),
                () -> assertFalse(adapter.contains("OpsApprovedLandingRolloutPolicy")),
                () -> assertFalse(adapter.contains("rolloutPolicy.requireAllowed(plan)")),
                () -> assertFalse(adapter.contains("@Value")),
                () -> assertFalse(adapter.contains("private boolean approvedLandingEnabled")),
                () -> assertTrue(settings.contains(
                        "public record OpsChangePackageLandingSettings(boolean enabled)")),
                () -> assertTrue(configuration.contains(
                        "orbisops.approved-landing.enabled")),
                () -> assertFalse(configuration.contains("orbisops.approved-landing.rollout.")));
    }

    @Test
    void modelApiBootstrapMustUseTypedConstructorBoundSettings() throws IOException {
        String service = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/config/"
                + "DefaultModelApiBootstrapService.java");
        String settings = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/config/"
                + "DefaultModelApiBootstrapSettings.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/config/"
                + "DefaultModelApiBootstrapConfiguration.java");
        String test = read(
                "orbisops-app/src/test/java/"
                        + "cn/lgs/orbisops/trigger/application/config/"
                        + "DefaultModelApiBootstrapServiceTest.java");

        assertAll(
                () -> assertTrue(service.contains(
                        "private final DefaultModelApiBootstrapUseCase bootstrapUseCase")),
                () -> assertTrue(service.contains(
                        "private final DefaultModelApiBootstrapPlan plan")),
                () -> assertTrue(service.contains("bootstrapUseCase.bootstrap(plan)")),
                () -> assertFalse(service.contains("DefaultModelApiBootstrapSettings")),
                () -> assertFalse(service.contains("IAiClientApiConfigRepository")),
                () -> assertFalse(service.contains("AiClientConfigRecord")),
                () -> assertFalse(service.contains("LocalDateTime.now")),
                () -> assertFalse(service.contains("@Value")),
                () -> assertTrue(settings.contains(
                        "public record DefaultModelApiBootstrapSettings(")),
                () -> assertTrue(configuration.contains(
                        "DefaultModelApiBootstrapPlan defaultModelApiBootstrapPlan")),
                () -> assertTrue(configuration.contains(
                        "DefaultModelApiBootstrapUseCase defaultModelApiBootstrapUseCase")),
                () -> assertTrue(configuration.contains(
                        "orbisops.model-api-bootstrap.enabled")),
                () -> assertTrue(configuration.contains(
                        "spring.ai.openai.api-key")),
                () -> assertFalse(test.contains("ReflectionTestUtils")),
                () -> assertFalse(test.contains("setField(service")));
    }

    @Test
    void channelInboundProtocolMustUseTypedConstructorBoundSettings() throws IOException {
        String adapter = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/channel/"
                + "OpsChannelInboundProtocolAdapter.java");
        String settings = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/channel/"
                + "OpsChannelInboundProtocolSettings.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/channel/"
                + "OpsChannelInboundProtocolConfiguration.java");

        assertAll(
                () -> assertTrue(adapter.contains(
                        "private final OpsChannelInboundProtocolSettings settings")),
                () -> assertTrue(adapter.contains("long maxClockSkewSeconds")),
                () -> assertTrue(adapter.contains("int maxMessageChars")),
                () -> assertTrue(adapter.contains("int maxMetadataChars")),
                () -> assertTrue(adapter.contains(
                        "new OpsChannelInboundProtocolSettings(")),
                () -> assertTrue(adapter.contains(
                        "settings.maxClockSkewSeconds()")),
                () -> assertTrue(adapter.contains("settings.maxMessageChars()")),
                () -> assertTrue(adapter.contains("settings.maxMetadataChars()")),
                () -> assertFalse(adapter.contains("@Value")),
                () -> assertFalse(adapter.contains(
                        "private final long maxClockSkewSeconds")),
                () -> assertTrue(settings.contains(
                        "public record OpsChannelInboundProtocolSettings(")),
                () -> assertTrue(settings.contains(
                        "Math.max(30, maxClockSkewSeconds)")),
                () -> assertTrue(settings.contains(
                        "Math.max(1000, maxMessageChars)")),
                () -> assertTrue(settings.contains(
                        "Math.max(1024, maxMetadataChars)")),
                () -> assertTrue(configuration.contains(
                        "orbisops.channel.webhook.max-clock-skew-seconds")),
                () -> assertTrue(configuration.contains(
                        "orbisops.channel.inbound.max-message-chars")),
                () -> assertTrue(configuration.contains(
                        "orbisops.channel.inbound.max-metadata-chars")));
    }

    @Test
    void remainingRuntimeAdaptersMustUseTypedSettings() throws IOException {
        String runtimeAdapter = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/runtime/"
                + "OpsRuntimeContextBundleAdapter.java");
        String runtimeConfiguration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/runtime/"
                + "OpsRuntimeContextBundleConfiguration.java");
        String sourceAdapter = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/source/"
                + "OpsSourceMcpProjectionAdapter.java");
        String sourceConfiguration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/source/"
                + "OpsSourceMcpProjectionConfiguration.java");

        assertAll(
                () -> assertTrue(runtimeAdapter.contains(
                        "private final OpsRuntimeContextBundleSettings settings")),
                () -> assertTrue(runtimeAdapter.contains("settings.selectedSkillLimit()")),
                () -> assertFalse(runtimeAdapter.contains("@Value")),
                () -> assertTrue(runtimeConfiguration.contains(
                        "orbisops.skill-runtime.selected-limit")),
                () -> assertTrue(sourceAdapter.contains(
                        "private final OpsSourceMcpProjectionSettings settings")),
                () -> assertTrue(sourceAdapter.contains("settings.requestTimeoutSeconds()")),
                () -> assertFalse(sourceAdapter.contains("@Value")),
                () -> assertTrue(sourceConfiguration.contains(
                        "orbisops.source-repository.command-timeout-seconds")));
    }

    @Test
    void feedbackAndRateLimitServicesMustUseTypedSettings() throws IOException {
        String feedback = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/rag/RagFeedbackService.java");
        String feedbackConfiguration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/rag/RagFeedbackConfiguration.java");
        String rateLimit = read(TRIGGER
                + "cn/lgs/orbisops/trigger/http/admin/security/OpsRateLimitService.java");
        String rateLimitConfiguration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/http/admin/security/OpsRateLimitConfiguration.java");

        assertAll(
                () -> assertTrue(feedback.contains("private final RagFeedbackUseCase useCase")),
                () -> assertTrue(feedback.contains("OpsRagFeedbackManagementAssembly assembly")),
                () -> assertFalse(feedback.contains("RagFeedbackSettings")),
                () -> assertFalse(feedback.contains("@Value")),
                () -> assertTrue(feedbackConfiguration.contains("orbisops.rag.feedback.auto-init")),
                () -> assertTrue(feedbackConfiguration.contains("OpsRagFeedbackManagementAssembly")),
                () -> assertTrue(rateLimit.contains("private final OpsRateLimitSettings settings")),
                () -> assertTrue(rateLimit.contains("settings.perUserPerMinute()")),
                () -> assertFalse(rateLimit.contains("@Value")),
                () -> assertTrue(rateLimitConfiguration.contains("orbisops.rate-limit.enabled")));
    }

    @Test
    void adminUserPolicyMustBindThroughTypedSettingsAndDomainRules() throws IOException {
        String service = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/security/AdminUserApplicationService.java");
        String configuration = read(TRIGGER
                + "cn/lgs/orbisops/trigger/application/security/AdminSecurityConfiguration.java");

        assertAll(
                () -> assertFalse(service.contains("@Value")),
                () -> assertFalse(service.contains("@Resource")),
                () -> assertFalse(service.contains("AdminUserSecuritySettings")),
                () -> assertTrue(service.contains("private final AdminUserCatalogUseCase catalogUseCase")),
                () -> assertTrue(service.contains("private final AdminUserAuthenticationUseCase authenticationUseCase")),
                () -> assertTrue(configuration.contains("AdminUserSecuritySettings adminUserSecuritySettings")),
                () -> assertTrue(configuration.contains("AdminCredentialRules adminCredentialRules")),
                () -> assertTrue(configuration.contains("settings.minimumLength()")),
                () -> assertTrue(configuration.contains("settings.prehashedAllowed()")),
                () -> assertTrue(configuration.contains("orbisops.admin.auth.password-min-length")),
                () -> assertTrue(configuration.contains("orbisops.admin.auth.allow-prehashed-passwords")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(
                current.resolve("orbisops-trigger"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null
                && Files.isDirectory(
                parent.resolve("orbisops-trigger"))) {
            return parent;
        }
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(
                nested.resolve("orbisops-trigger"))) {
            return nested;
        }
        throw new IllegalStateException(
                "Cannot locate orbisops reactor root from " + current);
    }
}
