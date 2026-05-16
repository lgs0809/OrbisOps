package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** Public-safe AI Provider configuration that references deployment credentials by environment variable. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientApiCredentialReferenceRequestDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String apiId;
    private String providerName;
    private String providerType;
    private String baseUrl;
    private String credentialEnvironmentVariable;
    private String completionsPath;
    private String embeddingsPath;
    private Integer status;
}
