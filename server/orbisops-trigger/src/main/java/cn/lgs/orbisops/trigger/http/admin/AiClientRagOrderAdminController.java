package cn.lgs.orbisops.trigger.http.admin;

import cn.lgs.orbisops.api.dto.AiClientRagOrderQueryRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientRagOrderRequestDTO;
import cn.lgs.orbisops.api.dto.AiClientRagOrderResponseDTO;
import cn.lgs.orbisops.api.dto.RagDocumentResponseDTO;
import cn.lgs.orbisops.api.response.Response;
import cn.lgs.orbisops.application.knowledge.KnowledgeAggregateCatalogPort;
import cn.lgs.orbisops.application.knowledge.KnowledgeAggregateSnapshot;
import cn.lgs.orbisops.application.knowledge.KnowledgeCatalogApplicationService;
import cn.lgs.orbisops.application.rag.RagIngestionCommandUseCase;
import cn.lgs.orbisops.application.rag.RagIngestionJobView;
import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.trigger.application.knowledge.OpsKnowledgeAuthorizationCommandMapper;
import cn.lgs.orbisops.trigger.application.knowledge.OpsKnowledgeCatalogCommandMapper;
import cn.lgs.orbisops.trigger.application.rag.AiClientRagOrderApplicationService;
import cn.lgs.orbisops.trigger.application.rag.LegacyRagDocumentApplicationService;
import cn.lgs.orbisops.trigger.application.rag.MultipartRagFileResource;
import cn.lgs.orbisops.trigger.application.rag.RagFeedbackService;
import cn.lgs.orbisops.trigger.application.security.AdminAuthService;
import cn.lgs.orbisops.trigger.http.admin.security.AdminWebSecurityConfig;
import cn.lgs.orbisops.trigger.application.rag.RagIngestionJobService;
import cn.lgs.orbisops.trigger.application.rag.RagQualityEvalService;
import cn.lgs.orbisops.types.enums.ResponseCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 知识库配置、文档和质量评测管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ai-client-rag-order")
public class AiClientRagOrderAdminController {

    private final AiClientRagOrderApplicationService aiClientRagOrderApplicationService;
    private final LegacyRagDocumentApplicationService legacyRagDocumentApplicationService;
    private final KnowledgeAggregateCatalogPort knowledgeAggregateCatalogPort;
    private final RagIngestionCommandUseCase ragService;
    private final RagIngestionJobService ragIngestionJobService;
    private final RagQualityEvalService ragQualityEvalService;
    private final RagFeedbackService ragFeedbackService;
    private final KnowledgeCatalogApplicationService<RagIngestionJobView, MultipartFile> knowledgeCatalog;
    private final OpsKnowledgeAuthorizationCommandMapper knowledgeAuthorizationCommandMapper;
    private final OpsKnowledgeCatalogCommandMapper knowledgeCatalogCommandMapper;

    public AiClientRagOrderAdminController(
            AiClientRagOrderApplicationService aiClientRagOrderApplicationService,
            LegacyRagDocumentApplicationService legacyRagDocumentApplicationService,
            KnowledgeAggregateCatalogPort knowledgeAggregateCatalogPort,
            RagIngestionCommandUseCase ragService,
            RagIngestionJobService ragIngestionJobService,
            RagQualityEvalService ragQualityEvalService,
            RagFeedbackService ragFeedbackService,
            @Qualifier("knowledgeCatalogApplicationService")
            KnowledgeCatalogApplicationService<RagIngestionJobView, MultipartFile> knowledgeCatalog,
            OpsKnowledgeAuthorizationCommandMapper knowledgeAuthorizationCommandMapper,
            OpsKnowledgeCatalogCommandMapper knowledgeCatalogCommandMapper) {
        this.aiClientRagOrderApplicationService = aiClientRagOrderApplicationService;
        this.legacyRagDocumentApplicationService = legacyRagDocumentApplicationService;
        this.knowledgeAggregateCatalogPort = knowledgeAggregateCatalogPort;
        this.ragService = ragService;
        this.ragIngestionJobService = ragIngestionJobService;
        this.ragQualityEvalService = ragQualityEvalService;
        this.ragFeedbackService = ragFeedbackService;
        this.knowledgeCatalog = knowledgeCatalog;
        this.knowledgeAuthorizationCommandMapper = knowledgeAuthorizationCommandMapper;
        this.knowledgeCatalogCommandMapper = knowledgeCatalogCommandMapper;
    }

    @PostMapping("/create")
    public Response<Boolean> createAiClientRagOrder(@RequestBody AiClientRagOrderRequestDTO request) {
        return handleBoolean("创建知识库配置失败", () -> aiClientRagOrderApplicationService.create(request));
    }

    @PutMapping("/update-by-id")
    public Response<Boolean> updateAiClientRagOrderById(@RequestBody AiClientRagOrderRequestDTO request) {
        if (request == null || request.getId() == null) {
            return illegalParameter("ID不能为空");
        }
        return handleBoolean("根据ID更新知识库配置失败", () -> aiClientRagOrderApplicationService.updateById(request));
    }

    @PutMapping("/update-by-rag-id")
    public Response<Boolean> updateAiClientRagOrderByRagId(@RequestBody AiClientRagOrderRequestDTO request) {
        if (request == null || !StringUtils.hasText(request.getRagId())) {
            return illegalParameter("知识库ID不能为空");
        }
        return handleBoolean("根据知识库ID更新知识库配置失败", () -> aiClientRagOrderApplicationService.updateByRagId(request));
    }

    @DeleteMapping("/delete-by-id/{id}")
    public Response<Boolean> deleteAiClientRagOrderById(@PathVariable("id") Long id) {
        return handleBoolean("根据ID删除旧版 RAG 配置失败", () -> aiClientRagOrderApplicationService.deleteById(id));
    }

    @DeleteMapping("/delete-by-rag-id/{ragId}")
    public Response<Boolean> deleteAiClientRagOrderByRagId(@PathVariable("ragId") String ragId) {
        return handleBoolean("根据知识库ID删除旧版 RAG 配置失败", () -> aiClientRagOrderApplicationService.deleteByRagId(ragId));
    }

    @GetMapping("/query-by-id/{id}")
    public Response<AiClientRagOrderResponseDTO> queryAiClientRagOrderById(@PathVariable("id") Long id) {
        return handleItem("根据ID查询知识库配置失败", () -> aiClientRagOrderApplicationService.queryById(id));
    }

    @GetMapping("/query-by-rag-id/{ragId}")
    public Response<AiClientRagOrderResponseDTO> queryAiClientRagOrderByRagId(@PathVariable("ragId") String ragId) {
        return handleItem("根据知识库ID查询知识库配置失败", () -> aiClientRagOrderApplicationService.queryByRagId(ragId));
    }

    @GetMapping("/query-enabled")
    public Response<List<AiClientRagOrderResponseDTO>> queryEnabledAiClientRagOrders() {
        return handleList("查询启用的知识库配置失败", aiClientRagOrderApplicationService::queryEnabled);
    }

    @GetMapping("/query-by-knowledge-tag/{knowledgeTag}")
    public Response<List<AiClientRagOrderResponseDTO>> queryAiClientRagOrdersByKnowledgeTag(@PathVariable("knowledgeTag") String knowledgeTag) {
        return handleList("根据知识标签查询知识库配置失败", () -> aiClientRagOrderApplicationService.queryByKnowledgeTag(knowledgeTag));
    }

    @GetMapping("/query-by-status/{status}")
    public Response<List<AiClientRagOrderResponseDTO>> queryAiClientRagOrdersByStatus(@PathVariable("status") Integer status) {
        return handleList("根据状态查询知识库配置失败", () -> aiClientRagOrderApplicationService.queryByStatus(status));
    }

    @PostMapping("/query-list")
    public Response<List<AiClientRagOrderResponseDTO>> queryAiClientRagOrderList(@RequestBody(required = false) AiClientRagOrderQueryRequestDTO request) {
        return handleList("分页查询知识库配置列表失败", () -> aiClientRagOrderApplicationService.queryList(request));
    }

    @GetMapping("/query-all")
    public Response<List<AiClientRagOrderResponseDTO>> queryAllAiClientRagOrders() {
        return handleList("查询所有知识库配置失败", aiClientRagOrderApplicationService::queryAll);
    }

    @RequestMapping(value = "file/upload", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<Boolean> uploadRagFile(@RequestParam("name") String name,
                                           @RequestParam("tag") String tag,
                                           @RequestParam("files") List<MultipartFile> files) {
        return handleBoolean("知识库向量入库失败", () -> {
            ragIngestionJobService.validate(name, tag, files);
            ragService.storeRagFile(name, tag, ragFiles(files));
            return true;
        });
    }

    @RequestMapping(value = "file/upload-async", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<RagIngestionJobView> uploadRagFileAsync(@RequestParam("name") String name,
                                                                         @RequestParam("tag") String tag,
                                                                         @RequestParam("files") List<MultipartFile> files) {
        return handle("创建 RAG 异步入库任务失败", () -> {
            try {
                return ragIngestionJobService.submit(name, tag, files);
            } catch (Exception e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        }, null);
    }

    @GetMapping("/file/jobs/{jobId}")
    public Response<RagIngestionJobView> getRagIngestionJob(@PathVariable("jobId") String jobId) {
        return success(ragIngestionJobService.get(jobId));
    }

    @GetMapping("/file/jobs")
    public Response<List<RagIngestionJobView>> listRagIngestionJobs(@RequestParam(value = "limit", required = false, defaultValue = "20") Integer limit) {
        return success(ragIngestionJobService.list(limit == null ? 20 : limit));
    }

    @GetMapping("/knowledge-bases")
    public Response<List<Map<String, Object>>> listKnowledgeBases() {
        return handle("查询知识库聚合列表失败",
                () -> knowledgeAggregateCatalogPort.listAggregates().stream()
                        .map(this::aggregateView)
                        .toList(),
                List.of());
    }

    @GetMapping("/knowledge-bases/global")
    public Response<List<Map<String, Object>>> listGlobalKnowledgeBases() {
        return handle("查询通用知识库失败", knowledgeCatalog::listGlobal, List.of());
    }

    @GetMapping("/knowledge-bases/global/stats")
    public Response<Map<String, Object>> getGlobalKnowledgeStats(
            @RequestParam(value = "kbId", required = false) String kbId) {
        return handle("查询通用知识库统计失败",
                () -> knowledgeCatalog.globalStats(kbId),
                Map.of());
    }

    @PostMapping("/knowledge-bases/global")
    public Response<Map<String, Object>> createGlobalKnowledgeBase(
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("创建通用知识库失败",
                () -> knowledgeCatalog.createGlobal(
                        knowledgeCatalogCommandMapper.mutation(request, actor(servletRequest))), Map.of());
    }

    @GetMapping("/knowledge-bases/global/{kbId}")
    public Response<Map<String, Object>> getGlobalKnowledgeBase(@PathVariable("kbId") String kbId) {
        return handle("查询通用知识库失败", () -> knowledgeCatalog.getGlobal(kbId), Map.of());
    }

    @PutMapping("/knowledge-bases/global/{kbId}")
    public Response<Map<String, Object>> updateGlobalKnowledgeBase(
            @PathVariable("kbId") String kbId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新通用知识库失败", () -> knowledgeCatalog.updateGlobal(
                kbId, knowledgeCatalogCommandMapper.mutation(request, actor(servletRequest))), Map.of());
    }

    @PatchMapping("/knowledge-bases/global/{kbId}/status")
    public Response<Map<String, Object>> updateGlobalKnowledgeBaseStatus(
            @PathVariable("kbId") String kbId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新通用知识库状态失败", () -> knowledgeCatalog.updateGlobalStatus(
                kbId, knowledgeCatalogCommandMapper.status(request, actor(servletRequest))), Map.of());
    }

    @PostMapping(value = "/knowledge-bases/global/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<RagIngestionJobView> importGlobalKnowledgeDocuments(
            @PathVariable("kbId") String kbId,
            @RequestParam("name") String name,
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest servletRequest) {
        return handle("导入通用知识库文档失败",
                () -> knowledgeCatalog.importGlobalDocuments(
                        kbId, name, files, actor(servletRequest)), null);
    }

    @GetMapping("/knowledge-bases/global/{kbId}/documents")
    public Response<List<Map<String, Object>>> listGlobalKnowledgeDocuments(@PathVariable("kbId") String kbId) {
        return handle("查询通用知识库文档失败", () -> knowledgeCatalog.listGlobalDocuments(kbId), List.of());
    }

    @GetMapping("/knowledge-bases/global/{kbId}/usage-projects")
    public Response<List<Map<String, Object>>> listGlobalKnowledgeBaseUsageProjects(@PathVariable("kbId") String kbId) {
        return handle("查询通用知识库使用项目失败",
                () -> knowledgeCatalog.globalUsageProjects(kbId),
                List.of());
    }

    @GetMapping("/knowledge-bases/global/{kbId}/chunks")
    public Response<List<Map<String, Object>>> listGlobalKnowledgeChunks(@PathVariable("kbId") String kbId,
                                                                         @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return handle("查询通用知识库结构化片段失败",
                () -> knowledgeCatalog.listGlobalChunks(kbId, limit == null ? 100 : limit), List.of());
    }

    @DeleteMapping("/knowledge-bases/global/{kbId}/chunks/{chunkId}")
    public Response<Boolean> deleteGlobalKnowledgeChunk(
            @PathVariable("kbId") String kbId,
            @PathVariable("chunkId") String chunkId,
            HttpServletRequest servletRequest) {
        return handleBoolean("删除通用知识库片段失败",
                () -> knowledgeCatalog.deleteGlobalChunk(
                        kbId, chunkId, actor(servletRequest)));
    }

    @DeleteMapping("/knowledge-bases/global/{kbId}/chunks")
    public Response<Map<String, Object>> deleteGlobalKnowledgeChunks(
            @PathVariable("kbId") String kbId,
            HttpServletRequest servletRequest) {
        return handle("清理通用知识库片段失败",
                () -> knowledgeCatalog.deleteGlobalChunks(kbId, actor(servletRequest)), Map.of());
    }

    @GetMapping("/knowledge-bases/global/{kbId}/retrieval-policy")
    public Response<Map<String, Object>> getGlobalRetrievalPolicy(@PathVariable("kbId") String kbId) {
        return handle("查询通用知识库检索策略失败", () -> knowledgeCatalog.globalRetrievalPolicy(kbId), Map.of());
    }

    @PutMapping("/knowledge-bases/global/{kbId}/retrieval-policy")
    public Response<Map<String, Object>> updateGlobalRetrievalPolicy(
            @PathVariable("kbId") String kbId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新通用知识库检索策略失败",
                () -> knowledgeCatalog.updateGlobalRetrievalPolicy(
                        kbId, knowledgeCatalogCommandMapper.retrievalPolicy(
                                request, actor(servletRequest))), Map.of());
    }

    @GetMapping("/projects/{projectId}/knowledge-bases")
    public Response<List<Map<String, Object>>> listProjectKnowledgeBases(@PathVariable("projectId") String projectId) {
        return handle("查询项目知识库失败", () -> knowledgeCatalog.listProject(projectId), List.of());
    }

    @GetMapping("/projects/{projectId}/knowledge-bases/stats")
    public Response<Map<String, Object>> getProjectKnowledgeStats(@PathVariable("projectId") String projectId,
                                                                  @RequestParam(value = "kbId", required = false) String kbId) {
        return handle("查询项目知识库统计失败",
                () -> knowledgeCatalog.projectStats(projectId, kbId),
                Map.of());
    }

    @PostMapping("/projects/{projectId}/knowledge-bases")
    public Response<Map<String, Object>> createProjectKnowledgeBase(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("创建项目知识库失败",
                () -> knowledgeCatalog.createProject(
                        projectId, knowledgeCatalogCommandMapper.mutation(
                                request, actor(servletRequest))), Map.of());
    }

    @PutMapping("/projects/{projectId}/knowledge-bases/{kbId}")
    public Response<Map<String, Object>> updateProjectKnowledgeBase(
            @PathVariable("projectId") String projectId,
            @PathVariable("kbId") String kbId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新项目知识库失败",
                () -> knowledgeCatalog.updateProject(
                        projectId, kbId, knowledgeCatalogCommandMapper.mutation(
                                request, actor(servletRequest))), Map.of());
    }

    @PatchMapping("/projects/{projectId}/knowledge-bases/{kbId}/status")
    public Response<Map<String, Object>> updateProjectKnowledgeBaseStatus(
            @PathVariable("projectId") String projectId,
            @PathVariable("kbId") String kbId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新项目知识库状态失败", () -> knowledgeCatalog.updateProjectStatus(
                projectId, kbId,
                knowledgeCatalogCommandMapper.status(request, actor(servletRequest))), Map.of());
    }

    @GetMapping("/projects/{projectId}/authorized-knowledge-bases")
    public Response<List<Map<String, Object>>> listAuthorizedKnowledgeBases(@PathVariable("projectId") String projectId) {
        return handle("查询项目授权知识库失败", () -> knowledgeCatalog.listAuthorized(projectId), List.of());
    }

    @PostMapping("/projects/{projectId}/knowledge-bases/enable-global")
    public Response<Map<String, Object>> enableGlobalKnowledgeBase(
            @PathVariable("projectId") String projectId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("启用通用知识库失败",
                () -> knowledgeCatalog.enableGlobalForProject(
                        knowledgeAuthorizationCommandMapper.enableGlobal(
                                projectId, request, actor(servletRequest))), Map.of());
    }

    @PostMapping(value = "/projects/{projectId}/knowledge-bases/{kbId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Response<RagIngestionJobView> importProjectKnowledgeDocuments(
            @PathVariable("projectId") String projectId,
            @PathVariable("kbId") String kbId,
            @RequestParam("name") String name,
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest servletRequest) {
        return handle("导入项目知识库文档失败",
                () -> knowledgeCatalog.importProjectDocuments(
                        projectId, kbId, name, files, actor(servletRequest)), null);
    }

    @GetMapping("/projects/{projectId}/knowledge-bases/{kbId}/documents")
    public Response<List<Map<String, Object>>> listProjectKnowledgeDocuments(@PathVariable("projectId") String projectId,
                                                                             @PathVariable("kbId") String kbId) {
        return handle("查询项目知识库文档失败",
                () -> knowledgeCatalog.listProjectDocuments(projectId, kbId), List.of());
    }

    @GetMapping("/projects/{projectId}/knowledge-bases/{kbId}/chunks")
    public Response<List<Map<String, Object>>> listProjectKnowledgeChunks(@PathVariable("projectId") String projectId,
                                                                         @PathVariable("kbId") String kbId,
                                                                         @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return handle("查询项目知识库结构化片段失败",
                () -> knowledgeCatalog.listProjectChunks(projectId, kbId, limit == null ? 100 : limit), List.of());
    }

    @DeleteMapping("/projects/{projectId}/knowledge-bases/{kbId}/chunks/{chunkId}")
    public Response<Boolean> deleteProjectKnowledgeChunk(
            @PathVariable("projectId") String projectId,
            @PathVariable("kbId") String kbId,
            @PathVariable("chunkId") String chunkId,
            HttpServletRequest servletRequest) {
        return handleBoolean("删除项目知识库片段失败",
                () -> knowledgeCatalog.deleteProjectChunk(
                        projectId, kbId, chunkId, actor(servletRequest)));
    }

    @DeleteMapping("/projects/{projectId}/knowledge-bases/{kbId}/chunks")
    public Response<Map<String, Object>> deleteProjectKnowledgeChunks(
            @PathVariable("projectId") String projectId,
            @PathVariable("kbId") String kbId,
            HttpServletRequest servletRequest) {
        return handle("清理项目知识库片段失败",
                () -> knowledgeCatalog.deleteProjectChunks(
                        projectId, kbId, actor(servletRequest)), Map.of());
    }

    @GetMapping("/projects/{projectId}/knowledge-bases/{kbId}/retrieval-policy")
    public Response<Map<String, Object>> getProjectRetrievalPolicy(@PathVariable("projectId") String projectId,
                                                                   @PathVariable("kbId") String kbId) {
        return handle("查询项目知识库检索策略失败",
                () -> knowledgeCatalog.projectRetrievalPolicy(projectId, kbId), Map.of());
    }

    @PutMapping("/projects/{projectId}/knowledge-bases/{kbId}/retrieval-policy")
    public Response<Map<String, Object>> updateProjectRetrievalPolicy(
            @PathVariable("projectId") String projectId,
            @PathVariable("kbId") String kbId,
            @RequestBody(required = false) Map<String, Object> request,
            HttpServletRequest servletRequest) {
        return handle("更新项目知识库检索策略失败",
                () -> knowledgeCatalog.updateProjectRetrievalPolicy(
                        projectId, kbId, knowledgeCatalogCommandMapper.retrievalPolicy(
                                request, actor(servletRequest))), Map.of());
    }

    @GetMapping("/document/stats")
    public Response<Map<String, Object>> queryRagDocumentStats(@RequestParam(value = "tag", required = false) String tag) {
        return handle("查询 RAG 文档统计失败", () -> legacyRagDocumentApplicationService.documentStats(tag), Map.of());
    }

    @DeleteMapping("/document/chunks/{chunkId}")
    public Response<Boolean> deleteRagChunk(@PathVariable("chunkId") String chunkId) {
        return handleBoolean("删除 RAG chunk 失败", () -> legacyRagDocumentApplicationService.deleteChunk(chunkId));
    }

    @DeleteMapping("/document/by-tag/{tag}")
    public Response<Map<String, Object>> deleteRagChunksByTag(@PathVariable("tag") String tag) {
        return handle("按知识标签删除 RAG chunk 失败", () -> legacyRagDocumentApplicationService.deleteChunksByTag(tag), Map.of());
    }

    @GetMapping("/document/list")
    public Response<List<RagDocumentResponseDTO>> queryRagDocuments(@RequestParam(value = "tag", required = false) String tag) {
        return handle("查询文档失败", () -> legacyRagDocumentApplicationService.listDocuments(tag), List.of());
    }

    @GetMapping("/document/content")
    public Response<RagDocumentResponseDTO> queryRagDocumentContent(@RequestParam("fileName") String fileName) {
        return handle("查询文档内容失败", () -> legacyRagDocumentApplicationService.documentContent(fileName), null);
    }

    @PostMapping("/quality/probe")
    public Response<Map<String, Object>> probeRagQuality(@RequestBody Map<String, Object> request) {
        return handle("RAG 质量探测失败", () -> ragQualityEvalService.probe(request), Map.of());
    }

    @GetMapping("/quality/eval-cases")
    public Response<List<Map<String, Object>>> listRagEvalCases(@RequestParam(value = "enabled", required = false) Boolean enabled,
                                                                 @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return handle("查询 RAG 评测用例失败", () -> ragQualityEvalService.listCases(enabled, limit == null ? 100 : limit), List.of());
    }

    @PostMapping("/quality/eval-cases")
    public Response<Map<String, Object>> saveRagEvalCase(@RequestBody Map<String, Object> request) {
        return handle("保存 RAG 评测用例失败", () -> ragQualityEvalService.saveCase(request), Map.of());
    }

    @DeleteMapping("/quality/eval-cases/{id}")
    public Response<Boolean> deleteRagEvalCase(@PathVariable("id") Long id) {
        return handleBoolean("删除 RAG 评测用例失败", () -> ragQualityEvalService.deleteCase(id));
    }

    @PostMapping("/quality/eval-run")
    public Response<Map<String, Object>> runRagEval(@RequestBody(required = false) Map<String, Object> request) {
        return handle("运行 RAG 离线评测失败", () -> ragQualityEvalService.run(request), Map.of());
    }

    @PostMapping("/quality/feedback")
    public Response<Map<String, Object>> submitRagFeedback(@RequestBody(required = false) Map<String, Object> request) {
        return handle("提交 RAG 反馈失败", () -> ragFeedbackService.submitFeedback(request), Map.of());
    }

    @GetMapping("/quality/feedback")
    public Response<List<Map<String, Object>>> listRagFeedback(@RequestParam(value = "knowledgeTag", required = false) String knowledgeTag,
                                                               @RequestParam(value = "useful", required = false) Boolean useful,
                                                               @RequestParam(value = "resolved", required = false) Boolean resolved,
                                                               @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return handle("查询 RAG 反馈失败", () -> ragFeedbackService.listFeedback(knowledgeTag, useful, resolved, limit == null ? 100 : limit), List.of());
    }

    @GetMapping("/quality/knowledge-gaps")
    public Response<List<Map<String, Object>>> listRagKnowledgeGaps(@RequestParam(value = "status", required = false) String status,
                                                                    @RequestParam(value = "knowledgeTag", required = false) String knowledgeTag,
                                                                    @RequestParam(value = "limit", required = false, defaultValue = "100") Integer limit) {
        return handle("查询 RAG 知识缺口失败", () -> ragFeedbackService.listGaps(status, knowledgeTag, limit == null ? 100 : limit), List.of());
    }

    @PutMapping("/quality/knowledge-gaps/{id}/status")
    public Response<Boolean> updateRagKnowledgeGapStatus(@PathVariable("id") Long id,
                                                         @RequestParam("status") String status) {
        return handleBoolean("更新 RAG 知识缺口状态失败", () -> ragFeedbackService.updateGapStatus(id, status));
    }

    @PostMapping("/quality/knowledge-gaps/{id}/eval-case")
    public Response<Map<String, Object>> saveRagKnowledgeGapAsEvalCase(@PathVariable("id") Long id) {
        return handle("知识缺口转评测用例失败", () -> ragFeedbackService.saveGapAsEvalCase(id), Map.of());
    }

    private Response<Boolean> handleBoolean(String errorMessage, Supplier<Boolean> action) {
        return handle(errorMessage, action, false);
    }

    private Response<AiClientRagOrderResponseDTO> handleItem(String errorMessage,
                                                            Supplier<AiClientRagOrderResponseDTO> action) {
        return handle(errorMessage, action, null);
    }

    private Response<List<AiClientRagOrderResponseDTO>> handleList(String errorMessage,
                                                                   Supplier<List<AiClientRagOrderResponseDTO>> action) {
        return handle(errorMessage, action, null);
    }

    private <T> Response<T> handle(String errorMessage, Supplier<T> action, T errorData) {
        try {
            return success(action.get());
        } catch (Exception e) {
            log.error(errorMessage, e);
            return failure(errorMessage + "：" + e.getMessage(), errorData);
        }
    }

    private <T> Response<T> success(T data) {
        return Response.<T>builder()
                .code(ResponseCode.SUCCESS.getCode())
                .info(ResponseCode.SUCCESS.getInfo())
                .data(data)
                .build();
    }

    private <T> Response<T> failure(String message, T data) {
        return Response.<T>builder()
                .code(ResponseCode.UN_ERROR.getCode())
                .info(message)
                .data(data)
                .build();
    }

    private Response<Boolean> illegalParameter(String message) {
        return Response.<Boolean>builder()
                .code(ResponseCode.ILLEGAL_PARAMETER.getCode())
                .info(message)
                .data(false)
                .build();
    }

    private String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AdminWebSecurityConfig.AUTH_PRINCIPAL_ATTRIBUTE);
        if (value instanceof AdminAuthService.AuthPrincipal principal) {
            return principal.username();
        }
        throw new IllegalStateException("未获取到已认证操作者");
    }

    private List<RagFileResource> ragFiles(List<MultipartFile> files) {
        return files == null ? List.of() : files.stream()
                .map(MultipartRagFileResource::new)
                .map(RagFileResource.class::cast)
                .toList();
    }

    private Map<String, Object> aggregateView(KnowledgeAggregateSnapshot aggregate) {
        return Map.of(
                "knowledgeTag", aggregate.knowledgeBaseId(),
                "chunkCount", aggregate.chunkCount(),
                "documentCount", aggregate.documentCount(),
                "ragOrders", aggregate.ragOrders());
    }

    private Map<String, Object> safe(Map<String, Object> request) {
        return request == null ? Map.of() : request;
    }
}
