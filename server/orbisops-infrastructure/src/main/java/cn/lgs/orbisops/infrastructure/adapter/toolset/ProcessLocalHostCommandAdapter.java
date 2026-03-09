package cn.lgs.orbisops.infrastructure.adapter.toolset;

import cn.lgs.orbisops.application.toolset.LocalHostCommandPort;
import cn.lgs.orbisops.application.toolset.LocalHostCommandResult;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** ProcessBuilder implementation of bounded local command execution. */
@Repository
public class ProcessLocalHostCommandAdapter
        implements LocalHostCommandPort {

    @Override
    public LocalHostCommandResult execute(
            List<String> command,
            String workingDirectory,
            int timeoutSeconds) {
        try {
            Process process = new ProcessBuilder(command)
                    .directory(Path.of(workingDirectory)
                            .toAbsolutePath()
                            .normalize()
                            .toFile())
                    .redirectErrorStream(true)
                    .start();
            boolean finished = process.waitFor(
                    Math.max(1, timeoutSeconds),
                    TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("命令超时：" + command);
            }
            String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            return new LocalHostCommandResult(
                    process.exitValue(),
                    output);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("执行本地命令被中断", error);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(
                    "执行本地命令失败：" + error.getMessage(),
                    error);
        }
    }
}
