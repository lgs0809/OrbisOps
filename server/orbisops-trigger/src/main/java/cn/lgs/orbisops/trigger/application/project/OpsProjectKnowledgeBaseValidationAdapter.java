package cn.lgs.orbisops.trigger.application.project;

import cn.lgs.orbisops.application.project.ProjectKnowledgeBaseValidationPort;
import cn.lgs.orbisops.domain.agent.adapter.repository.IAiClientRagOrderConfigRepository;
import cn.lgs.orbisops.domain.agent.model.entity.AiClientConfigRecord;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OpsProjectKnowledgeBaseValidationAdapter
        implements ProjectKnowledgeBaseValidationPort {

    private final IAiClientRagOrderConfigRepository repository;

    public OpsProjectKnowledgeBaseValidationAdapter(
            IAiClientRagOrderConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public void validateIfConfigured(String knowledgeBaseId) {
        String id = knowledgeBaseId == null ? "" : knowledgeBaseId.trim();
        if (id.isBlank()) {
            return;
        }
        AiClientConfigRecord record = repository.queryByRagId(id);
        boolean enabled = record != null && Integer.valueOf(1).equals(record.getStatus());
        if (!enabled) {
            List<AiClientConfigRecord> matches = repository.queryByKnowledgeTag(id);
            enabled = matches != null && matches.stream()
                    .anyMatch(item -> item != null
                            && Integer.valueOf(1).equals(item.getStatus()));
        }
        if (!enabled) {
            throw new IllegalArgumentException("知识库不存在或未启用：" + id);
        }
    }
}
