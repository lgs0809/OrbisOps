package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextSkillSelectionPort;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextSkillSelectionRequest;
import cn.lgs.orbisops.application.skill.SelectRuntimeSkillsQuery;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextSkillSelection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OpsRuntimeContextSkillSelectionAdapter implements RuntimeContextSkillSelectionPort {

    private final ObjectProvider<SelectRuntimeSkillsQuery> queryProvider;

    public OpsRuntimeContextSkillSelectionAdapter(ObjectProvider<SelectRuntimeSkillsQuery> queryProvider) {
        this.queryProvider = queryProvider;
    }

    @Override
    public RuntimeContextSkillSelection select(RuntimeContextSkillSelectionRequest request) {
        SelectRuntimeSkillsQuery query = queryProvider.getIfAvailable();
        if (query == null) {
            throw new IllegalStateException(
                    "SelectRuntimeSkillsQuery 未初始化，Runtime Context Bundle 不能采信 request skill refs");
        }
        SelectRuntimeSkillsQuery.Result result = query.select(new SelectRuntimeSkillsQuery.Request(
                request.projectId(),
                request.agentId(),
                request.requestedSkillIds(),
                request.query(),
                request.limit()));
        if (result == null) return null;
        return new RuntimeContextSkillSelection(
                result.catalogRefs(),
                result.selectedRefs(),
                result.suppressedRefs(),
                result.activeCount(),
                result.catalogCount());
    }
}
