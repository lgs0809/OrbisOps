package cn.lgs.orbisops.domain.memory.model;

/** A secondary projection is visible only through its authoritative retained source and completed epoch. */
public record SemanticProjectionReference(String documentId,long jobId,long epoch,String sourceHash,String kind) {}
