package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.memory.MemoryExtractionApplicationService;
import cn.lgs.orbisops.application.memory.MemoryExtractionCommand;
import cn.lgs.orbisops.trigger.application.memory.OpsColdMemoryMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Historical Trigger compatibility entry for typed memory extraction. */
@Service
public class OpsMemoryExtractor {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MemoryExtractionApplicationService applicationService;
    private final OpsColdMemoryMapper memoryMapper;
    private final OpsMemoryExtractionSettings settings;

    public OpsMemoryExtractor() {
        this(
                MemoryExtractionApplicationService.rulesOnly(
                        () -> FORMATTER.format(LocalDateTime.now())),
                OpsMemoryExtractionSettings.legacyConstructorDefaults());
    }

    public OpsMemoryExtractor(MemoryExtractionApplicationService applicationService) {
        this(applicationService, OpsMemoryExtractionSettings.legacyConstructorDefaults());
    }

    @Autowired
    public OpsMemoryExtractor(
            MemoryExtractionApplicationService applicationService,
            OpsMemoryExtractionSettings settings) {
        this.applicationService = applicationService;
        this.memoryMapper = new OpsColdMemoryMapper();
        this.settings = settings == null ? OpsMemoryExtractionSettings.defaults() : settings;
    }

    public List<OpsMemoryItem> extract(OpsMemoryMessage message) {
        if (!settings.enabled()
                || message == null
                || !StringUtils.hasText(message.getContent())) {
            return List.of();
        }
        return memoryMapper.candidateViews(applicationService.extract(
                new MemoryExtractionCommand(
                        memoryMapper.snapshot(message),
                        settings.maxItemsPerMessage(),
                        settings.modelEnabled(),
                        settings.modelMaxInputChars())));
    }
}
