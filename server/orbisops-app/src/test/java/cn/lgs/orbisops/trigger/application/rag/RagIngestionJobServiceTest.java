package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagIngestionJobUseCase;
import cn.lgs.orbisops.application.rag.RagIngestionJobView;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagIngestionJobServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void adaptsMultipartFilesToNeutralResourcesForValidation() {
        RagIngestionJobUseCase useCase = mock(RagIngestionJobUseCase.class);
        RagIngestionJobService service = new RagIngestionJobService(useCase);
        MockMultipartFile markdown = file("runbook.md", "text/markdown", "# 慢 SQL 排查");

        service.validate("ops", "sop", List.of(markdown));

        ArgumentCaptor<List<RagFileResource>> resources = ArgumentCaptor.forClass(List.class);
        verify(useCase).validate(eq("ops"), eq("sop"), resources.capture());
        assertEquals("runbook.md", resources.getValue().get(0).fileName());
        assertInstanceOf(MultipartRagFileResource.class, resources.getValue().get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void delegatesAsyncSubmissionToApplicationUseCase() throws Exception {
        RagIngestionJobUseCase useCase = mock(RagIngestionJobUseCase.class);
        RagIngestionJobService service = new RagIngestionJobService(useCase);
        MockMultipartFile markdown = file("runbook.md", "text/markdown", "# Runbook");
        RagParsePolicy policy = RagParsePolicy.defaults();
        RagIngestionJobView expected = new RagIngestionJobView(
                "rag_job_1", "PENDING", "ops", "sop", List.of("runbook.md"), 9L,
                null, "2026-07-19 08:00:00", "2026-07-19 08:00:00", Map.of());
        when(useCase.submit(eq("ops"), eq("sop"), any(List.class), eq(policy))).thenReturn(expected);

        RagIngestionJobView actual = service.submit("ops", "sop", List.of(markdown), policy);

        assertEquals(expected, actual);
        verify(useCase).submit(eq("ops"), eq("sop"), any(List.class), eq(policy));
    }

    private MockMultipartFile file(String name, String contentType, String content) {
        return new MockMultipartFile("files", name, contentType, content.getBytes(StandardCharsets.UTF_8));
    }
}
