package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Source-code structure extraction boundary.
 *
 * Owns package discovery, symbol boundary recognition, line ranges and the
 * existing length-triggered code grouping behavior while emitting neutral drafts.
 */
public final class RagCodeChunkExtractor {

    private static final Pattern CODE_BOUNDARY_PATTERN = Pattern.compile(
            "^\\s*(?:public\\s+|private\\s+|protected\\s+|static\\s+|final\\s+|async\\s+|export\\s+|abstract\\s+|class\\s+|interface\\s+|enum\\s+|record\\s+|def\\s+|function\\s+|const\\s+|let\\s+|var\\s+).*[A-Za-z_][\\w$]*(?:\\s*\\(|\\s*\\{|\\s*:|\\s*=).*");
    private static final Pattern CODE_PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([A-Za-z0-9_.]+)\\s*;");

    private final RagChunkMaterializer chunkMaterializer;

    public RagCodeChunkExtractor(RagChunkMaterializer chunkMaterializer) {
        if (chunkMaterializer == null) {
            throw new IllegalArgumentException("RAG_CHUNK_MATERIALIZER_REQUIRED");
        }
        this.chunkMaterializer = chunkMaterializer;
    }

    public List<RagChunkDraft> extract(String text, Map<String, Object> baseMetadata) {
        String[] lines = normalize(text).split("\n", -1);
        String packageName = Arrays.stream(lines)
                .map(CODE_PACKAGE_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group(1))
                .findFirst()
                .orElse("");

        List<RagChunkDraft> drafts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String symbol = packageName;
        int lineStart = 1;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean boundary = CODE_BOUNDARY_PATTERN.matcher(line).matches();
            if (boundary && current.length() > chunkMaterializer.maxSegmentChars(baseMetadata) / 3) {
                drafts.add(draft(current.toString(), baseMetadata, packageName, symbol, lineStart, i));
                current.setLength(0);
                lineStart = i + 1;
            }
            if (boundary) {
                symbol = codeSymbol(line);
            }
            current.append(line).append('\n');
            if (current.length() > chunkMaterializer.maxSegmentChars(baseMetadata)) {
                drafts.add(draft(current.toString(), baseMetadata, packageName, symbol, lineStart, i + 1));
                current.setLength(0);
                lineStart = i + 2;
            }
        }

        if (!current.isEmpty()) {
            drafts.add(draft(current.toString(), baseMetadata, packageName, symbol, lineStart, lines.length));
        }
        return List.copyOf(drafts);
    }

    private RagChunkDraft draft(String text,
                                Map<String, Object> baseMetadata,
                                String packageName,
                                String symbol,
                                int lineStart,
                                int lineEnd) {
        return RagChunkDraft.exact(text, chunkMaterializer.mergeMetadata(baseMetadata,
                "chunk_strategy", "code-symbol",
                "package", packageName,
                "symbol", symbol,
                "line_start", lineStart,
                "line_end", lineEnd));
    }

    private String codeSymbol(String line) {
        String cleaned = line.trim().replaceAll("[{(:=].*$", "").trim();
        String[] parts = cleaned.split("\\s+");
        return parts.length == 0 ? cleaned : parts[parts.length - 1];
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value;
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        return normalized.replace("\r\n", "\n").replace('\r', '\n');
    }
}
