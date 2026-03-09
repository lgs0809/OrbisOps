package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectExternalMcpCredentialReferencePort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.stereotype.Component;

@Component
public class OpsProjectExternalMcpCredentialReferenceAdapter
        implements ProjectExternalMcpCredentialReferencePort {

    private final OpsSecretResolver secretResolver;

    public OpsProjectExternalMcpCredentialReferenceAdapter(
            OpsSecretResolver secretResolver) {
        if (secretResolver == null) {
            throw new IllegalArgumentException("OPS_SECRET_RESOLVER_REQUIRED");
        }
        this.secretResolver = secretResolver;
    }

    @Override
    public boolean isReference(String value) {
        return secretResolver.isReference(value);
    }
}
