package com.xiafan.agent.service;

import com.xiafan.agent.config.AppProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Mirrors fileProcessingService.py (txt/md/pdf/docx extraction + overlapping text chunking). */
@Service
public class FileProcessingService {

    private final AppProperties props;

    public FileProcessingService(AppProperties props) {
        this.props = props;
    }

    /** Extracts text from file bytes based on its extension (including the dot). */
    public String extractText(byte[] data, String ext) {
        String e = ext == null ? "" : ext.toLowerCase();
        switch (e) {
            case ".txt":
            case ".md":
                return readText(data);
            case ".pdf":
                return readPdf(data);
            case ".docx":
                return readDocx(data);
            default:
                throw new IllegalArgumentException("不支持的文件类型: " + e);
        }
    }

    private String readText(byte[] data) {
        Charset[] encodings = {StandardCharsets.UTF_8, Charset.forName("GBK"), Charset.forName("GB2312"), StandardCharsets.UTF_16};
        for (Charset encoding : encodings) {
            try {
                return new String(data, encoding);
            } catch (Exception ignored) {
                // try next encoding
            }
        }
        throw new IllegalArgumentException("无法解码文件");
    }

    private String readPdf(byte[] data) {
        try (PDDocument doc = Loader.loadPDF(data)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(doc);
        } catch (IOException e) {
            throw new IllegalArgumentException("PDF解析失败: " + e.getMessage(), e);
        }
    }

    private String readDocx(byte[] data) {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText();
                if (text != null && !text.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(text);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("DOCX解析失败: " + e.getMessage(), e);
        }
        return sb.toString();
    }

    /** Splits cleaned text into overlapping chunks; each chunk carries content/chunk_index/metadata. */
    public List<Map<String, Object>> chunkText(String text, Map<String, Object> metadata) {
        List<Map<String, Object>> chunks = new ArrayList<>();
        int chunkSize = props.getChunkSize();
        int overlap = props.getChunkOverlap();
        String cleaned = cleanText(text);
        int start = 0;
        int chunkIndex = 0;
        while (start < cleaned.length()) {
            int end = Math.min(start + chunkSize, cleaned.length());
            String chunkContent = cleaned.substring(start, end);
            if (end < cleaned.length()) {
                int boundary = findSentenceBoundary(chunkContent);
                if (boundary > chunkSize / 2) {
                    chunkContent = chunkContent.substring(0, boundary);
                    end = start + boundary;
                }
            }
            if (!chunkContent.isBlank()) {
                Map<String, Object> md = new LinkedHashMap<>();
                if (metadata != null) {
                    md.putAll(metadata);
                }
                md.put("start_char", start);
                md.put("end_char", Math.min(end, cleaned.length()));
                Map<String, Object> chunk = new LinkedHashMap<>();
                chunk.put("content", chunkContent.strip());
                chunk.put("chunk_index", chunkIndex);
                chunk.put("metadata", md);
                chunks.add(chunk);
                chunkIndex++;
            }
            start = end - overlap;
            if (start < 0) {
                start = 0;
            }
            if (start >= cleaned.length()) {
                break;
            }
        }
        return chunks;
    }

    private static String cleanText(String text) {
        return (text == null ? "" : text).replaceAll("\\s+", " ").strip();
    }

    private static int findSentenceBoundary(String text) {
        String endings = "。！？；.!?;";
        for (int i = text.length() - 1; i > Math.max(0, text.length() - 100); i--) {
            if (endings.indexOf(text.charAt(i)) >= 0) {
                return i + 1;
            }
        }
        return text.length();
    }
}