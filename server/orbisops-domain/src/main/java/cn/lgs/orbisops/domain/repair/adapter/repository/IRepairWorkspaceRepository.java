package cn.lgs.orbisops.domain.repair.adapter.repository;

import cn.lgs.orbisops.domain.repair.model.RepairWorkspace;
import cn.lgs.orbisops.domain.repair.model.RepairWorkspaceStatus;
import cn.lgs.orbisops.domain.repair.model.RepairWriterLease;

import java.util.List;
import java.util.Optional;

public interface IRepairWorkspaceRepository {

    RepairWorkspace save(RepairWorkspace workspace);

    Optional<RepairWorkspace> find(String workspaceId);

    List<RepairWorkspace> list(String projectId);

    RepairWriterLease claimWriter(String workspaceId, String ownerId, long leaseSeconds);

    void updateWriterOwnedStatus(String workspaceId, String ownerId, RepairWorkspaceStatus status);

    boolean releaseWriter(String workspaceId, String ownerId);

    RepairWorkspace update(RepairWorkspace workspace);
}
