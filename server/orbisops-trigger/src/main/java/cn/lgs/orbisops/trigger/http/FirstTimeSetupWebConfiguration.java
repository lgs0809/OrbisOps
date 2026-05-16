package cn.lgs.orbisops.trigger.http;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/** Browser CORS policy for the anonymous first-time setup surface only. */
@Configuration
public class FirstTimeSetupWebConfiguration implements WebMvcConfigurer {

    private final String allowedOriginPatterns;

    public FirstTimeSetupWebConfiguration(
            @Value("${orbisops.allowed-origin-patterns:http://127.0.0.1:3000,http://localhost:3000}")
            String allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/setup/**")
                .allowedOriginPatterns(parseCsv(allowedOriginPatterns))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    private String[] parseCsv(String value) {
        String[] values = Arrays.stream((value == null ? "" : value).split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        return values.length == 0
                ? new String[]{"http://127.0.0.1:3000", "http://localhost:3000"}
                : values;
    }
}
