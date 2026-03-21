package cn.lgs.orbisops.trigger.application.runtime;

import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextBundleCreateApplicationService;
import cn.lgs.orbisops.application.runtime.contextbundle.RuntimeContextBundleQueryApplicationService;
import cn.lgs.orbisops.domain.runtime.contextbundle.model.RuntimeContextBundleSnapshot;
import cn.lgs.orbisops.trigger.ops.runtime.OpsAgentChatRequest;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import cn.lgs.orbisops.trigger.application.episode.OpsTaskEpisodeContextCapture;

import java.util.Map;

@Component
public class OpsRuntimeContextBundleAdapter {

    private final RuntimeContextBundleCreateApplicationService commands;
    private final RuntimeContextBundleQueryApplicationService queries;
    private final OpsRuntimeContextBundleMapper mapper;
    private final OpsRuntimeContextBundleSettings settings;
    private final OpsTaskEpisodeContextCapture episodeCapture;

    public OpsRuntimeContextBundleAdapter(
            RuntimeContextBundleCreateApplicationService commands,
            RuntimeContextBundleQueryApplicationService queries,
            OpsRuntimeContextBundleMapper mapper,
            OpsRuntimeContextBundleSettings settings) {
        this(commands, queries, mapper, settings, null);
    }

    @Autowired
    public OpsRuntimeContextBundleAdapter(
            RuntimeContextBundleCreateApplicationService commands,
            RuntimeContextBundleQueryApplicationService queries,
            OpsRuntimeContextBundleMapper mapper,
            OpsRuntimeContextBundleSettings settings,
            OpsTaskEpisodeContextCapture episodeCapture) {
        if (commands == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_COMMANDS_REQUIRED");
        if (queries == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_QUERIES_REQUIRED");
        if (mapper == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_MAPPER_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("RUNTIME_CONTEXT_BUNDLE_SETTINGS_REQUIRED");
        this.commands = commands;
        this.queries = queries;
        this.mapper = mapper;
        this.settings = settings;
        this.episodeCapture = episodeCapture;
    }

    public Map<String, Object> createBundle(
            OpsAgentChatRequest request,
            String memoryContext,
            Map<String, Object> metadata) {
        Map<String, Object> bundle = mapper.view(commands.create(mapper.createCommand(
                request,
                memoryContext,
                metadata,
                settings.selectedSkillLimit())));
        if (episodeCapture != null) episodeCapture.capture(request, memoryContext, bundle);
        return bundle;
    }

    public Map<String, Object> requireBundle(String contextBundleId, String contextBundleHash) {
        return mapper.view(requireSnapshot(contextBundleId, contextBundleHash));
    }

    public RuntimeContextBundleSnapshot requireSnapshot(
            String contextBundleId,
            String contextBundleHash) {
        return queries.require(contextBundleId, contextBundleHash);
    }

    public Map<String, Object> latestBundleForSession(String sessionId, String projectId) {
        return mapper.view(queries.latestForSession(sessionId, projectId));
    }

    public Map<String, Object> latestCompletedBundleForSession(
            String sessionId,
            String projectId,
            String actor) {
        return mapper.view(queries.latestCompletedForSession(sessionId, projectId, actor));
    }
}
