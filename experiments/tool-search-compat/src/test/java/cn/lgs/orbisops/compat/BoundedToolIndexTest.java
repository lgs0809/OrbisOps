package cn.lgs.orbisops.compat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;

class BoundedToolIndexTest {
    @Test void modelCannotExpandTheLimitEvenWhenAnIndexIgnoresIt() {
        var seen = new AtomicReference<ToolSearchRequest>();
        var index = new BoundedToolIndex(new ToolIndex() {
            public void indexTool(String session, ToolReference reference) { }
            public void clearIndex(String session) { }
            public ToolSearchResponse search(ToolSearchRequest request) {
                seen.set(request);
                return new ToolSearchResponse(IntStream.range(0, 20)
                        .mapToObj(i -> new ToolReference("tool_" + i, 1.0, "description")).toList(), 20, null);
            }
        }, 5);
        assertThat(index.search(new ToolSearchRequest("authorized-run", "query", 99, "metrics"))
                .toolReferences()).hasSize(5);
        assertThat(seen.get()).isEqualTo(new ToolSearchRequest("authorized-run", "query", 5, "metrics"));
        assertThat(index.search(new ToolSearchRequest("authorized-run", "query", 2, null))
                .toolReferences()).hasSize(2);
        assertThat(index.search(new ToolSearchRequest("authorized-run", "query", null, null))
                .toolReferences()).hasSize(5);
        assertThatIllegalArgumentException().isThrownBy(() -> index.search(
                new ToolSearchRequest("authorized-run", "query", 0, null)));
        assertThatIllegalArgumentException().isThrownBy(() -> index.search(
                new ToolSearchRequest("authorized-run", "query", -1, null)));
    }
}
