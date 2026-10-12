package com.raiseready.report;

import java.io.IOException;

import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

/**
 * Reads the words back out of a PDF with all whitespace removed. The extractor guesses at gaps between table cells (it can
 * run "Investor A" into the shares beside it), so text is compared without spaces: the figures and their order must match.
 */
final class PdfText {

    private PdfText() {
    }

    /** Removes all whitespace, so a figure can be looked for however the extractor spaced it. */
    static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    /** The same, for each expected piece of text, to hand to {@code contains}. */
    static String[] s(String... expected) {
        String[] squashed = new String[expected.length];
        for (int i = 0; i < expected.length; i++) {
            squashed[i] = squash(expected[i]);
        }
        return squashed;
    }

    static String of(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder all = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                all.append(extractor.getTextFromPage(page)).append(' ');
            }
            return squash(all.toString());
        } finally {
            reader.close();
        }
    }
}
