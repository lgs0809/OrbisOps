package cn.lgs.orbisops.trigger.ops.toolset;

import java.util.List;

public interface OpsBuiltInToolsetContributor {

    String contributorId();

    int order();

    List<OpsToolsetDefinition> definitions();
}
