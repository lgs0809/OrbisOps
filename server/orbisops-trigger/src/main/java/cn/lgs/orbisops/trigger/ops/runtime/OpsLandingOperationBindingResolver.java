package cn.lgs.orbisops.trigger.ops.runtime;

import cn.lgs.orbisops.application.changepackage.LandingOperationExecutionBinding;

import java.util.List;
import java.util.Map;

/** Resolves the platform-owned frozen operation identity for a Landing tool call. */
final class OpsLandingOperationBindingResolver {

    boolean ownsExecutionKey(OpsMcpServerConfig config, String toolName) {
        if (!landing(config) || config.getLandingOperationBindings() == null) return false;
        return config.getLandingOperationBindings().stream().anyMatch(binding -> binding != null
                && toolsetMatches(config, binding)
                && value(toolName).equalsIgnoreCase(value(binding.toolName()))
                && !binding.operationId().isBlank() && !binding.executionKey().isBlank());
    }

    LandingOperationExecutionBinding resolve(
            OpsMcpServerConfig config,
            String toolName,
            Map<String, Object> arguments) {
        if (!landing(config) || config.getLandingOperationBindings() == null
                || config.getLandingOperationBindings().isEmpty()) return null;
        String requested = value(toolName);
        List<LandingOperationExecutionBinding> candidates = config.getLandingOperationBindings().stream()
                .filter(binding -> binding != null && toolsetMatches(config, binding))
                .toList();
        if (candidates.isEmpty()) return null;

        String explicitKey = value(arguments == null ? null : arguments.get("executionKey"));
        List<LandingOperationExecutionBinding> exact = candidates.stream()
                .filter(binding -> requested.equalsIgnoreCase(value(binding.toolName())))
                .toList();
        if (!exact.isEmpty()) return validate(select(exact, arguments, requested), explicitKey, requested);
        if (!"get_operation_receipt".equalsIgnoreCase(requested)) return null;

        List<LandingOperationExecutionBinding> keyed = explicitKey.isBlank()
                ? candidates
                : candidates.stream().filter(binding -> explicitKey.equals(binding.executionKey())).toList();
        if (keyed.isEmpty()) {
            throw new IllegalStateException(
                    "LANDING_OPERATION_EXECUTION_BINDING_MISMATCH：tool=" + requested);
        }
        return validate(select(keyed, arguments, requested), explicitKey, requested);
    }

    String operationId(LandingOperationExecutionBinding binding, String toolName) {
        return binding != null && value(binding.toolName()).equalsIgnoreCase(value(toolName))
                ? binding.operationId()
                : "";
    }

    private LandingOperationExecutionBinding validate(
            LandingOperationExecutionBinding binding,
            String explicitKey,
            String toolName) {
        if (!explicitKey.isBlank() && !explicitKey.equals(binding.executionKey())) {
            throw new IllegalStateException(
                    "LANDING_OPERATION_EXECUTION_BINDING_MISMATCH：executionKey");
        }
        if (binding.executionKey().isBlank() || binding.operationId().isBlank()) {
            throw new IllegalStateException(
                    "LANDING_OPERATION_EXECUTION_BINDING_INCOMPLETE：tool=" + toolName);
        }
        return binding;
    }

    private LandingOperationExecutionBinding select(
            List<LandingOperationExecutionBinding> candidates,
            Map<String, Object> arguments,
            String toolName) {
        if (candidates.size() == 1) {
            LandingOperationExecutionBinding only = candidates.get(0);
            String service = serviceArgument(arguments);
            if (!service.isBlank() && serviceTool(toolName)
                    && !resourceMatches(only.resourceKey(), service)) {
                throw new IllegalStateException(
                        "LANDING_OPERATION_RESOURCE_MISMATCH：tool=" + toolName);
            }
            return only;
        }
        String service = serviceArgument(arguments);
        if (!service.isBlank()) {
            List<LandingOperationExecutionBinding> matching = candidates.stream()
                    .filter(binding -> resourceMatches(binding.resourceKey(), service))
                    .toList();
            if (matching.size() == 1) return matching.get(0);
        }
        throw new IllegalStateException(
                "LANDING_OPERATION_EXECUTION_BINDING_AMBIGUOUS：tool=" + toolName);
    }

    private boolean toolsetMatches(
            OpsMcpServerConfig config,
            LandingOperationExecutionBinding binding) {
        String expected = normalize(binding.toolsetId());
        if (expected.isBlank()) return true;
        return java.util.stream.Stream.of(config.getMcpId(), config.getToolId(), config.getName())
                .map(this::normalize)
                .filter(value -> !value.isBlank())
                .anyMatch(expected::equals);
    }

    private String serviceArgument(Map<String, Object> arguments) {
        if (arguments == null) return "";
        return first(arguments.get("service"), arguments.get("serviceId"),
                arguments.get("resourceKey"), arguments.get("resource"));
    }

    private boolean resourceMatches(String resourceKey, String service) {
        String resource = value(resourceKey);
        String target = value(service);
        if (resource.isBlank() || target.isBlank()) return false;
        if (resource.equals(target)) return true;
        int schemeSeparator = resource.lastIndexOf("//");
        String normalized = schemeSeparator >= 0 ? resource.substring(schemeSeparator + 2) : resource;
        int slash = normalized.lastIndexOf('/');
        return (slash >= 0 ? normalized.substring(slash + 1) : normalized).equals(target);
    }

    private boolean serviceTool(String toolName) {
        return List.of("get_service_status", "restart_service_dry_run", "restart_service",
                "get_operation_receipt").stream().anyMatch(toolName::equalsIgnoreCase);
    }

    private boolean landing(OpsMcpServerConfig config) {
        return config != null && "LANDING".equalsIgnoreCase(value(config.getToolCallStage()));
    }

    private String normalize(String value) {
        String normalized = value(value);
        return normalized.startsWith("mcp.") ? normalized.substring(4) : normalized;
    }

    private String first(Object... values) {
        if (values != null) {
            for (Object candidate : values) {
                String normalized = value(candidate);
                if (!normalized.isBlank()) return normalized;
            }
        }
        return "";
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
