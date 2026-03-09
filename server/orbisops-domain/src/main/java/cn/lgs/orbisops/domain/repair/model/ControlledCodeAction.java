package cn.lgs.orbisops.domain.repair.model;

public enum ControlledCodeAction {
    READ("code.read"),
    GREP("code.grep"),
    GLOB("code.glob"),
    EDIT("code.edit"),
    WRITE("code.write"),
    BASH("code.bash"),
    LSP("code.lsp"),
    ENTER_WORKTREE("code.enter_worktree"),
    EXIT_WORKTREE("code.exit_worktree"),
    DIFF("code.diff"),
    COMMIT("code.commit_repair");

    private final String auditName;

    ControlledCodeAction(String auditName) {
        this.auditName = auditName;
    }

    public String auditName() {
        return auditName;
    }
}
