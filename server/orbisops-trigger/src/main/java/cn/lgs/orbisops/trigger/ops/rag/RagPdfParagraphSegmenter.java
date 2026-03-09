package cn.lgs.orbisops.trigger.ops.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Segments positioned PDF text lines into structure-bounded paragraphs. */
public final class RagPdfParagraphSegmenter {

    public List<Paragraph> segment(
            List<RagPdfTextLayoutExtractor.TextLine> lines,
            int maxSegmentChars) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        List<RagPdfTextLayoutExtractor.TextLine> ordered = lines.stream()
                .filter(line -> hasText(line.text()))
                .sorted(Comparator.comparingInt(RagPdfTextLayoutExtractor.TextLine::page)
                        .thenComparingDouble(RagPdfTextLayoutExtractor.TextLine::y)
                        .thenComparingDouble(RagPdfTextLayoutExtractor.TextLine::x))
                .toList();
        if (ordered.isEmpty()) {
            return List.of();
        }

        double medianHeight = medianLineHeight(ordered);
        double paragraphGap = Math.max(10D, medianHeight * 1.8D);
        List<Paragraph> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        RagPdfTextLayoutExtractor.TextLine first = null;
        RagPdfTextLayoutExtractor.TextLine previous = null;

        for (RagPdfTextLayoutExtractor.TextLine line : ordered) {
            if (first == null) {
                first = line;
                appendLine(current, line.text());
                previous = line;
                continue;
            }

            boolean pageChanged = previous.page() != line.page();
            double gap = pageChanged ? 0D : Math.abs(line.y() - previous.y());
            boolean breakParagraph = pageChanged
                    ? endsWithSentenceBoundary(current)
                    : gap > paragraphGap
                    || current.length() + line.text().length() > maxSegmentChars;
            if (breakParagraph) {
                paragraphs.add(new Paragraph(
                        current.toString().trim(),
                        first.page(),
                        previous.page(),
                        first.lineNumber(),
                        previous.lineNumber()));
                current.setLength(0);
                first = line;
            }
            appendLine(current, line.text());
            previous = line;
        }

        if (!current.isEmpty() && first != null && previous != null) {
            paragraphs.add(new Paragraph(
                    current.toString().trim(),
                    first.page(),
                    previous.page(),
                    first.lineNumber(),
                    previous.lineNumber()));
        }
        return List.copyOf(paragraphs);
    }

    private void appendLine(StringBuilder current, String line) {
        String cleaned = line == null ? "" : line.trim();
        if (!hasText(cleaned)) {
            return;
        }
        if (current.isEmpty()) {
            current.append(cleaned);
            return;
        }
        if (current.charAt(current.length() - 1) == '-') {
            current.deleteCharAt(current.length() - 1).append(cleaned);
        } else {
            current.append(' ').append(cleaned);
        }
    }

    private boolean endsWithSentenceBoundary(CharSequence text) {
        if (text == null || text.isEmpty()) {
            return true;
        }
        String value = text.toString().trim();
        if (!hasText(value)) {
            return true;
        }
        char last = value.charAt(value.length() - 1);
        return "。.!?！？;；:：)）]】\"'".indexOf(last) >= 0;
    }

    private double medianLineHeight(
            List<RagPdfTextLayoutExtractor.TextLine> lines) {
        List<Double> heights = lines.stream()
                .map(RagPdfTextLayoutExtractor.TextLine::height)
                .filter(height -> height > 0D)
                .sorted()
                .toList();
        if (heights.isEmpty()) {
            return 12D;
        }
        return heights.get(heights.size() / 2);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Paragraph(
            String text,
            int pageStart,
            int pageEnd,
            int lineStart,
            int lineEnd) {
    }
}
