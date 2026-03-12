package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Global dependency gate for the completed Domain/Application/Trigger layering. */
class DddLayerDependencyArchitectureTest {

    private static final String DOMAIN = "orbisops-domain/src/main/java";
    private static final String APPLICATION = "orbisops-application/src/main/java";
    private static final String API = "orbisops-api/src/main/java";
    private static final String TRIGGER = "orbisops-trigger/src/main/java";

    @Test
    void domainMustRemainFrameworkAndOuterLayerIndependent() throws IOException {
        List<String> violations = forbiddenImports(
                DOMAIN,
                List.of(
                        "cn.lgs.orbisops.api",
                        "cn.lgs.orbisops.application",
                        "cn.lgs.orbisops.trigger",
                        "cn.lgs.orbisops.infrastructure",
                        "org.springframework",
                        "com.alibaba.fastjson",
                        "com.fasterxml.jackson"));

        assertTrue(violations.isEmpty(), () -> "Domain dependency violations:\n" + String.join("\n", violations));
    }

    @Test
    void applicationMustDependOnlyOnDomainAndItsOwnPorts() throws IOException {
        List<String> violations = forbiddenImports(
                APPLICATION,
                List.of(
                        "cn.lgs.orbisops.api.dto",
                        "cn.lgs.orbisops.trigger",
                        "cn.lgs.orbisops.infrastructure",
                        "org.springframework",
                        "com.alibaba.fastjson",
                        "com.fasterxml.jackson"));

        assertTrue(violations.isEmpty(), () -> "Application dependency violations:\n" + String.join("\n", violations));
    }

    @Test
    void apiMustRemainAFrameworkFreeTransportContractModule() throws IOException {
        List<String> violations = forbiddenImports(
                API,
                List.of(
                        "cn.lgs.orbisops.application",
                        "cn.lgs.orbisops.domain",
                        "cn.lgs.orbisops.infrastructure",
                        "cn.lgs.orbisops.trigger",
                        "org.springframework",
                        "jakarta.servlet",
                        "org.apache.catalina"));

        assertTrue(violations.isEmpty(), () -> "API dependency violations:\n" + String.join("\n", violations));
    }

    @Test
    void triggerMustDependOnApplicationPortsInsteadOfInfrastructureImplementations() throws IOException {
        List<String> violations = forbiddenImports(
                TRIGGER,
                List.of("cn.lgs.orbisops.infrastructure"));

        assertTrue(violations.isEmpty(), () -> "Trigger -> Infrastructure dependency violations:\n"
                + String.join("\n", violations));
    }

    @Test
    void mavenModulesMustPreserveDddDependencyDirection() throws IOException {
        String domainPom = Files.readString(projectRoot().resolve("orbisops-domain/pom.xml"));
        String apiPom = Files.readString(projectRoot().resolve("orbisops-api/pom.xml"));
        String triggerPom = Files.readString(projectRoot().resolve("orbisops-trigger/pom.xml"));

        assertAll(
                () -> assertTrue(!domainPom.contains("<artifactId>fastjson</artifactId>")),
                () -> assertTrue(!domainPom.contains("<artifactId>java-jwt</artifactId>")),
                () -> assertTrue(!domainPom.contains("<artifactId>guava</artifactId>")),
                () -> assertTrue(!domainPom.contains("<artifactId>commons-codec</artifactId>")),
                () -> assertTrue(!domainPom.contains("<artifactId>commons-lang3</artifactId>")),
                () -> assertTrue(!apiPom.contains("<artifactId>spring-webmvc</artifactId>")),
                () -> assertTrue(!apiPom.contains("<artifactId>tomcat-embed-core</artifactId>")),
                () -> assertTrue(!apiPom.contains("<artifactId>jakarta.validation-api</artifactId>")),
                () -> assertTrue(!triggerPom.contains("<artifactId>orbisops-infrastructure</artifactId>")),
                () -> assertTrue(!triggerPom.contains("<artifactId>agentscope</artifactId>")),
                () -> assertTrue(!triggerPom.contains("<artifactId>commons-lang3</artifactId>")),
                () -> assertTrue(!triggerPom.contains("<artifactId>Java-WebSocket</artifactId>")));
    }

    @Test
    void triggerComponentsMustUseConstructorInjectionAndFacadesMustUseApplicationPorts() throws IOException {
        List<String> repositoryViolations = new ArrayList<>();
        List<String> fieldInjectionViolations = new ArrayList<>();
        Path root = projectRoot().resolve(TRIGGER);
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (hasFieldInjection(source)) {
                    fieldInjectionViolations.add(relative);
                }
                boolean facade = source.contains("@Service")
                        || file.getFileName().toString().contains("ApplicationService")
                        || file.getFileName().toString().contains("Facade");
                if (!facade) continue;
                boolean repositoryImport = source.lines()
                        .map(String::trim)
                        .filter(line -> line.startsWith("import "))
                        .anyMatch(line -> line.contains(".adapter.repository.")
                                || line.matches(".*Repository;$"));
                if (repositoryImport) {
                    repositoryViolations.add(relative);
                }
            }
        }

        assertAll(
                () -> assertTrue(fieldInjectionViolations.isEmpty(),
                        () -> "Trigger field injection violations:\n" + String.join("\n", fieldInjectionViolations)),
                () -> assertTrue(repositoryViolations.isEmpty(),
                        () -> "Trigger facade repository violations:\n" + String.join("\n", repositoryViolations)));
    }

    @Test
    void canonicalJsonMustRemainFrameworkFreeAndExposeBothTextContracts() throws IOException {
        String source = Files.readString(projectRoot().resolve(
                DOMAIN + "/cn/lgs/orbisops/domain/shared/json/CanonicalJson.java"));

        assertAll(
                () -> assertTrue(source.contains("String stringify(Object value)")),
                () -> assertTrue(source.contains("String stringifyPreservingOrder(Object value)")),
                () -> assertTrue(source.contains("Map<String, Object> parseObject(String value)")),
                () -> assertTrue(source.contains("List<Object> parseArray(String value)")),
                () -> assertTrue(!source.contains("com.alibaba.fastjson")),
                () -> assertTrue(!source.contains("com.fasterxml.jackson")),
                () -> assertTrue(!source.contains("org.springframework")));
    }

    private List<String> forbiddenImports(
            String relativeRoot,
            List<String> forbiddenPrefixes) throws IOException {
        List<String> violations = new ArrayList<>();
        Path root = projectRoot().resolve(relativeRoot);
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String normalized = line.trim();
                    if (!normalized.startsWith("import ")) continue;
                    for (String forbidden : forbiddenPrefixes) {
                        if (normalized.contains(forbidden)) {
                            violations.add(root.relativize(file).toString().replace('\\', '/')
                                    + " -> " + normalized);
                        }
                    }
                }
            }
        }
        return violations;
    }

    private boolean hasFieldInjection(String source) {
        String[] lines = source.split("\\R");
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].trim();
            if (!isInjectionAnnotation(line)) continue;

            String declaration = afterAnnotation(line);
            for (int cursor = index + 1;
                 declaration.isBlank() && cursor < lines.length;
                 cursor++) {
                String candidate = lines[cursor].trim();
                if (candidate.isBlank() || candidate.startsWith("@")) continue;
                declaration = candidate;
            }
            if (declaration.contains(";")) return true;
        }
        return false;
    }

    private boolean isInjectionAnnotation(String line) {
        return line.startsWith("@Resource")
                || line.startsWith("@Autowired")
                || line.startsWith("@Inject");
    }

    private String afterAnnotation(String line) {
        int closingParenthesis = line.indexOf(')');
        if (closingParenthesis >= 0) {
            return line.substring(closingParenthesis + 1).trim();
        }
        int firstSpace = line.indexOf(' ');
        return firstSpace < 0 ? "" : line.substring(firstSpace + 1).trim();
    }

    private Path projectRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isDirectory(current.resolve("orbisops-domain"))) return current;
        Path parent = current.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("orbisops-domain"))) return parent;
        Path nested = current.resolve("orbisops");
        if (Files.isDirectory(nested.resolve("orbisops-domain"))) return nested;
        throw new IllegalStateException("Cannot locate orbisops reactor root from " + current);
    }
}
