package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.api.dto.OpsAgentRunRequestDTO;
import cn.lgs.orbisops.api.dto.OpsAnalysisResponseDTO;
import cn.lgs.orbisops.trigger.ops.OpsQuestionContext;
import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Creates the mutable state aggregate for a new analysis run. */
final class OpsAnalysisRuntimeStateFactory {

    private final OpsAnalysisSkillReadinessInspector skillReadinessInspector;

    OpsAnalysisRuntimeStateFactory(
            OpsAnalysisSkillReadinessInspector skillReadinessInspector) {
        if (skillReadinessInspector == null) {
            throw new IllegalArgumentException("ANALYSIS_SKILL_READINESS_INSPECTOR_REQUIRED");
        }
        this.skillReadinessInspector = skillReadinessInspector;
    }

    OpsAnalysisRuntimeStateManager.State create(
            OpsAgentDefinition definition,
            OpsAgentChatRequest request,
            OpsAgentRunRequestDTO analysisRequest,
            OpsAnalysisResponseDTO response) {
        initializeDefinitionSnapshot(definition, analysisRequest, response);
        OpsQuestionContext questionContext = request.getMetadata().get(
                OpsAnalysisRuntimeMetadata.QUESTION_CONTEXT_KEY)
                instanceof OpsQuestionContext context
                ? context
                : OpsQuestionContext.from(request.getQuery());
        List<String> runtimeNotes = Collections.synchronizedList(new ArrayList<>());
        runtimeNotes.add(
                "加载 Agent 编排定义：" + definition.getAgentId()
                        + " / " + definition.getEngine());
        runtimeNotes.addAll(skillReadinessInspector.inspect(definition));
        return new OpsAnalysisRuntimeStateManager.State(
                analysisRequest,
                response,
                questionContext,
                Collections.synchronizedList(new ArrayList<>()),
                runtimeNotes,
                Collections.synchronizedList(new ArrayList<>()),
                new AtomicInteger(0),
                Collections.synchronizedSet(new LinkedHashSet<>()),
                new AtomicReference<>(),
                new AtomicReference<>(new ArrayList<>()),
                new AtomicBoolean(false),
                new AtomicBoolean(false));
    }

    private void initializeDefinitionSnapshot(
            OpsAgentDefinition definition,
            OpsAgentRunRequestDTO request,
            OpsAnalysisResponseDTO response) {
        request.setAgentDefinitionId(definition.getAgentId());
        request.setAgentVersion(definition.getVersion());
        request.setAgentDefinitionSnapshotJson(JSON.toJSONString(definition));
        response.setAgentDefinitionId(definition.getAgentId());
        response.setAgentVersion(definition.getVersion());
        response.setAgentRuntime(definition.getEngine());
    }
}
