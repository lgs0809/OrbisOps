package cn.lgs.orbisops.application.toolset;

import cn.lgs.orbisops.domain.toolset.service.LocalHostPolicy;

import java.util.List;

/** Application service for local process execution and log retrieval. */
public class LocalHostApplicationService {

    private final LocalHostCommandPort commandPort;
    private final LocalLogFilePort logFilePort;
    private final LocalHostPolicy policy;

    public LocalHostApplicationService(
            LocalHostCommandPort commandPort,
            LocalLogFilePort logFilePort) {
        this(commandPort, logFilePort, new LocalHostPolicy());
    }

    LocalHostApplicationService(
            LocalHostCommandPort commandPort,
            LocalLogFilePort logFilePort,
            LocalHostPolicy policy) {
        if (commandPort == null || logFilePort == null || policy == null) {
            throw new IllegalArgumentException(
                    "LOCAL_HOST_DEPENDENCY_REQUIRED");
        }
        this.commandPort = commandPort;
        this.logFilePort = logFilePort;
        this.policy = policy;
    }

    public String run(
            List<String> command,
            String workingDirectory,
            String allowedRoots,
            boolean requireAllowedDirectory,
            int timeoutSeconds,
            int maxResponseBytes) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("LOCAL_COMMAND_REQUIRED");
        }
        String directory = requireAllowedDirectory
                ? policy.allowedDirectory(workingDirectory, allowedRoots)
                : defaultDirectory(workingDirectory);
        LocalHostCommandResult result = commandPort.execute(
                List.copyOf(command),
                directory,
                Math.max(1, timeoutSeconds));
        String output = policy.mask(policy.abbreviate(
                result.output(),
                Math.max(1024, maxResponseBytes)));
        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    "命令失败 exitCode=" + result.exitCode()
                            + " output=" + output);
        }
        return output;
    }

    public List<String> tail(
            Object file,
            String allowedRoots,
            Object limit,
            int maxRows) {
        String allowedFile = policy.allowedFile(file, allowedRoots);
        int boundedLimit = policy.limit(limit, maxRows, 100);
        return sanitize(logFilePort.tail(allowedFile, boundedLimit), boundedLimit);
    }

    public List<String> search(
            Object file,
            String allowedRoots,
            Object pattern,
            Object limit,
            int maxRows) {
        String allowedFile = policy.allowedFile(file, allowedRoots);
        String query = text(pattern).isBlank() ? "ERROR" : text(pattern);
        int boundedLimit = policy.limit(limit, maxRows, 100);
        return sanitize(
                logFilePort.search(allowedFile, query, boundedLimit),
                boundedLimit);
    }

    public String dockerName(Object value) {
        return policy.dockerName(value);
    }

    private List<String> sanitize(List<String> lines, int limit) {
        if (lines == null || lines.isEmpty()) return List.of();
        return lines.stream()
                .filter(line -> line != null)
                .map(policy::mask)
                .limit(Math.max(1, limit))
                .toList();
    }

    private String defaultDirectory(String value) {
        String directory = text(value);
        return directory.isBlank() ? "." : directory;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
