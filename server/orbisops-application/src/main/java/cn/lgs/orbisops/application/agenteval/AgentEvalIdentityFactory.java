package cn.lgs.orbisops.application.agenteval;

import java.time.Clock;
import java.time.Instant;
import java.util.function.Supplier;

/** Application-owned naming rules for Agent Eval suites, runs and case runs. */
public final class AgentEvalIdentityFactory {

    private final Supplier<String> tokenSupplier;
    private final Clock clock;

    public AgentEvalIdentityFactory(Supplier<String> tokenSupplier, Clock clock) {
        if (tokenSupplier == null) throw new IllegalArgumentException("AGENT_EVAL_ID_TOKEN_SUPPLIER_REQUIRED");
        this.tokenSupplier = tokenSupplier;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public String newSuiteId() {
        return prefixed("agent-eval-suite-");
    }

    public String newRunId() {
        return prefixed("agent-eval-run-");
    }

    public String newCaseRunId() {
        return prefixed("agent-eval-case-run-");
    }

    public Instant now() {
        return clock.instant();
    }

    private String prefixed(String prefix) {
        String token = tokenSupplier.get();
        String normalized = token == null ? "" : token.trim();
        if (normalized.isBlank()) throw new IllegalStateException("AGENT_EVAL_ID_TOKEN_EMPTY");
        return prefix + normalized;
    }
}
