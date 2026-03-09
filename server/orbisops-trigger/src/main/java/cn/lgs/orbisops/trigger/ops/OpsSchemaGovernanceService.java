package cn.lgs.orbisops.trigger.ops;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

/** Exposes schema governance state while keeping DDL changes explicit. */
@Service
public class OpsSchemaGovernanceService {

    private final OpsSchemaGovernanceSettings settings;
    private final OpsSchemaGovernanceSnapshotFactory snapshotFactory;

    public OpsSchemaGovernanceService() {
        this(OpsSchemaGovernanceSettings.defaults());
    }

    @Autowired
    public OpsSchemaGovernanceService(OpsSchemaGovernanceSettings settings) {
        this.settings = settings == null ? OpsSchemaGovernanceSettings.defaults() : settings;
        this.snapshotFactory = new OpsSchemaGovernanceSnapshotFactory();
    }

    public Map<String, Object> snapshot() {
        return snapshotFactory.create(settings);
    }
}
