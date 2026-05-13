package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.trigger.ops.rag.RagDocumentParserSettings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagDocumentParserConfiguration {

    @Bean
    public RagDocumentParserSettings ragDocumentParserSettings(
            @Value("${orbisops.rag.parse.max-chunk-chars:3000}") int maxChunkChars,
            @Value("${orbisops.rag.parse.max-pdf-images:20}") int maxPdfImages) {
        return new RagDocumentParserSettings(maxChunkChars, maxPdfImages);
    }
}
