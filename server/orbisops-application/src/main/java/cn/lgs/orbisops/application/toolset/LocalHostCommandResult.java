package cn.lgs.orbisops.application.toolset;

/** Raw result returned by the local process execution adapter. */
public record LocalHostCommandResult(int exitCode, String output) {

    public LocalHostCommandResult {
        output = output == null ? "" : output;
    }
}
