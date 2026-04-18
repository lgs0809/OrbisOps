package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.application.channel.ChannelOutboundApplicationService;
import cn.lgs.orbisops.application.channel.ChannelRuntimeReadPort;
import cn.lgs.orbisops.application.changepackage.ChangePackageCurrentReadPort;
import cn.lgs.orbisops.application.channel.provider.ChannelRichContent;
import cn.lgs.orbisops.domain.channel.model.ChannelRecord;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrent;
import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public final class OpsChannelApprovalCardService {

    private static final Set<String> INTERACTIVE_APPROVAL_PROVIDERS = Set.of("WECOM", "FEISHU", "SLACK", "DINGTALK");

    private final OpsChannelApprovalActionService approvalActions;
    private final ChannelOutboundApplicationService outbound;
    private final ChannelRuntimeReadPort channels;
    private final ChangePackageCurrentReadPort packages;

    public OpsChannelApprovalCardService(OpsChannelApprovalActionService approvalActions,
                                         ChannelOutboundApplicationService outbound,
                                         ChannelRuntimeReadPort channels,
                                         ChangePackageCurrentReadPort packages) {
        if (approvalActions == null) throw new IllegalArgumentException("CHANNEL_APPROVAL_ACTION_SERVICE_REQUIRED");
        if (outbound == null) throw new IllegalArgumentException("CHANNEL_OUTBOUND_SERVICE_REQUIRED");
        if (channels == null) throw new IllegalArgumentException("CHANNEL_REPOSITORY_REQUIRED");
        if (packages == null) throw new IllegalArgumentException("CHANGE_PACKAGE_CURRENT_REPOSITORY_REQUIRED");
        this.approvalActions = approvalActions;
        this.outbound = outbound;
        this.channels = channels;
        this.packages = packages;
    }

    public java.util.List<Map<String, Object>> available(String projectId) {
        String safeProjectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        return channels.findByProject(safeProjectId).stream()
                .filter(channel -> channel.status() == cn.lgs.orbisops.domain.channel.model.ChannelStatus.ACTIVE)
                .filter(channel -> INTERACTIVE_APPROVAL_PROVIDERS.contains(channel.channelType()))
                .map(channel -> Map.<String, Object>of(
                        "channelId", channel.channelId(),
                        "name", channel.name(),
                        "type", channel.channelType()))
                .toList();
    }

    public Map<String, Object> send(String projectId,
                                    String channelId,
                                    String target,
                                    String packageId,
                                    String actor) {
        String safeProjectId = required(projectId, "CHANNEL_PROJECT_ID_REQUIRED");
        String safeChannelId = required(channelId, "CHANNEL_ID_REQUIRED");
        ChannelRecord channel = channels.findById(safeChannelId)
                .orElseThrow(() -> new IllegalArgumentException("CHANNEL_NOT_FOUND"));
        if (!safeProjectId.equals(channel.projectId())) throw new SecurityException("CHANNEL_PROJECT_MISMATCH");
        if (!INTERACTIVE_APPROVAL_PROVIDERS.contains(channel.channelType())) {
            throw new IllegalArgumentException("CHANNEL_APPROVAL_CARD_PROVIDER_UNSUPPORTED:" + channel.channelType());
        }
        if (!packages.available()) throw new IllegalStateException("CHANGE_PACKAGE_CURRENT_STORE_UNAVAILABLE");
        ChangePackageCurrent current = packages.find(required(packageId, "CHANGE_PACKAGE_ID_REQUIRED"))
                .orElseThrow(() -> new IllegalArgumentException("CHANGE_PACKAGE_NOT_FOUND"));
        if (!safeProjectId.equals(current.projectId())) throw new SecurityException("CHANNEL_APPROVAL_PROJECT_MISMATCH");
        OpsChannelApprovalActionService.ApprovalActions actions = approvalActions.issue(safeChannelId, current.packageId(), actor);
        String summary = summary(current);
        return outbound.sendRich(new ChannelModels.RichSend(
                safeProjectId,
                safeChannelId,
                required(target, "CHANNEL_TARGET_REQUIRED"),
                new ChannelRichContent(summary, summary, actions.actions()),
                Map.of("source", "CHANNEL_APPROVAL_CARD"),
                required(actor, "CHANNEL_APPROVAL_ISSUER_REQUIRED")));
    }

    private String summary(ChangePackageCurrent current) {
        String risk = current.state().value(ChangePackageCurrentField.RISK_LEVEL);
        String target = current.state().value(ChangePackageCurrentField.TARGET_ENVIRONMENT);
        String service = current.state().nullable(ChangePackageCurrentField.SERVICE_ID);
        String objective = current.state().value(ChangePackageCurrentField.OBJECTIVE);
        String diff = current.state().nullable(ChangePackageCurrentField.DIFF_SUMMARY);
        String rollback = current.state().value(ChangePackageCurrentField.ROLLBACK_STEPS_JSON);
        String verify = current.state().value(ChangePackageCurrentField.VERIFICATION_CRITERIA_JSON);
        return "ChangePackage " + current.packageId() + " v" + current.version()
                + "\nWHY: " + fallback(objective, "Review governed production change")
                + "\nWHAT: " + fallback(diff, "See OrbisOps Approval Cockpit")
                + "\nWHERE: " + fallback(target, "unspecified") + " / " + fallback(service, "unspecified")
                + "\nRISK: " + fallback(risk, "UNKNOWN")
                + "\nROLLBACK: " + fallback(rollback, "See OrbisOps")
                + "\nVERIFY: " + fallback(verify, "See OrbisOps");
    }

    private String fallback(String value, String fallback) {
        String safe = value == null ? "" : value.trim();
        return safe.isBlank() ? fallback : safe;
    }

    private String required(String value, String reasonCode) {
        String safe = value == null ? "" : value.trim();
        if (safe.isBlank()) throw new IllegalArgumentException(reasonCode);
        return safe;
    }
}
