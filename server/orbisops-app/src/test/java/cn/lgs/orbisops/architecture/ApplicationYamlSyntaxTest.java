package cn.lgs.orbisops.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ApplicationYamlSyntaxTest {

    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Test
    void developmentConfigurationHasNoDuplicateKeys() throws Exception {
        assertFalse(loader.load("application-dev", new ClassPathResource("application-dev.yml")).isEmpty());
    }

    @Test
    void productionConfigurationHasNoDuplicateKeys() throws Exception {
        assertFalse(loader.load("application-prod", new ClassPathResource("application-prod.yml")).isEmpty());
    }
}
