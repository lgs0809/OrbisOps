package cn.lgs.orbisops.application.analysis;

/** Cooperative cancellation signal raised inside asynchronous analysis execution. */
public class AsyncAnalysisCanceledException extends RuntimeException {

    public AsyncAnalysisCanceledException(String message) {
        super(message);
    }
}
