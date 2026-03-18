package cn.lgs.orbisops.application.source;

import cn.lgs.orbisops.domain.source.model.SourceRepository;

public interface SourceMcpProjectionPort {
    void publish(SourceRepository repository);
}
