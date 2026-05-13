package cn.lgs.orbisops.application.rag;

import cn.lgs.orbisops.domain.knowledge.rag.repository.IRagIngestionJobRepository;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagIngestionJob;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagParsePolicy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Application process manager for asynchronous RAG ingestion jobs. */
public final class RagIngestionJobApplicationService implements RagIngestionJobUseCase {

    private static final System.Logger LOG = System.getLogger(RagIngestionJobApplicationService.class.getName());
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final RagIngestionCommandUseCase ingestionUseCase;
    private final Executor executor;
    private final IRagIngestionJobRepository repository;
    private final RagIngestionJobSettings settings;
    private final Clock clock;
    private final Supplier<String> jobIdSupplier;
    private final Map<String, RagIngestionJobView> memoryJobs = new ConcurrentHashMap<>();

    public RagIngestionJobApplicationService(RagIngestionCommandUseCase ingestionUseCase,
                                             Executor executor,
                                             IRagIngestionJobRepository repository,
                                             RagIngestionJobSettings settings) {
        this(ingestionUseCase, executor, repository, settings, Clock.systemDefaultZone(),
                () -> "rag_job_" + UUID.randomUUID().toString().replace("-", ""));
    }

    RagIngestionJobApplicationService(RagIngestionCommandUseCase ingestionUseCase,
                                      Executor executor,
                                      IRagIngestionJobRepository repository,
                                      RagIngestionJobSettings settings,
                                      Clock clock,
                                      Supplier<String> jobIdSupplier) {
        if (ingestionUseCase == null) throw new IllegalArgumentException("RAG_INGESTION_USE_CASE_REQUIRED");
        if (executor == null) throw new IllegalArgumentException("RAG_INGESTION_EXECUTOR_REQUIRED");
        if (repository == null) throw new IllegalArgumentException("RAG_INGESTION_JOB_REPOSITORY_REQUIRED");
        if (settings == null) throw new IllegalArgumentException("RAG_INGESTION_JOB_SETTINGS_REQUIRED");
        if (clock == null) throw new IllegalArgumentException("RAG_INGESTION_CLOCK_REQUIRED");
        if (jobIdSupplier == null) throw new IllegalArgumentException("RAG_INGESTION_JOB_ID_SUPPLIER_REQUIRED");
        this.ingestionUseCase = ingestionUseCase;
        this.executor = executor;
        this.repository = repository;
        this.settings = settings;
        this.clock = clock;
        this.jobIdSupplier = jobIdSupplier;
    }

    @Override
    public RagIngestionJobView submit(String name, String tag, List<RagFileResource> files) {
        return submit(name, tag, files, RagParsePolicy.defaults());
    }

    @Override
    public RagIngestionJobView submit(String name,
                                      String tag,
                                      List<RagFileResource> files,
                                      RagParsePolicy parsePolicy) {
        validate(name, tag, files);
        List<RagFileResource> storedFiles = snapshot(files);
        validate(name, tag, storedFiles);
        RagParsePolicy effectivePolicy = (parsePolicy == null ? RagParsePolicy.defaults() : parsePolicy)
                .normalized(RagParsePolicy.DEFAULT_MAX_SEGMENT_CHARS);
        List<String> fileNames = storedFiles.stream().map(RagFileResource::fileName).toList();
        long totalBytes = storedFiles.stream().mapToLong(RagFileResource::size).sum();
        String now = now();
        RagIngestionJob pending = RagIngestionJob.pending(
                jobIdSupplier.get(), name.trim(), tag.trim(), fileNames, totalBytes, now);
        Map<String, Object> metadata = metadata(effectivePolicy);
        save(pending, metadata);
        try {
            executor.execute(() -> execute(pending, storedFiles, effectivePolicy, metadata));
        } catch (RuntimeException error) {
            save(pending.fail(reason(error), now()), metadata);
            throw error;
        }
        return RagIngestionJobView.from(pending, metadata);
    }

    @Override
    public void validate(String name, String tag, List<RagFileResource> files) {
        if (!hasText(name)) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        if (!hasText(tag)) {
            throw new IllegalArgumentException("知识标签不能为空");
        }
        List<RagFileResource> safeFiles = files == null ? List.of() : files;
        if (safeFiles.isEmpty()) {
            throw new IllegalArgumentException("至少上传 1 个文件");
        }
        if (safeFiles.size() > settings.maxFileCount()) {
            throw new IllegalArgumentException("上传文件数量超过限制：" + settings.maxFileCount());
        }
        long totalBytes = 0L;
        for (RagFileResource file : safeFiles) {
            validateFile(file, totalBytes);
            totalBytes += file.size();
        }
    }

    @Override
    public RagIngestionJobView get(String jobId) {
        if (!hasText(jobId)) {
            return null;
        }
        RagIngestionJob persisted = repository.get(jobId);
        RagIngestionJobView memory = memoryJobs.get(jobId);
        if (persisted == null) {
            return memory;
        }
        return RagIngestionJobView.from(persisted, memory == null ? null : memory.metadata());
    }

    @Override
    public List<RagIngestionJobView> list(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<RagIngestionJob> storedJobs = repository.list(safeLimit);
        List<RagIngestionJobView> persisted = (storedJobs == null ? List.<RagIngestionJob>of() : storedJobs).stream()
                .map(job -> {
                    RagIngestionJobView memory = memoryJobs.get(job.jobId());
                    return RagIngestionJobView.from(job, memory == null ? null : memory.metadata());
                })
                .toList();
        if (!persisted.isEmpty()) {
            return persisted;
        }
        return memoryJobs.values().stream()
                .sorted(Comparator.comparing(RagIngestionJobView::updatedAt,
                        Comparator.nullsLast(String::compareTo)).reversed())
                .limit(safeLimit)
                .toList();
    }

    private void execute(RagIngestionJob pending,
                         List<RagFileResource> storedFiles,
                         RagParsePolicy parsePolicy,
                         Map<String, Object> metadata) {
        RagIngestionJob current = pending;
        try {
            current = current.start(now());
            save(current, metadata);
            ingestionUseCase.storeRagFile(current.name(), current.tag(), storedFiles, parsePolicy);
            save(current.succeed(now()), metadata);
        } catch (Exception error) {
            LOG.log(System.Logger.Level.ERROR, "RAG asynchronous ingestion failed: " + pending.jobId(), error);
            try {
                save(current.fail(reason(error), now()), metadata);
            } catch (RuntimeException writebackError) {
                LOG.log(System.Logger.Level.ERROR,
                        "RAG ingestion failure status writeback failed: " + pending.jobId(), writebackError);
            }
        }
    }

    private void validateFile(RagFileResource file, long currentTotalBytes) {
        if (file == null || file.empty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        String fileName = file.fileName();
        long size = file.size();
        if (size > settings.maxFileBytes()) {
            throw new IllegalArgumentException("单文件超过大小限制：" + fileName);
        }
        if (size > settings.maxTotalBytes() - currentTotalBytes) {
            throw new IllegalArgumentException("上传文件总大小超过限制：" + settings.maxTotalBytes() + " bytes");
        }
        String extension = extension(fileName);
        if (!settings.allowedExtensions().contains(extension)) {
            throw new IllegalArgumentException("不支持的文件扩展名：" + fileName);
        }
        String contentType = hasText(file.contentType())
                ? file.contentType().trim().toLowerCase(Locale.ROOT)
                : "application/octet-stream";
        if (!settings.allowedContentTypes().contains(contentType)) {
            throw new IllegalArgumentException("不支持的文件类型：" + contentType);
        }
    }

    private List<RagFileResource> snapshot(List<RagFileResource> files) {
        List<RagFileResource> snapshots = new ArrayList<>();
        for (RagFileResource file : files == null ? List.<RagFileResource>of() : files) {
            if (file == null) {
                continue;
            }
            try {
                snapshots.add(new StoredRagFileResource(
                        file.name(), file.originalFilename(), file.contentType(), file.readAllBytes()));
            } catch (IOException error) {
                throw new IllegalStateException("读取上传文件失败：" + file.fileName(), error);
            }
        }
        return List.copyOf(snapshots);
    }

    private void save(RagIngestionJob job, Map<String, Object> metadata) {
        RagIngestionJobView previous = memoryJobs.get(job.jobId());
        Map<String, Object> effectiveMetadata = metadata != null
                ? metadata
                : previous == null ? null : previous.metadata();
        memoryJobs.put(job.jobId(), RagIngestionJobView.from(job, effectiveMetadata));
        repository.save(job);
    }

    private Map<String, Object> metadata(RagParsePolicy policy) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("ingestionPipeline", "RagDocumentParser");
        metadata.put("segmentationMode", "STRUCTURE_FIRST");
        metadata.put("structurePreserved", true);
        metadata.put("scope", policy.scope());
        metadata.put("projectId", policy.projectId());
        metadata.put("maxSegmentChars", policy.maxSegmentChars());
        metadata.put("hardSplitOverlapChars", policy.hardSplitOverlapChars());
        return Map.copyOf(metadata);
    }

    private String extension(String filename) {
        if (!hasText(filename)) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String now() {
        return LocalDateTime.now(clock).format(DATE_TIME_FORMATTER);
    }

    private String reason(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static final class StoredRagFileResource implements RagFileResource {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] bytes;

        private StoredRagFileResource(String name,
                                      String originalFilename,
                                      String contentType,
                                      byte[] bytes) {
            this.name = name == null ? "" : name;
            this.originalFilename = originalFilename == null ? "" : originalFilename;
            this.contentType = contentType == null ? "" : contentType;
            this.bytes = bytes == null ? new byte[0] : bytes.clone();
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String originalFilename() {
            return originalFilename;
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
        public InputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }
    }
}
