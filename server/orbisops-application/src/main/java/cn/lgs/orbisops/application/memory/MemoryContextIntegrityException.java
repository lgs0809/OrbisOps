package cn.lgs.orbisops.application.memory;

/** A protected historical source cannot be silently removed to fit a context budget. */
public class MemoryContextIntegrityException extends IllegalStateException {
    public MemoryContextIntegrityException(String code) {
        super(code);
    }
}
