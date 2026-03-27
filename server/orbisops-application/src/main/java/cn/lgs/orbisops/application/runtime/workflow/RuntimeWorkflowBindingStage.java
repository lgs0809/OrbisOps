package cn.lgs.orbisops.application.runtime.workflow;

public interface RuntimeWorkflowBindingStage {

    String stageId();

    int order();

    void apply(RuntimeWorkflowBindingContext context);
}
