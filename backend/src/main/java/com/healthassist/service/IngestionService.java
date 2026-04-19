package com.healthassist.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;

    @Value("${healthassist.ingestion.chunk-size:512}")
    private int chunkSize;

    @Value("${healthassist.ingestion.chunk-overlap:50}")
    private int chunkOverlap;

    @Value("${healthassist.ingestion.force-reingest:false}")
    private boolean forceReingest;

    public IngestionService(VectorStore vectorStore, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ingestAllDocuments() {
        try {
            if (forceReingest) {
                log.info("Force re-ingestion enabled — clearing existing vector store data.");
                jdbcTemplate.execute("DELETE FROM vector_store");
            } else {
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM vector_store", Integer.class);
                if (count != null && count > 0) {
                    log.info("Vector store already populated ({} chunks). Skipping ingestion.", count);
                    return;
                }
            }

            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:kb-documents/**/*.*");

            List<Document> allChunks = new ArrayList<>();

            for (Resource resource : resources) {
                if (!resource.isReadable()) continue;

                String filename = resource.getFilename();
                if (filename == null) continue;

                // Skip README files
                if (filename.equalsIgnoreCase("README.md")) continue;

                log.info("Ingesting document: {}", filename);

                List<Document> documents = readDocument(resource);
                Map<String, Object> metadata = deriveMetadata(resource);

                for (Document doc : documents) {
                    doc.getMetadata().putAll(metadata);
                }

                allChunks.addAll(documents);
            }

            if (allChunks.isEmpty()) {
                log.warn("No documents found for ingestion in kb-documents/");
                return;
            }

            // Split into chunks
            TokenTextSplitter splitter = new TokenTextSplitter(chunkSize, chunkOverlap, 5, 10000, true);
            List<Document> chunks = splitter.apply(allChunks);

            log.info("Total chunks to ingest: {}", chunks.size());

            // Store in vector store (embeddings generated automatically)
            vectorStore.add(chunks);

            log.info("Document ingestion completed successfully.");
        } catch (Exception e) {
            log.error("Error during document ingestion", e);
            throw new RuntimeException("Document ingestion failed", e);
        }
    }

    private List<Document> readDocument(Resource resource) {
        String filename = resource.getFilename();
        if (filename == null) return Collections.emptyList();

        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return readPdf(resource);
        } else if (lower.endsWith(".md") || lower.endsWith(".txt")) {
            return readText(resource);
        }
        log.warn("Unsupported file type: {}", filename);
        return Collections.emptyList();
    }

    private List<Document> readPdf(Resource resource) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(resource);
        return reader.get();
    }

    private List<Document> readText(Resource resource) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String content = br.lines().collect(Collectors.joining("\n"));
            Document doc = new Document(content);
            return List.of(doc);
        } catch (Exception e) {
            log.error("Failed to read text resource: {}", resource.getFilename(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Derive metadata from the resource path structure.
     */
    private Map<String, Object> deriveMetadata(Resource resource) {
        Map<String, Object> metadata = new HashMap<>();
        try {
            String path = resource.getURI().toString();
            String filename = resource.getFilename();
            metadata.put("source_document", filename);

            if (path.contains("/sops/")) {
                metadata.put("doc_type", "sop");
            } else if (path.contains("/insurance/")) {
                metadata.put("doc_type", "insurance");
            } else if (path.contains("/triage/")) {
                metadata.put("doc_type", "triage");
            } else if (path.contains("/departments/")) {
                metadata.put("doc_type", "department");
            } else if (path.contains("/facility/")) {
                metadata.put("doc_type", "facility");
            } else {
                metadata.put("doc_type", "general");
            }

            String lowerFilename = filename != null ? filename.toLowerCase() : "";
            if (lowerFilename.contains("cardiology") || lowerFilename.contains("cardiac")) {
                metadata.put("department", "cardiology");
                metadata.put("symptom_category", "cardiac");
            } else if (lowerFilename.contains("respiratory") || lowerFilename.contains("pulmonary")) {
                metadata.put("department", "pulmonology");
                metadata.put("symptom_category", "respiratory");
            } else if (lowerFilename.contains("gastro") || lowerFilename.contains("stomach")) {
                metadata.put("department", "gastroenterology");
                metadata.put("symptom_category", "gastrointestinal");
            } else if (lowerFilename.contains("neuro")) {
                metadata.put("department", "neurology");
                metadata.put("symptom_category", "neurological");
            } else if (path.contains("/triage/") || lowerFilename.contains("triage") || lowerFilename.contains("emergency")) {
                metadata.put("department", "emergency");
            } else {
                metadata.put("department", "general");
            }

            metadata.put("facility", "main-hospital");
        } catch (Exception e) {
            log.warn("Could not derive metadata for resource", e);
        }
        return metadata;
    }
}
