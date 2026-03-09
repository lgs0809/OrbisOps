package cn.lgs.orbisops.application.toolset;

import java.util.List;

/** Infrastructure boundary for bounded local process execution. */
public interface LocalHostCommandPort {

    LocalHostCommandResult execute(
            List<String> command,
            String workingDirectory,
            int timeoutSeconds);
}
