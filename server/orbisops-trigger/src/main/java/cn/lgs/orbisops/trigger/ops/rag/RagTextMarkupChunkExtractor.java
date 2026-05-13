package cn.lgs.orbisops.trigger.ops.rag;

import cn.lgs.orbisops.domain.knowledge.rag.model.RagChunkDraft;
import cn.lgs.orbisops.domain.knowledge.rag.service.RagChunkMaterializer;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Markdown and HTML extraction boundary that emits framework-neutral chunk drafts.
 *
 * The extractor owns heading hierarchy, section evidence, Markdown image references,
 * markup sniffing and the existing regex-based HTML-to-Markdown compatibility chain.
 */
public final class RagTextMarkupChunkExtractor {

    private static final Pattern MARKDOWN_HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern MARKDOWN_IMAGE_PATTERN = Pattern.compile(
            "!\\[([^\\]]*)]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");

    private final RagChunkMaterializer chunkMaterializer;

    public RagTextMarkupChunkExtractor(RagChunkMaterializer chunkMaterializer) {
        if (chunkMaterializer == null) {
            throw new IllegalArgumentException("RAG_CHUNK_MATERIALIZER_REQUIRED");
        }
        this.chunkMaterializer = chunkMaterializer;
    }

    public List<RagChunkDraft> extractMarkdown(String text,
                                               Map<String, Object> baseMetadata,
                                               String strategy) {
        List<RagChunkDraft> drafts = new ArrayList<>();
        String normalized = normalize(text);
        String[] lines = normalized.split("\n", -1);
        String[] headingStack = new String[6];
        StringBuilder section = new StringBuilder();
        String currentHeadingPath = "ROOT";
        String currentTitle = "ROOT";
        boolean inFence = false;
        int sectionIndex = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                inFence = !inFence;
            }
            Matcher headingMatcher = MARKDOWN_HEADING_PATTERN.matcher(line);
            if (!inFence && headingMatcher.matches()) {
                if (!section.isEmpty()) {
                    drafts.add(sectionDraft(
                            section.toString(),
                            baseMetadata,
                            strategy,
                            sectionIndex++,
                            currentTitle,
                            currentHeadingPath));
                    section.setLength(0);
                }
                int level = headingMatcher.group(1).length();
                currentTitle = cleanupTitle(headingMatcher.group(2));
                headingStack[level - 1] = currentTitle;
                for (int i = level; i < headingStack.length; i++) {
                    headingStack[i] = null;
                }
                currentHeadingPath = Arrays.stream(headingStack)
                        .filter(this::hasText)
                        .collect(Collectors.joining(" > "));
            }
            if (!inFence) {
                appendImageDrafts(drafts, line, baseMetadata, currentHeadingPath, currentTitle);
            }
            section.append(line).append('\n');
        }

        if (!section.isEmpty()) {
            drafts.add(sectionDraft(
                    section.toString(),
                    baseMetadata,
                    strategy,
                    sectionIndex,
                    currentTitle,
                    currentHeadingPath));
        }
        return List.copyOf(drafts);
    }

    public List<RagChunkDraft> extractHtml(String html, Map<String, Object> baseMetadata) {
        return extractMarkdown(htmlToMarkdown(html), baseMetadata, "html-to-markdown");
    }

    public boolean looksLikeMarkdown(String text) {
        return normalize(text).lines()
                .limit(80)
                .anyMatch(line -> MARKDOWN_HEADING_PATTERN.matcher(line).matches()
                        || line.trim().startsWith("```"));
    }

    private RagChunkDraft sectionDraft(String text,
                                       Map<String, Object> baseMetadata,
                                       String strategy,
                                       int sectionIndex,
                                       String sectionTitle,
                                       String headingPath) {
        return RagChunkDraft.structureBounded(text, chunkMaterializer.mergeMetadata(baseMetadata,
                "chunk_strategy", strategy,
                "section_index", sectionIndex,
                "section_title", sectionTitle,
                "heading_path", headingPath));
    }

    private void appendImageDrafts(List<RagChunkDraft> drafts,
                                   String line,
                                   Map<String, Object> baseMetadata,
                                   String headingPath,
                                   String sectionTitle) {
        Matcher matcher = MARKDOWN_IMAGE_PATTERN.matcher(line == null ? "" : line);
        while (matcher.find()) {
            String alt = matcher.group(1) == null ? "" : matcher.group(1).trim();
            String imagePath = stripImagePath(matcher.group(2));
            if (!hasText(imagePath)) {
                continue;
            }
            String text = """
                    # Markdown image reference

                    Alt text: %s
                    Image path: %s
                    Section: %s

                    This chunk represents an image reference in a Markdown document. If the referenced image file is available to the multimodal model index, it can be embedded together with this textual context.
                    """.formatted(value(alt), imagePath, value(headingPath));
            drafts.add(RagChunkDraft.exact(text, chunkMaterializer.mergeMetadata(baseMetadata,
                    "chunk_strategy", "markdown-image-reference",
                    "chunk_type", "image_reference",
                    "embedding_input_modality", "text",
                    "multimodal_embedding_strategy", "linked_image_when_available",
                    "image_reference_type", "markdown",
                    "image_path", imagePath,
                    "image_alt", alt,
                    "section_title", sectionTitle,
                    "heading_path", headingPath)));
        }
    }

    private String htmlToMarkdown(String html) {
        String value = normalize(html)
                .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", "\n")
                .replaceAll("(?is)<!--.*?-->", "\n")
                .replaceAll("(?i)<h1[^>]*>", "\n\n# ")
                .replaceAll("(?i)</h1>", "\n\n")
                .replaceAll("(?i)<h2[^>]*>", "\n\n## ")
                .replaceAll("(?i)</h2>", "\n\n")
                .replaceAll("(?i)<h3[^>]*>", "\n\n### ")
                .replaceAll("(?i)</h3>", "\n\n")
                .replaceAll("(?i)<h4[^>]*>", "\n\n#### ")
                .replaceAll("(?i)</h4>", "\n\n")
                .replaceAll("(?i)<h5[^>]*>", "\n\n##### ")
                .replaceAll("(?i)</h5>", "\n\n")
                .replaceAll("(?i)<h6[^>]*>", "\n\n###### ")
                .replaceAll("(?i)</h6>", "\n\n")
                .replaceAll("(?i)<li[^>]*>", "\n- ")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)<pre[^>]*>", "\n\n```\n")
                .replaceAll("(?i)</pre>", "\n```\n\n")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>|</div>|</section>|</article>", "\n\n")
                .replaceAll("(?i)<tr[^>]*>", "\n| ")
                .replaceAll("(?i)</tr>", " |\n")
                .replaceAll("(?i)</t[dh]>", " | ")
                .replaceAll("(?is)<[^>]+>", "");
        return htmlUnescape(value);
    }

    private String cleanupTitle(String title) {
        return title == null ? "" : title.replaceAll("#+$", "").trim();
    }

    private String stripImagePath(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.trim();
        if (cleaned.startsWith("<") && cleaned.endsWith(">") && cleaned.length() > 1) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        return cleaned;
    }

    private String htmlUnescape(String value) {
        return value.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value;
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }
        return normalized.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String value(String value) {
        return hasText(value) ? value : "";
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
