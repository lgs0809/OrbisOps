package cn.lgs.orbisops.domain.source.model;

import java.nio.file.Path;
import java.util.List;

public record ProjectServiceBuildCommand(Path workingDirectory, List<String> command) {

    public ProjectServiceBuildCommand {
        if (workingDirectory == null) throw new IllegalArgumentException("SOURCE_BUILD_WORKING_DIRECTORY_REQUIRED");
        command = command == null ? List.of() : List.copyOf(command);
        if (command.isEmpty()) throw new IllegalArgumentException("SOURCE_BUILD_COMMAND_REQUIRED");
    }
}
