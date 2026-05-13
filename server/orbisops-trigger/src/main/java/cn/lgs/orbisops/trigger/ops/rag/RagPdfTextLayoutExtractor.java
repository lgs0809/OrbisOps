package cn.lgs.orbisops.trigger.ops.rag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/** Extracts positioned PDF text fragments and assembles stable visual lines. */
public final class RagPdfTextLayoutExtractor {

    public List<TextLine> extract(PDDocument pdf) throws IOException {
        if (pdf == null) throw new IllegalArgumentException("PDF_DOCUMENT_REQUIRED");
        Collector collector = new Collector();
        collector.setSortByPosition(true);
        collector.getText(pdf);
        return collector.lines();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class Collector extends PDFTextStripper {

        private final List<TextFragment> fragments = new ArrayList<>();

        private Collector() throws IOException {
            super();
        }

        @Override
        protected void writeString(
                String text,
                List<TextPosition> textPositions) {
            if (!hasText(text)
                    || textPositions == null
                    || textPositions.isEmpty()) {
                return;
            }
            double minX = textPositions.stream()
                    .mapToDouble(TextPosition::getXDirAdj)
                    .min()
                    .orElse(0D);
            double maxX = textPositions.stream()
                    .mapToDouble(position -> position.getXDirAdj()
                            + position.getWidthDirAdj())
                    .max()
                    .orElse(minX);
            double y = textPositions.stream()
                    .mapToDouble(TextPosition::getYDirAdj)
                    .average()
                    .orElse(0D);
            double height = textPositions.stream()
                    .mapToDouble(TextPosition::getHeightDir)
                    .max()
                    .orElse(12D);
            fragments.add(new TextFragment(
                    getCurrentPageNo(),
                    text.trim(),
                    minX,
                    y,
                    Math.max(0D, maxX - minX),
                    height));
        }

        private List<TextLine> lines() {
            List<TextFragment> ordered = fragments.stream()
                    .filter(fragment -> hasText(fragment.text()))
                    .sorted(Comparator.comparingInt(TextFragment::page)
                            .thenComparingDouble(TextFragment::y)
                            .thenComparingDouble(TextFragment::x))
                    .toList();
            List<LineBuilder> builders = new ArrayList<>();
            for (TextFragment fragment : ordered) {
                LineBuilder current = builders.isEmpty()
                        ? null
                        : builders.get(builders.size() - 1);
                double tolerance = Math.max(2.5D, fragment.height() * 0.45D);
                if (current == null
                        || current.page != fragment.page()
                        || Math.abs(current.y - fragment.y()) > tolerance) {
                    builders.add(new LineBuilder(
                            fragment.page(),
                            fragment.x(),
                            fragment.y(),
                            fragment.height()));
                    current = builders.get(builders.size() - 1);
                }
                current.add(fragment);
            }
            AtomicInteger lineNumber = new AtomicInteger(1);
            return builders.stream()
                    .map(builder -> builder.toLine(lineNumber.getAndIncrement()))
                    .filter(line -> hasText(line.text()))
                    .toList();
        }
    }

    private static final class LineBuilder {
        private final int page;
        private final double y;
        private double x;
        private double height;
        private final List<TextFragment> fragments = new ArrayList<>();

        private LineBuilder(
                int page,
                double x,
                double y,
                double height) {
            this.page = page;
            this.x = x;
            this.y = y;
            this.height = height;
        }

        private void add(TextFragment fragment) {
            fragments.add(fragment);
            x = Math.min(x, fragment.x());
            height = Math.max(height, fragment.height());
        }

        private TextLine toLine(int lineNumber) {
            String text = fragments.stream()
                    .sorted(Comparator.comparingDouble(TextFragment::x))
                    .map(TextFragment::text)
                    .collect(Collectors.joining(" "))
                    .replaceAll("\\s+", " ")
                    .trim();
            return new TextLine(page, lineNumber, text, x, y, height);
        }
    }

    private record TextFragment(
            int page,
            String text,
            double x,
            double y,
            double width,
            double height) {
    }

    public record TextLine(
            int page,
            int lineNumber,
            String text,
            double x,
            double y,
            double height) {
    }
}
