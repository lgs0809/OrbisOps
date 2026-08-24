package cn.lgs.orbisops.domain.memory.model;

/** A fixed invalid source must be blocked, rather than retried as a transient model outage. */
public class MemoryModelInputInvalidException extends IllegalArgumentException {
    public MemoryModelInputInvalidException(String reason) {super(reason);}
    public MemoryModelInputInvalidException(String reason,Throwable cause) {super(reason,cause);}
}
