package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagFileResource;
import cn.lgs.orbisops.application.model.ModelAvailabilityPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RagVisualDocumentAnalyzer {

    private final RagVisualAnalysisAvailability availability;
    private final RagVisualMediaPreparer mediaPreparer;
    private final RagVisualAnalysisProtocol protocol;
    private final RagVisualDocumentProjector projector;

    public RagVisualDocumentAnalyzer() {
        this(RagVisualAnalysisSettings.defaults(), (ModelAvailabilityPort) null);
    }

    @Autowired
    public RagVisualDocumentAnalyzer(
            RagVisualAnalysisSettings settings,
            ObjectProvider<ModelAvailabilityPort> availabilityProvider) {
        this(settings, availabilityProvider.getIfAvailable());
    }

    public RagVisualDocumentAnalyzer(
            RagVisualAnalysisSettings settings,
            ModelAvailabilityPort aiModelAvailability) {
        this(
                new RagVisualAnalysisAvailability(settings, aiModelAvailability),
                new RagVisualMediaPreparer(settings),
                new RagVisualAnalysisProtocol(settings),
                new RagVisualDocumentProjector(settings));
    }

    RagVisualDocumentAnalyzer(
            RagVisualAnalysisAvailability availability,
            RagVisualMediaPreparer mediaPreparer,
            RagVisualAnalysisProtocol protocol,
            RagVisualDocumentProjector projector) {
        if (availability == null) throw new IllegalArgumentException("RAG_VISUAL_AVAILABILITY_REQUIRED");
        if (mediaPreparer == null) throw new IllegalArgumentException("RAG_VISUAL_MEDIA_PREPARER_REQUIRED");
        if (protocol == null) throw new IllegalArgumentException("RAG_VISUAL_PROTOCOL_REQUIRED");
        if (projector == null) throw new IllegalArgumentException("RAG_VISUAL_PROJECTOR_REQUIRED");
        this.availability = availability;
        this.mediaPreparer = mediaPreparer;
        this.protocol = protocol;
        this.projector = projector;
    }

    public boolean shouldAnalyze(Map<String, Object> metadata) {
        return availability.shouldAnalyze(metadata);
    }

    public List<Document> analyzeImage(
            RagFileResource file,
            Map<String, Object> baseMetadata) {
        try {
            RagVisualMediaPreparer.PreparedImage image = mediaPreparer.prepareImage(file);
            Map<String, Object> metadata = projector.sourceMetadata(baseMetadata, image);
            return List.of(analyzePreparedImage(image, metadata));
        } catch (Exception e) {
            log.warn(
                    "图片视觉解析失败 file={} reason={}",
                    baseMetadata.get("source"),
                    e.getMessage());
            return List.of(projector.failure(
                    "Image visual extraction failed: " + e.getMessage(),
                    baseMetadata));
        }
    }

    public List<Document> analyzePdf(
            RagFileResource file,
            Map<String, Object> baseMetadata) {
        List<Document> documents = new ArrayList<>();
        try {
            RagVisualMediaPreparer.PdfPreparation preparation = mediaPreparer.preparePdf(file);
            for (RagVisualMediaPreparer.PreparedImage image : preparation.pages()) {
                Map<String, Object> metadata = projector.sourceMetadata(baseMetadata, image);
                documents.add(analyzePreparedImage(image, metadata));
            }
            if (preparation.failure() != null) {
                throw preparation.failure();
            }
        } catch (Exception e) {
            log.warn(
                    "PDF 视觉解析失败 file={} reason={}",
                    baseMetadata.get("source"),
                    e.getMessage());
            documents.add(projector.failure(
                    "PDF visual extraction failed: " + e.getMessage(),
                    baseMetadata));
        }
        return documents;
    }

    private Document analyzePreparedImage(
            RagVisualMediaPreparer.PreparedImage image,
            Map<String, Object> metadata) throws Exception {
        if (!image.accepted()) {
            return projector.tooLarge(image, metadata);
        }
        RagVisualAnalysisProtocol.AnalysisResponse response = protocol.analyze(
                image.bytes(),
                image.mimeType());
        if (response.refused()) {
            return projector.refused(response.refusal(), metadata);
        }
        return projector.project(response.content(), metadata, image.index());
    }
}
