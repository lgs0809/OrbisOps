package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangePackageCurrentStateJdbcBoundaryArchitectureTest {

    private static final String INFRASTRUCTURE = "orbisops-infrastructure/src/main/java/"
            + "cn/lgs/orbisops/infrastructure/adapter/repository/";

    @Test
    void onePackagePrivateMapperOwnsFlattenedStateOrdering() throws IOException {
        String mapper = read(INFRASTRUCTURE + "ChangePackageCurrentStateJdbcMapper.java");

        assertAll(
                () -> assertTrue(mapper.contains("final class ChangePackageCurrentStateJdbcMapper")),
                () -> assertFalse(mapper.contains("public final class")),
                () -> assertTrue(mapper.contains("List.of(ChangePackageCurrentField.values())")),
                () -> assertTrue(mapper.contains("static List<Object> orderedValues")),
                () -> assertTrue(mapper.contains("static ChangePackageCurrentState fromRow")),
                () -> assertTrue(mapper.contains("static String columnList()")),
                () -> assertTrue(mapper.contains("static String assignmentList()")),
                () -> assertTrue(mapper.contains("field.nullable() ? state.nullable(field) : state.value(field)")),
                () -> assertFalse(mapper.contains("org.springframework")));
    }

    @Test
    void currentAndPointerRepositoriesShareTheSameStateCatalog() throws IOException {
        String current = read(INFRASTRUCTURE + "JdbcChangePackageCurrentRepository.java");
        String pointer = read(INFRASTRUCTURE + "JdbcChangePackagePointerRepository.java");

        assertAll(
                () -> assertTrue(current.contains("ChangePackageCurrentStateJdbcMapper.columnList()")),
                () -> assertTrue(current.contains("ChangePackageCurrentStateJdbcMapper.orderedValues(current.state())")),
                () -> assertTrue(current.contains("ChangePackageCurrentStateJdbcMapper.fromRow(row)")),
                () -> assertTrue(pointer.contains("ChangePackageCurrentStateJdbcMapper.assignmentList()")),
                () -> assertTrue(pointer.contains("ChangePackageCurrentStateJdbcMapper.orderedValues(nextState)")),
                () -> assertFalse(current.contains("state.value(ChangePackageCurrentField.")),
                () -> assertFalse(current.contains("state.nullable(ChangePackageCurrentField.")),
                () -> assertFalse(pointer.contains("nextState.value(ChangePackageCurrentField.")),
                () -> assertFalse(pointer.contains("nextState.nullable(ChangePackageCurrentField.")),
                () -> assertFalse(current.contains("import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;")),
                () -> assertFalse(pointer.contains("import cn.lgs.orbisops.domain.changepackage.model.ChangePackageCurrentField;")));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(projectRoot().resolve(relativePath));
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
