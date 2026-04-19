package com.healthassist.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * US 08: Post-Retrieval Reranking.
 * Prioritises retrieved chunks based on symptom severity, department relevance,
 * and safety policies before they are injected into the LLM prompt.
 * Also captures source document names for reliable citation extraction.
 */
@Component
public class HealthcareDocumentReranker implements DocumentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(HealthcareDocumentReranker.class);

    /** Thread-local citation store — populated during each request, read by ChatService after LLM call. */
    public static final ThreadLocal<List<String>> CITATIONS = ThreadLocal.withInitial(ArrayList::new);

    private static final Set<String> EMERGENCY_QUERY_KEYWORDS = Set.of(
            "chest", "pain", "tightness", "breath", "breathing", "stroke",
            "seizure", "bleeding", "emergency", "triage", "palpitation",
            "pressure", "severe", "urgent", "heart", "shortness"
    );

    @Override
    public List<Document> process(Query query, List<Document> documents) {
        if (documents.isEmpty()) return documents;

        boolean isEmergencyQuery = isEmergency(query.text());

        List<Document> reranked = documents.stream()
                .sorted(Comparator.comparingInt((Document doc) -> score(doc, isEmergencyQuery)).reversed())
                .collect(Collectors.toList());

        // Capture unique source document names for citation — reliable alternative to parsing LLM output
        List<String> citations = CITATIONS.get();
        citations.clear();
        reranked.forEach(doc -> {
            String src = (String) doc.getMetadata().getOrDefault("source_document", null);
            if (src != null && !citations.contains(src)) citations.add(src);
        });

        log.debug("Reranked {} documents (emergencyQuery={}), citations: {}", reranked.size(), isEmergencyQuery, citations);
        return reranked;
    }

    private boolean isEmergency(String queryText) {
        if (queryText == null) return false;
        String lower = queryText.toLowerCase();
        return EMERGENCY_QUERY_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private int score(Document doc, boolean emergencyQuery) {
        String dept = (String) doc.getMetadata().getOrDefault("department", "general");
        String docType = (String) doc.getMetadata().getOrDefault("doc_type", "general");
        String symptomCategory = (String) doc.getMetadata().getOrDefault("symptom_category", "general");

        int score = 0;

        // Boost emergency/triage docs for urgent queries
        if (emergencyQuery) {
            if ("emergency".equals(dept)) score += 4;
            if ("emergency".equals(symptomCategory)) score += 3;
        }

        // Doc type priority: triage > sop > department info > others
        switch (docType) {
            case "triage"      -> score += 3;
            case "sop"         -> score += 2;
            case "department"  -> score += 1;
            default            -> {}
        }

        return score;
    }
}
