package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagIngestionJobRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagIngestionJob;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RagIngestionJobApplicationServiceTest {

    @Test
    void validatesNeutralResourcesUsingApplicationSettings() {
        RagIngestionJobApplicationService service = service(
                mock(RagIngestionCommandUseCase.class),
                command -> { },
                new MemoryRepository(),
                new RagIngestionJobSettings(
                        2, 10L, 12L,
                        Set.of("md", "pdf"),
                        Set.of("text/markdown", "application/pdf")));

        assertDoesNotThrow(() -> service.validate("ops", "sop", List.of(
                file("runbook.md", "text/markdown", "12345"),
                file("incident.pdf", "application/pdf", "12345"))));
        assertThrows(IllegalArgumentException.class, () -> service.validate(
                "ops", "sop", List.of(file("metrics.csv", "text/csv", "1"))));
        assertThrows(IllegalArgumentException.class, () -> service.validate(
                "ops", "sop", List.of(
                        file("one.md", "text/markdown", "1234567"),
                        file("two.md", "text/markdown", "1234567"))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshotsResourcesBeforeSchedulingAndCompletesDomainLifecycle() throws Exception {
        RagIngestionCommandUseCase ingestion = mock(RagIngestionCommandUseCase.class);
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        MemoryRepository repository = new MemoryRepository();
        RagIngestionJobApplicationService service = service(
                ingestion, scheduled::set, repository, settings());
        MutableResource source = file("runbook.md", "text/markdown", "# original");

        RagIngestionJobView pending = service.submit(
                "Ops", "sop", List.of(source), RagParsePolicy.defaults());
        source.setContent("# changed");
        scheduled.get().run();

        ArgumentCaptor<List<RagFileResource>> files = ArgumentCaptor.forClass(List.class);
        verify(ingestion).storeRagFile(eq("Ops"), eq("sop"), files.capture(), any(RagParsePolicy.class));
        assertArrayEquals("# original".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                files.getValue().get(0).readAllBytes());
        assertEquals("PENDING", pending.status());
        assertEquals("SUCCEEDED", service.get("rag_job_test").status());
        assertEquals("STRUCTURE_FIRST", service.get("rag_job_test").metadata().get("segmentationMode"));
        assertEquals(List.of("PENDING", "RUNNING", "SUCCEEDED"), repository.statuses());
    }

    @Test
    void recordsFailedTerminalStateWhenIngestionThrows() {
        RagIngestionCommandUseCase ingestion = mock(RagIngestionCommandUseCase.class);
        doThrow(new IllegalStateException("vector unavailable"))
                .when(ingestion).storeRagFile(
                        anyString(), anyString(), anyList(), any(RagParsePolicy.class));
        AtomicReference<Runnable> scheduled = new AtomicReference<>();
        MemoryRepository repository = new MemoryRepository();
        RagIngestionJobApplicationService service = service(
                ingestion, scheduled::set, repository, settings());

        service.submit("Ops", "sop", List.of(file("runbook.md", "text/markdown", "# runbook")));
        scheduled.get().run();

        RagIngestionJobView failed = service.get("rag_job_test");
        assertEquals("FAILED", failed.status());
        assertEquals("vector unavailable", failed.errorMessage());
        assertEquals(List.of("PENDING", "RUNNING", "FAILED"), repository.statuses());
    }

    private RagIngestionJobApplicationService service(RagIngestionCommandUseCase ingestion,
                                                       java.util.concurrent.Executor executor,
                                                       IRagIngestionJobRepository repository,
                                                       RagIngestionJobSettings settings) {
        return new RagIngestionJobApplicationService(
                ingestion,
                executor,
                repository,
                settings,
                Clock.fixed(Instant.parse("2026-07-19T00:00:00Z"), ZoneOffset.UTC),
                () -> "rag_job_test");
    }

    private RagIngestionJobSettings settings() {
        return new RagIngestionJobSettings(
                20, 1024L, 4096L,
                Set.of("md", "markdown", "pdf"),
                Set.of("text/markdown", "text/plain", "application/pdf", "application/octet-stream"));
    }

    private MutableResource file(String name, String contentType, String content) {
        return new MutableResource(name, contentType, content);
    }

    private static final class MemoryRepository implements IRagIngestionJobRepository {
        private final Map<String, RagIngestionJob> jobs = new LinkedHashMap<>();
        private final List<String> statuses = new ArrayList<>();

        @Override
        public void save(RagIngestionJob job) {
            jobs.put(job.jobId(), job);
            statuses.add(job.status().name());
        }

        @Override
        public RagIngestionJob get(String jobId) {
            return jobs.get(jobId);
        }

        @Override
        public List<RagIngestionJob> list(int limit) {
            return jobs.values().stream().limit(limit).toList();
        }

        private List<String> statuses() {
            return List.copyOf(statuses);
        }
    }

    private static final class MutableResource implements RagFileResource {
        private final String fileName;
        private final String contentType;
        private byte[] bytes;

        private MutableResource(String fileName, String contentType, String content) {
            this.fileName = fileName;
            this.contentType = contentType;
            setContent(content);
        }

        private void setContent(String content) {
            this.bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public String name() {
            return "files";
        }

        @Override
        public String originalFilename() {
            return fileName;
        }

        @Override
        public String contentType() {
            return contentType;
        }

        @Override
        public long size() {
            return bytes.length;
        }

        @Override
        public byte[] readAllBytes() {
            return bytes.clone();
        }

        @Override
        public ByteArrayInputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }
    }
}
