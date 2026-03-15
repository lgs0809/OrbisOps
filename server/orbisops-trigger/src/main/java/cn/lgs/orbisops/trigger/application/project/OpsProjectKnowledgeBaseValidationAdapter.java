package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectKnowledgeBaseValidationPort;
import cn.lgs.orbisops.application.rag.RagOrderCatalogPort;
import cn.lgs.orbisops.application.rag.RagOrderDefinition;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpsProjectKnowledgeBaseValidationAdapter
        implements ProjectKnowledgeBaseValidationPort {

    private final RagOrderCatalogPort repository;

    public OpsProjectKnowledgeBaseValidationAdapter(
            RagOrderCatalogPort repository) {
        this.repository = repository;
    }

    @Override
    public void validateIfConfigured(String knowledgeBaseId) {
        String id = knowledgeBaseId == null ? "" : knowledgeBaseId.trim();
        if (id.isBlank()) {
            return;
        }
        RagOrderDefinition record = repository.queryByRagId(id);
        boolean enabled = record != null && Integer.valueOf(1).equals(record.status());
        if (!enabled) {
            List<RagOrderDefinition> matches = repository.queryByKnowledgeTag(id);
            enabled = matches != null && matches.stream()
                    .anyMatch(item -> item != null
                            && Integer.valueOf(1).equals(item.status()));
        }
        if (!enabled) {
            throw new IllegalArgumentException("知识库不存在或未启用：" + id);
        }
    }
}
