package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.memory.MemoryCompressionApplicationService;
import cn.lgs.orbisops.application.memory.MemoryCompressionCommand;
import cn.lgs.orbisops.trigger.application.memory.OpsColdMemoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Historical Trigger compatibility entry for typed context compression. */
@Service
public class OpsContextCompressor {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MemoryCompressionApplicationService applicationService;
    private final OpsColdMemoryMapper memoryMapper;
    private final OpsContextCompressionSettings settings;

    public OpsContextCompressor() {
        this(
                MemoryCompressionApplicationService.rulesOnly(
                        () -> FORMATTER.format(LocalDateTime.now())),
                OpsContextCompressionSettings.legacyConstructorDefaults());
    }

    public OpsContextCompressor(MemoryCompressionApplicationService applicationService) {
        this(applicationService, OpsContextCompressionSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsContextCompressor(
            MemoryCompressionApplicationService applicationService,
            OpsContextCompressionSettings settings) {
        this.applicationService = applicationService;
        this.memoryMapper = new OpsColdMemoryMapper();
        this.settings = settings == null
                ? OpsContextCompressionSettings.defaults()
                : settings;
    }

    public void compressIfNeeded(
            String sessionId,
            String userId,
            List<OpsMemoryMessage> recentMessages,
            int hotBufferMessages) {
        if (!settings.enabled()
                || !StringUtils.hasText(sessionId)
                || recentMessages == null) {
            return;
        }
        applicationService.compress(new MemoryCompressionCommand(
                sessionId,
                userId,
                memoryMapper.messageSnapshots(recentMessages),
                settings.thresholdMessages(),
                settings.keepRecent(),
                settings.modelEnabled(),
                settings.modelMaxInputChars(),
                hotBufferMessages));
    }

    public String trimContext(String context, int maxChars) {
        return applicationService.trimContext(context, maxChars);
    }
}
