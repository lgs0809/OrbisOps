package cn.lgs.orbisops.application.evidence;

import java.time.Clock;
import java.util.function.Supplier;

/** Application-owned naming rules shared by ToolResult, Evidence and TrustedProof. */
public final class EvidenceIdentityFactory {

    private final Supplier<String> tokenSupplier;
    private final Clock clock;

    public EvidenceIdentityFactory(Supplier<String> tokenSupplier, Clock clock) {
        if (tokenSupplier == null) throw new IllegalArgumentException("EVIDENCE_ID_TOKEN_SUPPLIER_REQUIRED");
        this.tokenSupplier = tokenSupplier;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public String nextToolResultId() {
        return prefixed("tool-result-");
    }

    public String nextEvidenceId() {
        return prefixed("evidence-");
    }

    public String nextProofId() {
        return prefixed("proof-");
    }

    public String now() {
        return clock.instant().toString();
    }

    private String prefixed(String prefix) {
        String token = tokenSupplier.get();
        String normalized = token == null ? "" : token.trim();
        if (normalized.isBlank()) throw new IllegalStateException("EVIDENCE_ID_TOKEN_EMPTY");
        return prefix + normalized;
    }
}
