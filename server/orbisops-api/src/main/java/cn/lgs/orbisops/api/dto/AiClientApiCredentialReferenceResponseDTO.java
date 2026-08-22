package cn.lgs.orbisops.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** Safe credential metadata for an AI Provider. Never contains the resolved credential value. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiClientApiCredentialReferenceResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String apiId;
    private String credentialEnvironmentVariable;
    private boolean environmentReference;
    private boolean legacyStoredCredential;
}
