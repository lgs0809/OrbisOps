package cn.lgs.orbisops.trigger.ops.rag;

import java.util.Locale;
import java.util.Set;

/** Pure file-name/content-type routing policy for the structured parser. */
final class RagDocumentKindDetector {

    private static final Set<String> CODE_EXTENSIONS = Set.of(
            "java", "kt", "go", "py", "js", "ts", "tsx", "jsx", "sql", "sh",
            "yaml", "yml", "xml", "properties");
    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "webp", "gif", "bmp", "tiff", "tif");

    RagDocumentKind detect(String fileName, String contentType) {
        String extension = extension(fileName);
        String type = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if ("md".equals(extension) || "markdown".equals(extension)) {
            return RagDocumentKind.MARKDOWN;
        }
        if ("html".equals(extension) || "htm".equals(extension) || type.contains("html")) {
            return RagDocumentKind.HTML;
        }
        if ("pdf".equals(extension) || type.contains("pdf")) {
            return RagDocumentKind.PDF;
        }
        if ("csv".equals(extension) || "tsv".equals(extension)) {
            return RagDocumentKind.TABLE_TEXT;
        }
        if ("xls".equals(extension) || "xlsx".equals(extension)) {
            return RagDocumentKind.TABLE_BINARY;
        }
        if (IMAGE_EXTENSIONS.contains(extension) || type.startsWith("image/")) {
            return RagDocumentKind.IMAGE;
        }
        if (isConversationName(lowerName)) {
            return RagDocumentKind.CONVERSATION;
        }
        if (CODE_EXTENSIONS.contains(extension)) {
            return RagDocumentKind.CODE;
        }
        if (type.startsWith("text/") || Set.of("txt", "log", "json").contains(extension)) {
            return RagDocumentKind.TEXT;
        }
        return RagDocumentKind.TIKA;
    }

    private boolean isConversationName(String lowerName) {
        return lowerName.contains("chat")
                || lowerName.contains("conversation")
                || lowerName.contains("ticket")
                || lowerName.contains("workorder")
                || lowerName.contains("工单")
                || lowerName.contains("聊天")
                || lowerName.contains("会话");
    }

    private String extension(String fileName) {
        if (fileName == null || fileName.isBlank() || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1)
                .toLowerCase(Locale.ROOT);
    }
}
