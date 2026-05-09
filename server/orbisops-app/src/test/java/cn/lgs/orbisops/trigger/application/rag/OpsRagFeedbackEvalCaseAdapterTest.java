package cn.lgs.orbisops.trigger.application.rag;

import cn.lgs.orbisops.application.rag.RagQualityCaseCatalogUseCase;
import cn.lgs.orbisops.application.rag.RagQualityCaseRecord;
import cn.lgs.orbisops.application.rag.RagQualityCaseSaveCommand;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpsRagFeedbackEvalCaseAdapterTest {

    @Test
    void delegatesToTypedQualityCaseCatalog() {
        RagQualityCaseCatalogUseCase catalog = mock(RagQualityCaseCatalogUseCase.class);
        OpsRagFeedbackEvalCaseAdapter adapter = new OpsRagFeedbackEvalCaseAdapter(catalog);
        RagQualityCaseSaveCommand command = new RagQualityCaseSaveCommand(
                null, "知识缺口 #9", "问题", "ops", List.of(), 8, true);
        RagQualityCaseRecord saved = new RagQualityCaseRecord(
                15L, "知识缺口 #9", "问题", "ops", List.of(), 8, true);
        when(catalog.save(command)).thenReturn(saved);

        assertEquals(saved, adapter.save(command));
        verify(catalog).save(command);
    }
}
