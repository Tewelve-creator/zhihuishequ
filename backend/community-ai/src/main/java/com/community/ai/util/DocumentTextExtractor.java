package com.community.ai.util;

import com.community.common.exception.BizException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 从 PDF / Word / Markdown / 纯文本提取可读正文，供知识库入库。
 */
public final class DocumentTextExtractor {

    private DocumentTextExtractor() {
    }

    public static String extract(String fileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException("文件内容为空");
        }
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (name.endsWith(".pdf")) {
                return extractPdf(bytes);
            }
            if (name.endsWith(".docx")) {
                return extractDocx(bytes);
            }
            if (name.endsWith(".doc")) {
                throw new BizException("暂不支持旧版 .doc，请另存为 .docx 或导出为 PDF/TXT");
            }
            // md / txt / 其他按文本
            Charset cs = detectCharset(bytes);
            String text = new String(bytes, cs).replace("\uFEFF", "").trim();
            if (text.isEmpty()) throw new BizException("未能从文件中解析出文本");
            return text;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("文档解析失败：" + e.getMessage());
        }
    }

    private static String extractPdf(byte[] bytes) throws Exception {
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(bytes))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            if (text == null || text.isBlank()) {
                throw new BizException("PDF 未解析到文本（可能是扫描件，请先 OCR）");
            }
            return text.trim();
        }
    }

    private static String extractDocx(byte[] bytes) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(e.getName())) {
                    String xml = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    String text = xml
                            .replaceAll("(?is)<w:tab[^/]*/>", "\t")
                            .replaceAll("(?is)</w:p>", "\n")
                            .replaceAll("(?is)<[^>]+>", "")
                            .replace("&amp;", "&")
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&quot;", "\"")
                            .replaceAll("[ \\t]+\\n", "\n")
                            .replaceAll("\\n{3,}", "\n\n")
                            .trim();
                    if (text.isEmpty()) throw new BizException("Word 文档无有效文本");
                    return text;
                }
            }
        }
        throw new BizException("无法解析 Word 文档结构");
    }

    private static Charset detectCharset(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return StandardCharsets.UTF_8;
        }
        // 简单探测：非法 UTF-8 时回落 GBK
        String asUtf = new String(bytes, StandardCharsets.UTF_8);
        if (asUtf.contains("\uFFFD")) {
            try {
                return Charset.forName("GBK");
            } catch (Exception e) {
                return StandardCharsets.UTF_8;
            }
        }
        return StandardCharsets.UTF_8;
    }

    public static String guessTitle(String fileName) {
        if (fileName == null || fileName.isBlank()) return "未命名文档";
        return fileName.replaceAll("(?i)\\.(md|txt|pdf|docx|doc)$", "");
    }
}
