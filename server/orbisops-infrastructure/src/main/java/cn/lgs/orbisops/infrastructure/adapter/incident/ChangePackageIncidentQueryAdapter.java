package cn.lgs.orbisops.infrastructure.adapter.incident;

import cn.lgs.orbisops.application.incident.IncidentChangePackageQueryPort;
import cn.lgs.orbisops.application.incident.IncidentChangePackageSnapshot;
import cn.lgs.orbisops.domain.changepackage.adapter.repository.IChangePackageCurrentRepository;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentQuery;
import org.springframework.stereotype.Component;

import java.util.List;

/** Infrastructure adapter translating ChangePackage persistence into the Incident published read model. */
@Component
public final class ChangePackageIncidentQueryAdapter implements IncidentChangePackageQueryPort {

    private final IChangePackageCurrentRepository repository;

    public ChangePackageIncidentQueryAdapter(IChangePackageCurrentRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<IncidentChangePackageSnapshot> findByIncidentId(String incidentId, int limit) {
        if (repository == null || !repository.available()) return List.of();
        int bounded = Math.max(1, Math.min(limit, 200));
        return repository.findAll(new ChangePackageCurrentQuery("", "", text(incidentId), null, bounded)).stream()
                .map(change -> new IncidentChangePackageSnapshot(
                        change.packageId(),
                        change.status().name(),
                        change.version(),
                        change.packageHash(),
                        change.landingRunId(),
                        change.updateTime() == null ? "" : change.updateTime().toString()))
                .toList();
    }

    private String text(String value) {
        return value == null ? "" : value.trim();
    }
}
