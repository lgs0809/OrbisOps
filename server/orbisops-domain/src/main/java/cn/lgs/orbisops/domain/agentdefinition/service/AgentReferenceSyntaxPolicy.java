package cn.lgs.orbisops.domain.agentdefinition.service;

/** Domain syntax invariants for capability and resource identifiers referenced by Agent definitions. */
public final class AgentReferenceSyntaxPolicy {

    public void validateSkill(String skill, String owner) {
        if (!hasText(skill) || !skill.matches("[A-Za-z0-9_.-]+")) {
            throw new IllegalArgumentException(owner + " 引用了非法 skill：" + skill);
        }
    }

    public void validateResourceId(String id, String owner) {
        if (hasText(id) && !id.matches("[A-Za-z0-9_.:-]+")) {
            throw new IllegalArgumentException(owner + " 引用了非法 ID：" + id);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }
}
