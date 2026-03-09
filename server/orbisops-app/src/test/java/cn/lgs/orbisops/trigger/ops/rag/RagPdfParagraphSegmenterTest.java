package cn.lgs.orbisops.trigger.ops.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPdfParagraphSegmenterTest {

    private final RagPdfParagraphSegmenter segmenter =
            new RagPdfParagraphSegmenter();

    @Test
    void nearbyLinesMustJoinAndHyphenatedLineMustRemoveBoundaryHyphen() {
        List<RagPdfParagraphSegmenter.Paragraph> paragraphs = segmenter.segment(
                List.of(
                        line(1, 1, "error-", 100D),
                        line(1, 2, "rate increased.", 112D)),
                1000);

        assertEquals(1, paragraphs.size());
        assertEquals("errorrate increased.", paragraphs.get(0).text());
        assertEquals(1, paragraphs.get(0).pageStart());
        assertEquals(1, paragraphs.get(0).pageEnd());
        assertEquals(1, paragraphs.get(0).lineStart());
        assertEquals(2, paragraphs.get(0).lineEnd());
    }

    @Test
    void largeGapAndMaxLengthMustCreateNewParagraphs() {
        List<RagPdfParagraphSegmenter.Paragraph> gap = segmenter.segment(
                List.of(
                        line(1, 1, "first", 100D),
                        line(1, 2, "second", 140D)),
                1000);
        List<RagPdfParagraphSegmenter.Paragraph> length = segmenter.segment(
                List.of(
                        line(1, 1, "1234567890", 100D),
                        line(1, 2, "abcdefghij", 112D)),
                15);

        assertEquals(List.of("first", "second"),
                gap.stream().map(RagPdfParagraphSegmenter.Paragraph::text).toList());
        assertEquals(List.of("1234567890", "abcdefghij"),
                length.stream().map(RagPdfParagraphSegmenter.Paragraph::text).toList());
    }

    @Test
    void pageBoundaryMustBreakOnlyAfterSentenceBoundary() {
        List<RagPdfParagraphSegmenter.Paragraph> joined = segmenter.segment(
                List.of(
                        line(1, 1, "continued", 100D),
                        line(2, 2, "on next page", 100D)),
                1000);
        List<RagPdfParagraphSegmenter.Paragraph> separated = segmenter.segment(
                List.of(
                        line(1, 1, "finished.", 100D),
                        line(2, 2, "next page", 100D)),
                1000);

        assertEquals(1, joined.size());
        assertEquals("continued on next page", joined.get(0).text());
        assertEquals(1, joined.get(0).pageStart());
        assertEquals(2, joined.get(0).pageEnd());
        assertEquals(2, separated.size());
        assertEquals("finished.", separated.get(0).text());
        assertEquals("next page", separated.get(1).text());
    }

    @Test
    void blankLinesMustBeIgnoredAndEmptyInputMustProduceNoParagraphs() {
        assertTrue(segmenter.segment(List.of(), 1000).isEmpty());
        assertTrue(segmenter.segment(
                List.of(line(1, 1, "  ", 100D)),
                1000).isEmpty());
    }

    private RagPdfTextLayoutExtractor.TextLine line(
            int page,
            int line,
            String text,
            double y) {
        return new RagPdfTextLayoutExtractor.TextLine(
                page,
                line,
                text,
                50D,
                y,
                10D);
    }
}
