package cn.lgs.orbisops.trigger.ops.rag;

/** Stable parser routing kinds and public document-type metadata values. */
enum RagDocumentKind {
    MARKDOWN("markdown"),
    HTML("html"),
    PDF("pdf"),
    TABLE_TEXT("table"),
    TABLE_BINARY("table"),
    CODE("code"),
    CONVERSATION("conversation"),
    IMAGE("image"),
    TEXT("text"),
    TIKA("generic");

    private final String documentType;

    RagDocumentKind(String documentType) {
        this.documentType = documentType;
    }

    String documentType() {
        return documentType;
    }
}
