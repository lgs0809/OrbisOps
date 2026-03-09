package cn.lgs.orbisops.trigger.ops.rag;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Matches and extends figure captions near embedded PDF images. */
public final class RagPdfFigureCaptionPolicy {

    private static final Pattern FIGURE_CAPTION_PATTERN = Pattern.compile(
            "(?i)^\\s*(?:fig\\.?|figure)\\s*[0-9IVXivxA-Za-z._-]+\\s*[:.\\-\\s].*"
                    + "|^\\s*图\\s*[0-9一二三四五六七八九十._-]+\\s*[:：.\\-\\s]?.*");

    public String captionForImage(
            List<RagPdfTextLayoutExtractor.TextLine> lines,
            int page,
            double imageBottomY) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        List<RagPdfTextLayoutExtractor.TextLine> pageLines = lines.stream()
                .filter(line -> line.page() == page)
                .sorted(Comparator.comparingDouble(
                        RagPdfTextLayoutExtractor.TextLine::y))
                .toList();
        int captionIndex = -1;
        for (int i = 0; i < pageLines.size(); i++) {
            RagPdfTextLayoutExtractor.TextLine line = pageLines.get(i);
            if (line.y() + 4D < imageBottomY
                    || line.y() > imageBottomY + 180D) {
                continue;
            }
            if (FIGURE_CAPTION_PATTERN.matcher(line.text()).matches()) {
                captionIndex = i;
                break;
            }
        }
        if (captionIndex < 0) {
            return "";
        }

        StringBuilder caption = new StringBuilder(
                pageLines.get(captionIndex).text());
        RagPdfTextLayoutExtractor.TextLine previous =
                pageLines.get(captionIndex);
        for (int i = captionIndex + 1;
             i < pageLines.size() && caption.length() < 600;
             i++) {
            RagPdfTextLayoutExtractor.TextLine line = pageLines.get(i);
            double gap = line.y() - previous.y();
            if (gap > Math.max(18D, previous.height() * 1.8D)
                    || FIGURE_CAPTION_PATTERN.matcher(line.text()).matches()) {
                break;
            }
            caption.append(' ').append(line.text());
            previous = line;
        }
        return caption.toString().trim();
    }
}
