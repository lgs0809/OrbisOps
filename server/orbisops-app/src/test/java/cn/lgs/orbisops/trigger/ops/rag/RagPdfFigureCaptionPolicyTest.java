package cn.lgs.orbisops.trigger.ops.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagPdfFigureCaptionPolicyTest {

    private final RagPdfFigureCaptionPolicy policy =
            new RagPdfFigureCaptionPolicy();

    @Test
    void englishCaptionMustMatchNearImageAndIncludeCloseContinuationLines() {
        String caption = policy.captionForImage(
                List.of(
                        line(1, 1, "unrelated", 150D),
                        line(1, 2, "Fig. 1: Lock topology", 200D),
                        line(1, 3, "and callback retry queue.", 212D),
                        line(1, 4, "far paragraph", 250D)),
                1,
                190D);

        assertEquals(
                "Fig. 1: Lock topology and callback retry queue.",
                caption);
    }

    @Test
    void chineseCaptionAndPageBoundaryMustBeSupported() {
        String caption = policy.captionForImage(
                List.of(
                        line(1, 1, "图 2：错误率趋势", 210D),
                        line(2, 2, "Fig. 2: Other page", 210D)),
                1,
                200D);

        assertEquals("图 2：错误率趋势", caption);
    }

    @Test
    void nextFigureOrLargeGapMustStopContinuation() {
        String nextFigure = policy.captionForImage(
                List.of(
                        line(1, 1, "Fig. 1: First", 200D),
                        line(1, 2, "Fig. 2: Second", 212D)),
                1,
                190D);
        String largeGap = policy.captionForImage(
                List.of(
                        line(1, 1, "Fig. 1: First", 200D),
                        line(1, 2, "not continuation", 230D)),
                1,
                190D);

        assertEquals("Fig. 1: First", nextFigure);
        assertEquals("Fig. 1: First", largeGap);
    }

    @Test
    void absentOrOutOfRangeCaptionMustReturnEmptyText() {
        assertEquals("", policy.captionForImage(List.of(), 1, 100D));
        assertEquals("", policy.captionForImage(
                List.of(line(1, 1, "Fig. 1: Far", 500D)),
                1,
                100D));
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
