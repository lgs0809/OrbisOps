package cn.lgs.orbisops.application.agent;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OpsMainAgentCoordinator {

    private static final Pattern REASON_CODE = Pattern.compile("^([A-Z][A-Z0-9_]{2,80})");
    private final Map<OpsMainAgentActionType, OpsMainAgentActionHandler> handlers;

    public OpsMainAgentCoordinator(List<OpsMainAgentActionHandler> handlers) {
        EnumMap<OpsMainAgentActionType, OpsMainAgentActionHandler> registry =
                new EnumMap<>(OpsMainAgentActionType.class);
        if (handlers != null) {
            for (OpsMainAgentActionHandler handler : handlers) {
                if (handler == null) continue;
                Set<OpsMainAgentActionType> supported = handler.supportedActions();
                if (supported == null) continue;
                for (OpsMainAgentActionType action : supported) {
                    if (action == null) continue;
                    OpsMainAgentActionHandler previous = registry.putIfAbsent(action, handler);
                    if (previous != null) {
                        throw new IllegalStateException("MAIN_AGENT_DUPLICATE_HANDLER_" + action.name());
                    }
                }
            }
        }
        this.handlers = Map.copyOf(registry);
    }

    public OpsMainAgentOutcome execute(OpsMainAgentCommand command) {
        Objects.requireNonNull(command, "command");
        OpsMainAgentActionHandler handler = handlers.get(command.actionType());
        if (handler == null) {
            return OpsMainAgentOutcome.failed(OpsActionStatus.BLOCKED, new OpsFailureDescriptor(
                    "MAIN_AGENT_HANDLER_NOT_CONFIGURED",
                    "ACTION_ROUTING",
                    OpsFailureCategory.DEPENDENCY,
                    "当前任务暂未配置可用的处理能力，系统没有执行任何操作。",
                    false,
                    OpsSideEffectState.NOT_STARTED,
                    List.of(),
                    List.of("CONTACT_ADMIN"),
                    references(command)));
        }
        try {
            OpsMainAgentOutcome outcome = handler.handle(command);
            if (outcome == null) {
                throw new IllegalStateException("MAIN_AGENT_HANDLER_RETURNED_NULL");
            }
            return outcome;
        } catch (OpsMainAgentActionException error) {
            return OpsMainAgentOutcome.failed(error.status(), error.failure());
        } catch (SecurityException error) {
            return OpsMainAgentOutcome.failed(OpsActionStatus.BLOCKED, new OpsFailureDescriptor(
                    reasonCode(error, "MAIN_AGENT_ACTION_FORBIDDEN"),
                    "POLICY_GUARD",
                    OpsFailureCategory.AUTHORIZATION,
                    "当前操作被权限或安全策略拒绝，系统没有绕过限制。",
                    false,
                    OpsSideEffectState.NOT_STARTED,
                    List.of(),
                    List.of("REVIEW_PERMISSION_OR_POLICY"),
                    references(command)));
        } catch (IllegalArgumentException error) {
            return OpsMainAgentOutcome.failed(OpsActionStatus.NEEDS_INPUT, new OpsFailureDescriptor(
                    reasonCode(error, "MAIN_AGENT_INPUT_INVALID"),
                    "INPUT_VALIDATION",
                    OpsFailureCategory.VALIDATION,
                    safeInputMessage(error),
                    false,
                    OpsSideEffectState.NOT_STARTED,
                    List.of(),
                    List.of("PROVIDE_MISSING_INPUT"),
                    references(command)));
        } catch (RuntimeException error) {
            OpsMainAgentOutcome providerFailure = providerFailure(command, error);
            if (providerFailure != null) return providerFailure;
            return OpsMainAgentOutcome.failed(OpsActionStatus.FAILED, new OpsFailureDescriptor(
                    reasonCode(error, "MAIN_AGENT_ACTION_FAILED"),
                    "ACTION_EXECUTION",
                    OpsFailureCategory.UNKNOWN,
                    "任务执行失败。系统不会自动重试可能产生副作用的步骤，请查看任务详情后决定下一步。",
                    false,
                    OpsSideEffectState.UNKNOWN,
                    List.of(),
                    List.of("VIEW_TASK_DETAIL", "RETRY_IF_IDEMPOTENT"),
                    references(command)));
        }
    }

    public Map<OpsMainAgentActionType, OpsMainAgentActionHandler> handlers() {
        return handlers;
    }

    private OpsMainAgentOutcome providerFailure(OpsMainAgentCommand command, RuntimeException error) {
        String code = providerFailureCode(error);
        if (code == null) return null;
        boolean retryable = "MODEL_PROVIDER_RATE_LIMITED".equals(code)
                || "MODEL_PROVIDER_UNAVAILABLE".equals(code);
        String message = switch (code) {
            case "MODEL_PROVIDER_QUOTA_EXHAUSTED" ->
                    "模型服务当前额度不足，本轮未完成分析或变更准备。请补充额度或切换可用模型后重试；系统没有因此绕过审批或自动执行生产变更。";
            case "MODEL_PROVIDER_AUTH_FAILED" ->
                    "模型服务认证失败，本轮未完成分析或变更准备。请检查模型 Provider 的凭据配置后重试；系统没有绕过审批或自动执行生产变更。";
            case "MODEL_PROVIDER_RATE_LIMITED" ->
                    "模型服务当前限流，本轮未完成分析或变更准备。稍后可安全重试本轮对话；系统没有绕过审批或自动执行生产变更。";
            default ->
                    "模型服务当前不可用，本轮未完成分析或变更准备。恢复模型服务后可重新发起；系统没有绕过审批或自动执行生产变更。";
        };
        List<String> recovery = switch (code) {
            case "MODEL_PROVIDER_QUOTA_EXHAUSTED" -> List.of("REFILL_OR_SWITCH_MODEL", "RETRY_AFTER_MODEL_RECOVERY");
            case "MODEL_PROVIDER_AUTH_FAILED" -> List.of("REVIEW_MODEL_CREDENTIALS", "RETRY_AFTER_MODEL_RECOVERY");
            default -> List.of("RETRY_AFTER_MODEL_RECOVERY");
        };
        return OpsMainAgentOutcome.failed(
                retryable ? OpsActionStatus.RETRYABLE_FAILURE : OpsActionStatus.BLOCKED,
                new OpsFailureDescriptor(
                        code,
                        "MODEL_PROVIDER",
                        OpsFailureCategory.DEPENDENCY,
                        message,
                        retryable,
                        OpsSideEffectState.UNKNOWN,
                        List.of(),
                        recovery,
                        references(command)));
    }

    private String providerFailureCode(Throwable error) {
        Throwable current = error;
        for (int depth = 0; current != null && depth < 12; depth++, current = current.getCause()) {
            String message = String.valueOf(current.getMessage()).trim();
            String normalized = message.toLowerCase(Locale.ROOT);
            if (message.startsWith("MODEL_PROVIDER_QUOTA_EXHAUSTED")
                    || normalized.contains("insufficient_balance")
                    || normalized.contains("insufficient account balance")
                    || normalized.contains("quota exceeded")) {
                return "MODEL_PROVIDER_QUOTA_EXHAUSTED";
            }
            if (message.startsWith("MODEL_PROVIDER_AUTH_FAILED")
                    || normalized.contains("invalid api key")
                    || normalized.contains("invalid_api_key")
                    || normalized.contains("unauthorized")
                    || normalized.matches("(?s).*\\b401\\b.*")) {
                return "MODEL_PROVIDER_AUTH_FAILED";
            }
            if (message.startsWith("MODEL_PROVIDER_RATE_LIMITED")
                    || normalized.contains("rate limit")
                    || normalized.contains("rate_limit")
                    || normalized.matches("(?s).*\\b429\\b.*")) {
                return "MODEL_PROVIDER_RATE_LIMITED";
            }
            if (message.startsWith("MODEL_PROVIDER_UNAVAILABLE")) {
                return "MODEL_PROVIDER_UNAVAILABLE";
            }
        }
        return null;
    }

    private Map<String, Object> references(OpsMainAgentCommand command) {
        ReferenceBuilder refs = new ReferenceBuilder();
        refs.put("runId", command.runId());
        refs.put("sessionId", command.sessionId());
        refs.put("projectId", command.projectId());
        refs.put("actionType", command.actionType().name());
        return refs.build();
    }

    private String safeInputMessage(IllegalArgumentException error) {
        String message = error.getMessage();
        if (message == null || message.isBlank() || containsSensitiveKey(message)) {
            return "任务缺少必要信息或输入格式不正确，请根据提示补充后重试。";
        }
        return message.length() > 300 ? message.substring(0, 300) : message;
    }

    private boolean containsSensitiveKey(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("secret")
                || normalized.contains("token") || normalized.contains("credential");
    }

    private String reasonCode(Throwable error, String fallback) {
        String message = error == null ? "" : String.valueOf(error.getMessage()).trim();
        Matcher matcher = REASON_CODE.matcher(message);
        return matcher.find() ? matcher.group(1) : fallback;
    }

    private static final class ReferenceBuilder {
        private final java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();

        void put(String key, String value) {
            if (value != null && !value.isBlank()) values.put(key, value);
        }

        Map<String, Object> build() {
            return Map.copyOf(values);
        }
    }
}
