package cn.lgs.orbisops.domain.runtime.graph.adapter.repository;

import cn.lgs.orbisops.domain.runtime.graph.model.GraphEvent;
import cn.lgs.orbisops.domain.runtime.graph.model.GraphEventDraft;

import java.util.List;
import java.util.Optional;

public interface IGraphEventRepository {

    Optional<GraphEvent> append(GraphEventDraft draft);

    List<GraphEvent> list(String scopeId, long afterSequence, int limit);
}
