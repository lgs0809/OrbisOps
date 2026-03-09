package cn.lgs.orbisops.application.incident;

import java.util.List;

/** Narrow query port that lets Incident aggregate remediation facts without owning ChangePackage persistence. */
public interface IncidentChangePackageQueryPort {

    List<IncidentChangePackageSnapshot> findByIncidentId(String incidentId, int limit);
}
