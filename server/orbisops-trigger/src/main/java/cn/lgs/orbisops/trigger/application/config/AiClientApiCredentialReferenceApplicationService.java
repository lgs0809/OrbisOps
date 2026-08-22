package cn.lgs.orbisops.trigger.application.config;

import cn.lgs.orbisops.api.dto.AiClientApiCredentialReferenceRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientApiCredentialReferenceResponseDTO;
import cn.lgs.orbisops.application.config.AiClientApiCatalogUseCase;
import cn.lgs.orbisops.application.config.AiClientApiDefinition;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Public-safe Provider credential configuration.
 *
 * <p>The UI supplies only an environment-variable name. The catalog stores the
 * same ${env:NAME:} reference shape already used by default Provider bootstrap;
 * Runtime resolves it through OpsSecretResolver.</p>
 */
@Service
public class AiClientApiCredentialReferenceApplicationService {

    private static final Pattern ENV_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern ENV_REFERENCE = Pattern.compile("\\$\\{env:([A-Za-z_][A-Za-z0-9_]*)(?::[^}]*)?}");

    private final AiClientApiCatalogUseCase catalogUseCase;

    public AiClientApiCredentialReferenceApplicationService(AiClientApiCatalogUseCase catalogUseCase) {
        if (catalogUseCase == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_CATALOG_USE_CASE_REQUIRED");
        }
        this.catalogUseCase = catalogUseCase;
    }

    public boolean create(AiClientApiCredentialReferenceRequestDTO request) {
        return catalogUseCase.create(toDefinition(request));
    }

    public boolean update(AiClientApiCredentialReferenceRequestDTO request) {
        if (request == null || request.getId() == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_ID_REQUIRED");
        }
        return catalogUseCase.updateById(toDefinition(request));
    }

    public AiClientApiCredentialReferenceResponseDTO queryReference(String apiId) {
        if (!StringUtils.hasText(apiId)) {
            throw new IllegalArgumentException("AI_CLIENT_API_ID_REQUIRED");
        }
        AiClientApiDefinition definition = catalogUseCase.findByApiId(apiId.trim());
        if (definition == null) {
            return null;
        }
        String stored = definition.apiKey();
        Matcher matcher = StringUtils.hasText(stored) ? ENV_REFERENCE.matcher(stored.trim()) : null;
        boolean environmentReference = matcher != null && matcher.matches();
        return AiClientApiCredentialReferenceResponseDTO.builder()
                .apiId(definition.apiId())
                .credentialEnvironmentVariable(environmentReference ? matcher.group(1) : "")
                .environmentReference(environmentReference)
                .legacyStoredCredential(StringUtils.hasText(stored) && !environmentReference)
                .build();
    }

    private AiClientApiDefinition toDefinition(AiClientApiCredentialReferenceRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("AI_CLIENT_API_REQUEST_REQUIRED");
        }
        String envName = normalizeEnvironmentName(request.getCredentialEnvironmentVariable());
        return new AiClientApiDefinition(
                request.getId(),
                request.getApiId(),
                request.getProviderName(),
                request.getProviderType(),
                request.getBaseUrl(),
                "${env:" + envName + ":}",
                request.getCompletionsPath(),
                request.getEmbeddingsPath(),
                request.getStatus(),
                null,
                null);
    }

    private String normalizeEnvironmentName(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if (!ENV_NAME.matcher(normalized).matches()) {
            throw new IllegalArgumentException("CREDENTIAL_ENVIRONMENT_VARIABLE_INVALID");
        }
        return normalized;
    }
}
