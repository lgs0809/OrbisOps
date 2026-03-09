package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.memory.MemoryCaptureApplicationService;
import cn.lgs.orbisops.application.memory.MemoryCaptureCommand;
import cn.lgs.orbisops.application.memory.MemoryQueryApplicationService;
import cn.lgs.orbisops.application.memory.MemorySessionClearApplicationService;
import cn.lgs.orbisops.trigger.application.memory.OpsMemoryQueryMapper;
import cn.lgs.orbisops.trigger.ops.memory.OpsMemorySelection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/** Memory query, capture, and clear facade over typed runtime policy. */
@Service
public class OpsMemoryFacade {

    private final MemoryQueryApplicationService queryService;
    private final OpsMemoryQueryMapper queryMapper;
    private final MemoryCaptureApplicationService captureService;
    private final MemorySessionClearApplicationService clearService;
    private final OpsMemoryFacadeSettings settings;

    public OpsMemoryFacade(
            MemoryQueryApplicationService queryService,
            MemoryCaptureApplicationService captureService,
            MemorySessionClearApplicationService clearService) {
        this(
                queryService,
                captureService,
                clearService,
                OpsMemoryFacadeSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsMemoryFacade(
            MemoryQueryApplicationService queryService,
            MemoryCaptureApplicationService captureService,
            MemorySessionClearApplicationService clearService,
            OpsMemoryFacadeSettings settings) {
        this.queryService = queryService;
        this.queryMapper = new OpsMemoryQueryMapper();
        this.captureService = captureService;
        this.clearService = clearService;
        this.settings = settings == null ? OpsMemoryFacadeSettings.defaults() : settings;
    }

    public String assembleContext(String sessionId, String userId, String query) {
        return assembleContext(sessionId, userId, query, Map.of());
    }

    public String assembleContext(
            String sessionId,
            String userId,
            String query,
            Map<String, Object> metadata) {
        return assembleSelection(sessionId, userId, query, metadata).context();
    }

    public OpsMemorySelection assembleSelection(
            String sessionId,
            String userId,
            String query,
            Map<String, Object> metadata) {
        if (!settings.enabled()
                || !StringUtils.hasText(sessionId)
                || queryService == null) {
            return queryMapper.selection(null);
        }
        return queryMapper.selection(queryService.query(queryMapper.command(
                sessionId,
                userId,
                query,
                metadata,
                settings.itemMatchLimit(),
                settings.hotMaxMessages(),
                settings.semanticTopK(),
                settings.contextMaxChars(),
                settings.assembleTimeoutMillis(),
                settings.recencyAwareEnabled(),
                settings.recencyHalfLifeTurns())));
    }

    public void appendMessage(
            String sessionId,
            String userId,
            String role,
            String content,
            Map<String, Object> metadata) {
        if (!settings.enabled() || captureService == null) {
            return;
        }
        captureService.capture(new MemoryCaptureCommand(
                sessionId,
                userId,
                role,
                content,
                metadata,
                settings.captureBufferSize(),
                settings.extractionAsyncEnabled()));
    }

    public void clear(String sessionId) {
        if (clearService != null) {
            clearService.clear(sessionId);
        }
    }
}
