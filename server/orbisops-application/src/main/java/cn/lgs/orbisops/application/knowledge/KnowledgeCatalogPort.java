package cn.lgs.orbisops.application.knowledge;

import java.util.List;

public interface KnowledgeCatalogPort<J, F> {
    J importGlobalDocuments(String kbId, String name, List<F> files);
    J importProjectDocuments(String projectId, String kbId, String name, List<F> files);
}
