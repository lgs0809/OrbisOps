package cn.lgs.orbisops.application.incident;

import cn.lgs.orbisops.domain.incident.model.IncidentSnapshot;

/** Executes/reads authoritative recovery verification without accepting client-declared success. */
public interface IncidentVerificationPort {

    IncidentVerificationResult verify(IncidentSnapshot incident, String packageId);
}
