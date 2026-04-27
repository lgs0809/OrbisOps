package cn.lgs.orbisops.application.channel;

import cn.lgs.orbisops.domain.channel.adapter.repository.IChannelRepository;
import cn.lgs.orbisops.domain.channel.model.ChannelMessageRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Projects one authoritative Work Session into a compact Channel progress surface.
 * Progress is presentation only: delivery/update failures never change run authority.
 */
public final class ChannelRunProgressApplicationService {

    private static final Duration UPDATE_DEBOUNCE = Duration.ofSeconds(2);
    private static final Duration STAGED_DEBOUNCE = Duration.ofSeconds(10);

    private final IChannelRepository repository;
    private final ChannelOutboundApplicationService outbound;
    private final ChannelOutboundDeliveryPort deliveryPort;

    public ChannelRunProgressApplicationService(IChannelRepository repository,
                                                ChannelOutboundApplicationService outbound,
                                                ChannelOutboundDeliveryPort deliveryPort) {
        if (repository == null) throw new IllegalArgumentException("CHANNEL_REPOSITORY_REQUIRED");
        if (outbound == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_APPLICATION_REQUIRED");
        if (deliveryPort == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_DELIVERY_PORT_REQUIRED");
        this.repository = repository;
        this.outbound = outbound;
        this.deliveryPort = deliveryPort;
    }

    public ProjectionOutcome project(ProgressCommand command) {
        if (command == null) return ProjectionOutcome.skipped("PROGRESS_COMMAND_MISSING");
        ChannelRecord channel = repository.findById(required(command.channelId(), "CHANNEL_ID_REQUIRED"))
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_NOT_FOUND"));
        if (!required(command.projectId(), "CHANNEL_PROJECT_ID_REQUIRED").equals(channel.projectId())) {
            throw new SecurityException("CHANNEL_PROJECT_MISMATCH");
        }
        if (channel.status() != ChannelStatus.ACTIVE) return ProjectionOutcome.skipped("CHANNEL_DISABLED");

        String runId = required(command.runId(), "CHANNEL_PROGRESS_RUN_ID_REQUIRED");
        String target = required(command.target(), "CHANNEL_TARGET_REQUIRED");
        boolean updateSupported = deliveryPort.supportsMessageUpdate(channel);
        Optional<ChannelMessageRecord> existing = repository.findLatestOutboundByRun(
                channel.projectId(), channel.channelId(), runId, ChannelRunProgressContract.SENDER);
        Duration debounce = updateSupported ? UPDATE_DEBOUNCE : STAGED_DEBOUNCE;
        if (existing.isPresent() && !command.force() && !due(existing.get(), debounce)) {
            return ProjectionOutcome.skipped("DEBOUNCED");
        }

        String content = render(command);
        Map<String, Object> metadata = metadata(command);
        ChannelMessageRecord prior = existing.orElse(null);
        if (prior != null
                && updateSupported
                && !"FAILED".equalsIgnoreCase(prior.status())
                && prior.externalMessageId() != null
                && !prior.externalMessageId().isBlank()) {
            try {
                Map<String, Object> result = outbound.update(new ChannelModels.Update(
                        channel.projectId(), channel.channelId(), target,
                        prior.messageId(), prior.externalMessageId(), content,
                        metadata, command.actor()));
                return new ProjectionOutcome(true, true, false,
                        text(result.get("status"), "UPDATED"), prior.messageId());
            } catch (RuntimeException updateFailure) {
                // The prior progress message is presentation only. Start a fresh anchor
                // instead of allowing a provider-side edit failure to stall the run.
            }
        }

        Map<String, Object> result = outbound.send(new ChannelModels.Send(
                channel.projectId(), channel.channelId(), target, content,
                metadata, command.actor()));
        return new ProjectionOutcome(true, false, true,
                text(result.get("status"), "DELIVERED"), text(result.get("messageId"), ""));
    }

    private boolean due(ChannelMessageRecord record, Duration debounce) {
        Instant baseline = record.updateTime() == null ? record.createTime() : record.updateTime();
        if (baseline == null) return true;
        return !Instant.now().isBefore(baseline.plus(debounce));
    }

    private Map<String, Object> metadata(ProgressCommand command) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "runId", command.runId());
        put(metadata, "sessionId", command.sessionId());
        put(metadata, "source", ChannelRunProgressContract.SOURCE);
        put(metadata, "replyToMessageId", command.replyToMessageId());
        return Map.copyOf(metadata);
    }

    private String render(ProgressCommand command) {
        String stage = text(command.stage(), "INVESTIGATING").toUpperCase(Locale.ROOT);
        String title = switch (stage) {
            case "PREPARING" -> "Preparing";
            case "WAITING_APPROVAL" -> "Waiting for approval";
            case "RESUMING" -> "Resuming";
            case "PREPARING_CHANGE" -> "Preparing controlled change";
            case "COMPLETED" -> "Completed";
            case "FAILED" -> "Failed";
            case "CANCELED" -> "Canceled";
            default -> "Investigating";
        };
        int phase = switch (stage) {
            case "PREPARING" -> 0;
            case "INVESTIGATING", "RESUMING", "PREPARING_CHANGE" -> 1;
            case "WAITING_APPROVAL" -> 2;
            case "COMPLETED", "FAILED", "CANCELED" -> 3;
            default -> 1;
        };
        StringBuilder value = new StringBuilder();
        value.append("**OrbisOps · ").append(title).append("**\n\n");
        value.append(mark(phase, 0, "Prepare")).append('\n');
        value.append(mark(phase, 1, "Investigate")).append('\n');
        value.append(mark(phase, 2, "Decision / approval")).append('\n');
        value.append(mark(phase, 3, "Complete")).append("\n\n");
        String summary = abbreviate(command.summary(), 420);
        if (!summary.isBlank()) value.append("Current: ").append(summary).append("\n\n");
        value.append("Run: `").append(abbreviate(command.runId(), 80)).append('`');
        return value.toString();
    }

    private String mark(int phase, int item, String label) {
        if (phase > item) return "✓ " + label;
        if (phase == item) return "● " + label;
        return "○ " + label;
    }

    private void put(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) target.put(key, value);
    }

    private String required(String value, String reasonCode) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        return normalized;
    }

    private String text(Object value, String fallback) {
        String normalized = value == null ? "" : String.valueOf(value).trim();
        return normalized.isBlank() ? fallback : normalized;
    }

    private String abbreviate(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= max ? normalized : normalized.substring(0, max) + "...";
    }

    public record ProgressCommand(String projectId,
                                  String channelId,
                                  String target,
                                  String runId,
                                  String sessionId,
                                  String replyToMessageId,
                                  String stage,
                                  String summary,
                                  boolean force,
                                  String actor) {
        public ProgressCommand {
            actor = actor == null || actor.isBlank() ? "channel-runtime" : actor.trim();
        }
    }

    public record ProjectionOutcome(boolean attempted,
                                    boolean updated,
                                    boolean sent,
                                    String status,
                                    String messageId) {
        public static ProjectionOutcome skipped(String status) {
            return new ProjectionOutcome(false, false, false, status, "");
        }
    }
}
