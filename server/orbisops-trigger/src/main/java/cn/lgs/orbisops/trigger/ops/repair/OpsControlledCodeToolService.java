package cn.lgs.orbisops.trigger.ops.repair;

import cn.lgs.orbisops.application.repair.ControlledCodeApplicationService;
import cn.lgs.orbisops.trigger.application.repair.OpsControlledCodeMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class OpsControlledCodeToolService {

    private final ControlledCodeApplicationService application;
    private final OpsControlledCodeMapper mapper;

    public OpsControlledCodeToolService(
            ControlledCodeApplicationService application,
            OpsControlledCodeMapper mapper) {
        this.application = application;
        this.mapper = mapper;
    }

    public Map<String, Object> read(Map<String, Object> request, String actor) {
        return mapper.view(application.read(mapper.read(request), actor));
    }

    public Map<String, Object> grep(Map<String, Object> request, String actor) {
        return mapper.view(application.grep(mapper.grep(request), actor));
    }

    public Map<String, Object> glob(Map<String, Object> request, String actor) {
        return mapper.view(application.glob(mapper.glob(request), actor));
    }

    public Map<String, Object> edit(Map<String, Object> request, String actor) {
        return mapper.editView(application.edit(mapper.edit(request), actor));
    }

    public Map<String, Object> write(Map<String, Object> request, String actor) {
        return mapper.writeView(application.write(mapper.write(request), actor));
    }

    public Map<String, Object> bash(Map<String, Object> request, String actor) {
        return mapper.view(application.bash(mapper.bash(request), actor));
    }

    public Map<String, Object> enterWorktree(Map<String, Object> request, String actor) {
        return mapper.view(application.enterWorktree(mapper.enterWorktree(request), actor));
    }

    public Map<String, Object> exitWorktree(Map<String, Object> request, String actor) {
        return mapper.view(application.exitWorktree(mapper.exitWorktree(request), actor));
    }

    public Map<String, Object> lsp(Map<String, Object> request, String actor) {
        return mapper.view(application.lsp(mapper.lsp(request), actor));
    }

    public Map<String, Object> computeRepairDiff(Map<String, Object> request, String actor) {
        return mapper.view(application.computeDiff(mapper.workspaceId(request), actor));
    }

    public Map<String, Object> commitRepair(Map<String, Object> request, String actor) {
        return mapper.view(application.commit(mapper.commit(request), actor));
    }
}
