package cn.lgs.orbisops.infrastructure.adapter.toolset;

import cn.lgs.orbisops.application.toolset.LocalLogFilePort;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Filesystem implementation of bounded local log reads. */
@Repository
public class FileLocalLogAdapter implements LocalLogFilePort {

    @Override
    public List<String> tail(String file, int limit) {
        int bounded = Math.max(1, limit);
        ArrayDeque<String> tail = new ArrayDeque<>(bounded);
        try (Stream<String> stream = Files.lines(
                Path.of(file),
                StandardCharsets.UTF_8)) {
            stream.forEach(line -> {
                if (tail.size() == bounded) tail.removeFirst();
                tail.addLast(line);
            });
            return List.copyOf(tail);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "读取本地日志失败：" + error.getMessage(),
                    error);
        }
    }

    @Override
    public List<String> search(
            String file,
            String pattern,
            int limit) {
        String query = pattern == null
                ? ""
                : pattern.toLowerCase(Locale.ROOT);
        try (Stream<String> stream = Files.lines(
                Path.of(file),
                StandardCharsets.UTF_8)) {
            return stream
                    .filter(line -> line != null
                            && line.toLowerCase(Locale.ROOT).contains(query))
                    .limit(Math.max(1, limit))
                    .toList();
        } catch (IOException error) {
            throw new IllegalStateException(
                    "读取本地日志失败：" + error.getMessage(),
                    error);
        }
    }
}
