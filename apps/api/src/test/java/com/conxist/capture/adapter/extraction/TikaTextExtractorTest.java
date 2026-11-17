package com.conxist.capture.adapter.extraction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import com.conxist.capture.domain.RejectedDocument;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class TikaTextExtractorTest {

    private static final String SENTENCE = "Every source connects to the hub";
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final TikaTextExtractor extractor = new TikaTextExtractor(10_000);

    @Test
    void reads_a_pdf() throws IOException {
        byte[] pdf = pdfWith(SENTENCE);

        String mediaType = extractor.detectMediaType(pdf, "spec.pdf");

        assertThat(mediaType).isEqualTo("application/pdf");
        assertThat(extractor.extractText(pdf, mediaType)).contains(SENTENCE);
    }

    @Test
    void reads_a_word_document() throws IOException {
        byte[] docx = docxWith(SENTENCE);

        String mediaType = extractor.detectMediaType(docx, "spec.docx");

        assertThat(mediaType).isEqualTo(DOCX);
        assertThat(extractor.extractText(docx, mediaType)).contains(SENTENCE);
    }

    @Test
    void reads_markdown_as_text() {
        byte[] markdown = ("# Notes\n" + SENTENCE).getBytes(UTF_8);

        String mediaType = extractor.detectMediaType(markdown, "notes.md");

        assertThat(mediaType).startsWith("text/");
        assertThat(extractor.extractText(markdown, mediaType)).isEqualTo("# Notes\n" + SENTENCE);
    }

    @Test
    void detects_the_real_type_whatever_the_file_name_says() throws IOException {
        assertThat(extractor.detectMediaType(pdfWith(SENTENCE), "notes.txt")).isEqualTo("application/pdf");
    }

    @Test
    void drops_characters_the_database_cannot_store() {
        byte[] text = "Hub\u0000 notes\u0007\tok\n".getBytes(UTF_8);

        assertThat(extractor.extractText(text, "text/plain")).isEqualTo("Hub notes\tok");
    }

    @Test
    void rejects_a_file_with_no_text() {
        assertThatThrownBy(() -> extractor.extractText("   \n".getBytes(UTF_8), "text/plain"))
                .isInstanceOf(RejectedDocument.class);
    }

    @Test
    void rejects_a_damaged_pdf() {
        byte[] damaged = "%PDF-1.7 not really a pdf".getBytes(UTF_8);

        assertThatThrownBy(() -> extractor.extractText(damaged, "application/pdf"))
                .isInstanceOf(RejectedDocument.class);
    }

    private static byte[] pdfWith(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 700);
                content.showText(text);
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Builds the smallest valid Word file by hand, so the test needs no Word library. */
    private static byte[] docxWith(String text) throws IOException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(out)) {
            addEntry(zip, "[Content_Types].xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                      <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                      <Default Extension="xml" ContentType="application/xml"/>
                      <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                    </Types>""");
            addEntry(zip, "_rels/.rels", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                    </Relationships>""");
            addEntry(zip, "word/document.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                      <w:body><w:p><w:r><w:t>%s</w:t></w:r></w:p></w:body>
                    </w:document>""".formatted(text));
            zip.finish();
            return out.toByteArray();
        }
    }

    private static void addEntry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(UTF_8));
        zip.closeEntry();
    }
}
