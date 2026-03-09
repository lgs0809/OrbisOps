package cn.lgs.orbisops.trigger.application.repair;

import cn.lgs.orbisops.application.repair.CodeDeliverySecretPort;
import cn.lgs.orbisops.trigger.ops.runtime.OpsSecretResolver;
import org.springframework.stereotype.Component;

@Component
public class OpsCodeDeliverySecretAdapter implements CodeDeliverySecretPort {

    private final OpsSecretResolver secrets;

    public OpsCodeDeliverySecretAdapter(OpsSecretResolver secrets) {
        this.secrets = secrets;
    }

    @Override
    public String resolve(String secretRef) {
        return secrets.resolve(secretRef);
    }
}
