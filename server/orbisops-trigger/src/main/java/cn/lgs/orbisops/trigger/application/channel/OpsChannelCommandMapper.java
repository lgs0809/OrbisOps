package cn.lgs.orbisops.trigger.application.channel;

import cn.lgs.orbisops.application.channel.ChannelModels;
import cn.lgs.orbisops.domain.channel.model.ChannelAccessPolicy;
import cn.lgs.orbisops.domain.channel.model.ChannelStatus;
import cn.lgs.orbisops.types.execution.ExecutionType;
import cn.lgs.orbisops.types.execution.ExecutionVersionPolicy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public final class OpsChannelCommandMapper {

    public ChannelModels.ConfigurationMutation configuration(String projectId,
                                                             String channelId,
                                                             Map<String, Object> request,
                                                             String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new ChannelModels.ConfigurationMutation(
                text(projectId),
                text(channelId),
                textField(safe, "projectId"),
                textField(safe, "channelId"),
                executionTypeField(safe),
                workflowIdField(safe),
                workflowVersionPolicyField(safe),
                workflowVersionField(safe),
                textField(safe, "name"),
                channelTypeField(safe),
                textField(safe, "credentialRef"),
                mapField(safe, "config"),
                enumField(safe, "accessPolicy", ChannelAccessPolicy::require),
                enumField(safe, "status", ChannelStatus::require),
                actor);
    }

    public ChannelModels.IdentityBinding identity(String projectId,
                                                  String channelId,
                                                  Map<String, Object> request,
                                                  String actor) {
        Map<String, Object> safe = request == null ? Map.of() : request;
        return new ChannelModels.IdentityBinding(
                projectId,
                channelId,
                bounded(safe.get("externalSenderId"), "CHANNEL_EXTERNAL_IDENTITY_REQUIRED", 256),
                text(safe.get("platformUserId")),
                text(safe.get("username")),
                ChannelStatus.require(text(safe.get("status"))),
                longValue(safe.get("expectedVersion")),
                actor);
    }

    private ChannelModels.Field<ExecutionType> executionTypeField(Map<String, Object> source) {
        if (source.containsKey("executionType")) {
            return ChannelModels.Field.supplied(ExecutionType.require(text(source.get("executionType"))));
        }
        if (source.containsKey("agentId") && !text(source.get("agentId")).isBlank()) {
            return ChannelModels.Field.supplied(ExecutionType.WORKFLOW);
        }
        return ChannelModels.Field.absent();
    }

    private ChannelModels.Field<String> workflowIdField(Map<String, Object> source) {
        if (source.containsKey("workflowId")) return textField(source, "workflowId");
        return textField(source, "agentId");
    }

    private ChannelModels.Field<ExecutionVersionPolicy> workflowVersionPolicyField(Map<String, Object> source) {
        if (source.containsKey("workflowVersionPolicy")) {
            return enumField(source, "workflowVersionPolicy", ExecutionVersionPolicy::require);
        }
        return enumField(source, "agentBindingMode", ExecutionVersionPolicy::require);
    }

    private ChannelModels.Field<Integer> workflowVersionField(Map<String, Object> source) {
        if (source.containsKey("workflowVersion")) return integerField(source, "workflowVersion");
        return integerField(source, "agentVersion");
    }

    private ChannelModels.Field<String> channelTypeField(Map<String, Object> source) {
        if (source.containsKey("type")) return ChannelModels.Field.supplied(text(source.get("type")));
        return textField(source, "channelType");
    }

    private ChannelModels.Field<String> textField(Map<String, Object> source, String key) {
        return source.containsKey(key)
                ? ChannelModels.Field.supplied(text(source.get(key)))
                : ChannelModels.Field.absent();
    }

    private ChannelModels.Field<Integer> integerField(Map<String, Object> source, String key) {
        return source.containsKey(key)
                ? ChannelModels.Field.supplied(integer(source.get(key)))
                : ChannelModels.Field.absent();
    }

    private ChannelModels.Field<Map<String, Object>> mapField(Map<String, Object> source, String key) {
        return source.containsKey(key)
                ? ChannelModels.Field.supplied(objectMap(source.get(key)))
                : ChannelModels.Field.absent();
    }

    private <T> ChannelModels.Field<T> enumField(Map<String, Object> source,
                                                 String key,
                                                 java.util.function.Function<String, T> parser) {
        return source.containsKey(key)
                ? ChannelModels.Field.supplied(parser.apply(text(source.get(key))))
                : ChannelModels.Field.absent();
    }

    private String bounded(Object value, String reasonCode, int maxLength) {
        String normalized = text(value);
        if (normalized.isBlank()) throw new IllegalArgumentException(reasonCode);
        if (normalized.length() > maxLength) throw new IllegalArgumentException("CHANNEL_VALUE_TOO_LONG");
        return normalized;
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.valueOf(text(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return Long.parseLong(text(value));
        } catch (RuntimeException ignored) {
            return 0L;
        }
    }

    private Map<String, Object> objectMap(Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
        }
        return Map.copyOf(result);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
