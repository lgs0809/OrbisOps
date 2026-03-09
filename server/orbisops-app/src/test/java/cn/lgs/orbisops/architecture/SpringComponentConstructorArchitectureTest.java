package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SpringComponentConstructorArchitectureTest {

    private static final Pattern STEREOTYPE = Pattern.compile(
            "@(Component|Service|Repository|Controller|RestController)\\b");

    @Test
    void springComponentsWithMultipleConstructorsMustDeclareTheInjectionConstructor()
            throws IOException {
        List<String> violations = new ArrayList<>();
        sourceFiles().forEach(path -> inspect(path, violations));
        assertTrue(violations.isEmpty(),
                "Spring components with ambiguous constructors: " + violations);
    }

    @Test
    void proxyAdvisedSpringComponentsMustRemainProxyable()
            throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path path : sourceFiles()) {
            String source = Files.readString(path);
            boolean proxyAdvised = source.contains("@Repository")
                    || source.contains("@Transactional")
                    || source.contains("@Async")
                    || source.contains("@Retryable")
                    || source.contains("@Cacheable")
                    || source.contains("@CacheEvict")
                    || source.contains("@CachePut");
            if (STEREOTYPE.matcher(source).find()
                    && proxyAdvised
                    && source.contains("public final class")) {
                violations.add(projectRoot().relativize(path).toString());
            }
        }
        assertTrue(violations.isEmpty(),
                "Proxy-advised Spring components cannot be final: " + violations);
    }

    private List<Path> sourceFiles() throws IOException {
        List<Path> result = new ArrayList<>();
        try (var modules = Files.list(projectRoot())) {
            for (Path module : modules.toList()) {
                Path sourceRoot = module.resolve("src/main/java");
                if (!Files.isDirectory(sourceRoot)) continue;
                try (var files = Files.walk(sourceRoot)) {
                    files.filter(path -> path.toString().endsWith(".java"))
                            .forEach(result::add);
                }
            }
        }
        return result;
    }

    private void inspect(Path path, List<String> violations) {
        try {
            String source = Files.readString(path);
            if (!STEREOTYPE.matcher(source).find()) return;
            String fileName = path.getFileName().toString();
            String className = fileName.substring(0, fileName.length() - ".java".length());
            Pattern constructor = Pattern.compile(
                    "(?m)^\\s*(?:(?:public|protected|private)\\s+)?"
                            + Pattern.quote(className) + "\\s*\\(");
            Matcher matcher = constructor.matcher(source);
            int count = 0;
            while (matcher.find()) count++;
            if (count <= 1 || hasNoArgConstructor(source, className)
                    || source.contains("@Autowired")
                    || source.contains("@org.springframework.beans.factory.annotation.Autowired")) {
                return;
            }
            violations.add(projectRoot().relativize(path).toString());
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + path, exception);
        }
    }

    private boolean hasNoArgConstructor(String source, String className) {
        return Pattern.compile(
                "(?m)^\\s*public\\s+" + Pattern.quote(className) + "\\s*\\(\\s*\\)")
                .matcher(source)
                .find();
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-trigger"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-trigger"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-trigger"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
