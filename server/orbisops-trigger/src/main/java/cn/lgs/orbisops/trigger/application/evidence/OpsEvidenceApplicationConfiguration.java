package cn.lgs.orbisops.trigger.application.evidence;

import cn.lgs.orbisops.application.evidence.EvidenceApplicationService;
import cn.lgs.orbisops.application.evidence.EvidenceAuditPort;
import cn.lgs.orbisops.application.evidence.EvidenceIdentityFactory;
import cn.lgs.orbisops.application.evidence.EvidenceTransactionPort;
import cn.lgs.orbisops.application.evidence.ToolResultApplicationService;
import cn.lgs.orbisops.application.evidence.TrustedProofApplicationService;
import cn.lgs.orbisops.domain.evidence.adapter.repository.IEvidenceRepository;
import cn.lgs.orbisops.domain.evidence.adapter.repository.IToolResultRepository;
import cn.lgs.orbisops.domain.evidence.adapter.repository.ITrustedProofRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

@Configuration
public class OpsEvidenceApplicationConfiguration {

    @Bean
    public EvidenceIdentityFactory evidenceIdentityFactory() {
        return new EvidenceIdentityFactory(
                () -> UUID.randomUUID().toString(),
                Clock.systemUTC());
    }

    @Bean
    public ToolResultApplicationService toolResultApplicationService(
            IToolResultRepository results,
            EvidenceIdentityFactory identities,
            EvidenceAuditPort audit,
            EvidenceTransactionPort transactions,
            @Value("${orbisops.tool-result.auto-init:true}") boolean autoInit) {
        return new ToolResultApplicationService(results, identities, audit, transactions, autoInit);
    }

    @Bean
    public EvidenceApplicationService evidenceApplicationService(
            IEvidenceRepository evidence,
            EvidenceIdentityFactory identities,
            EvidenceAuditPort audit,
            EvidenceTransactionPort transactions,
            @Value("${orbisops.evidence.auto-init:true}") boolean autoInit) {
        return new EvidenceApplicationService(evidence, identities, audit, transactions, autoInit);
    }

    @Bean
    public TrustedProofApplicationService trustedProofApplicationService(
            ITrustedProofRepository proofs,
            EvidenceIdentityFactory identities,
            EvidenceAuditPort audit,
            EvidenceTransactionPort transactions,
            @Value("${orbisops.trusted-proof.auto-init:true}") boolean autoInit) {
        return new TrustedProofApplicationService(proofs, identities, audit, transactions, autoInit);
    }
}
