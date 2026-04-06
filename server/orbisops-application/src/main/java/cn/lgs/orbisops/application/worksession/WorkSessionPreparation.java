package cn.lgs.orbisops.application.worksession;

public record WorkSessionPreparation<C, O>(C context, O terminalResponse) {

    public static <C, O> WorkSessionPreparation<C, O> ready(C context) {
        if (context == null) throw new IllegalArgumentException("WORK_SESSION_CONTEXT_REQUIRED");
        return new WorkSessionPreparation<>(context, null);
    }

    public static <C, O> WorkSessionPreparation<C, O> terminal(O response) {
        if (response == null) throw new IllegalArgumentException("WORK_SESSION_TERMINAL_RESPONSE_REQUIRED");
        return new WorkSessionPreparation<>(null, response);
    }

    public boolean terminal() {
        return terminalResponse != null;
    }
}
