package cn.lgs.orbisops.domain.agentdefinition.rule;

public sealed interface WorkflowRule
        permits AllRule, AnyRule, NotRule, ComparisonRule,
                ExistsRule, InRule, ChangedRule {
}
