package cn.lgs.orbisops.trigger.application.memory;

import cn.lgs.orbisops.application.memory.SemanticMemoryClearFailurePort;
import cn.lgs.orbisops.application.memory.SemanticMemoryRetrievalFailurePort;
import cn.lgs.orbisops.application.memory.SemanticMemoryWriteFailurePort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** One-time degradation logger shared by semantic-memory adapters. */
@Slf4j
@Component
public class OpsSemanticRetrievalFailureAdapter implements SemanticMemoryRetrievalFailurePort,
        SemanticMemoryWriteFailurePort,
        SemanticMemoryClearFailurePort {

    private volatile boolean unavailableLogged;

    @Override
    public void onFailure(String operation, RuntimeException error) {
        String detail = error == null ? "unknown" : error.getMessage();
        if ("vector-recall".equals(operation)) {
            onDegraded("向量记忆召回失败：" + detail);
            return;
        }
        if ("lexical-recall".equals(operation)) {
            onDegraded("PostgreSQL FTS 记忆召回失败：" + detail);
            return;
        }
        onDegraded("语义记忆融合失败：" + detail);
    }

    @Override
    public void onWriteFailure(String operation, RuntimeException error) {
        String detail = error == null ? "unknown" : error.getMessage();
        if ("vector-write".equals(operation)) {
            onDegraded("向量记忆写入失败，改用 PostgreSQL FTS-only 写入：" + detail);
            return;
        }
        onDegraded("PostgreSQL FTS-only 语义记忆写入失败：" + detail);
    }

    @Override
    public void onClearFailure(RuntimeException error) {
        onDegraded("清理 PgVector 语义记忆失败："
                + (error == null ? "unknown" : error.getMessage()));
    }

    public void onDegraded(String message) {
        if (!unavailableLogged) {
            unavailableLogged = true;
            log.warn("运维对话语义记忆 PgVector 不可用，已降级：{}", message);
        }
    }
}
