package com.healthassist.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * US 08: Post-Retrieval Compression.
 * Truncates large SOP/document chunks to a concise, safe summary length
 * before they are injected into the LLM prompt, preventing token overflow.
 */
@Component
public class HealthcareDocumentCompressor implements DocumentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(HealthcareDocumentCompressor.class);
    private static final int MAX_CONTENT_LENGTH = 900;

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents.isEmpty()) return documents;

        List<Document> compressed = documents.stream()
                .map(this::compress)
                .collect(Collectors.toList());

        log.debug("Compressed {} documents to max {} chars each", compressed.size(), MAX_CONTENT_LENGTH);
        return compressed;
    }

    private Document compress(Document doc) {
        String content = doc.getText();
        if (content == null || content.length() <= MAX_CONTENT_LENGTH) {
            return doc;
        }
        // Keep up to MAX_CONTENT_LENGTH chars, cutting at the last sentence boundary if possible
        String truncated = content.substring(0, MAX_CONTENT_LENGTH);
        int lastPeriod = truncated.lastIndexOf('.');
        if (lastPeriod > MAX_CONTENT_LENGTH / 2) {
            truncated = truncated.substring(0, lastPeriod + 1);
        }
        return new Document(truncated + " [...]", doc.getMetadata());
    }
}
