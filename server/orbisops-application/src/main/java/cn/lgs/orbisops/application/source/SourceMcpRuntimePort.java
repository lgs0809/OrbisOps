package cn.lgs.orbisops.application.source;

import cn.lgs.orbisops.domain.source.model.SourceMcpRuntimeSpec;
import cn.lgs.orbisops.domain.source.model.SourceRepository;

public interface SourceMcpRuntimePort {
    SourceMcpRuntimeSpec describe(SourceRepository repository);
}
