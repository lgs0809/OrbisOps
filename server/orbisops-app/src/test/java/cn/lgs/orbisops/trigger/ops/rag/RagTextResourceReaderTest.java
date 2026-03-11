package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagTextResourceReaderTest {

    @Test
    void readsUtf8LinesAndStripsLeadingBom() throws Exception {
        byte[] bytes = ("\uFEFF第一行\nsecond").getBytes(StandardCharsets.UTF_8);
        RagFileResource file = new ByteArrayResource(bytes);

        String text = new RagTextResourceReader().read(file);

        assertEquals("第一行\nsecond\n", text);
    }

    private record ByteArrayResource(byte[] bytes) implements RagFileResource {
        @Override public String name() { return "text"; }
        @Override public String originalFilename() { return "text.txt"; }
        @Override public String contentType() { return "text/plain"; }
        @Override public long size() { return bytes.length; }
        @Override public ByteArrayInputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }
    }
}
