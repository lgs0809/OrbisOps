package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagMultimodalRepository;

import java.util.concurrent.atomic.AtomicBoolean;

/** Evaluates multimodal availability and owns the one-time unavailable warning decision. */
public final class RagMultimodalAvailability {

    private final RagMultimodalSettings settings;
    private final IRagMultimodalRepository repository;
    private final AtomicBoolean unavailableWarningDecided = new AtomicBoolean(false);

    public RagMultimodalAvailability(
            RagMultimodalSettings settings,
            IRagMultimodalRepository repository) {
        if (settings == null) throw new IllegalArgumentException("RAG_MULTIMODAL_SETTINGS_REQUIRED");
        if (repository == null) throw new IllegalArgumentException("RAG_MULTIMODAL_REPOSITORY_REQUIRED");
        this.settings = settings;
        this.repository = repository;
    }

    public Decision evaluate() {
        boolean baseConfigured = settings.baseUrlConfigured();
        boolean credentialConfigured = settings.apiKeyConfigured();
        if (!settings.enabled()) {
            return new Decision(false, false, settings.provider(),
                    baseConfigured, credentialConfigured, false);
        }

        boolean structuralConfigurationAvailable = settings.providerSupported()
                && baseConfigured
                && credentialConfigured;
        if (!structuralConfigurationAvailable) {
            boolean warnUnavailable = unavailableWarningDecided.compareAndSet(false, true);
            boolean repositoryAvailable = warnUnavailable && repository.available();
            return new Decision(false, warnUnavailable, settings.provider(),
                    baseConfigured, credentialConfigured, repositoryAvailable);
        }

        boolean repositoryAvailable = repository.available();
        boolean warnUnavailable = !repositoryAvailable
                && unavailableWarningDecided.compareAndSet(false, true);
        return new Decision(repositoryAvailable, warnUnavailable, settings.provider(),
                baseConfigured, credentialConfigured, repositoryAvailable);
    }

    public record Decision(
            boolean available,
            boolean warnUnavailable,
            String provider,
            boolean baseUrlConfigured,
            boolean apiKeyConfigured,
            boolean repositoryAvailable) {
    }
}
