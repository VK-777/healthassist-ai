package com.healthassist.service;

import com.healthassist.filter.DynamicMetadataFilter;
import com.healthassist.model.ChatResponse;
import com.healthassist.util.DisclaimerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.TranslationQueryTransformer;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    // High-risk symptom keywords sourced from KB-004 (Triage Guidelines & Emergency Routing)
    private static final Set<String> HIGH_RISK_KEYWORDS = Set.of(
            "chest pain", "chest tightness", "tightness in my chest", "tightness in chest",
            "chest pressure", "sharp chest", "shortness of breath", "difficulty breathing",
            "can't breathe", "cannot breathe", "severe bleeding", "uncontrolled bleeding",
            "stroke", "sudden weakness", "slurred speech", "loss of consciousness",
            "unconscious", "heart attack", "palpitation", "seizure", "severe chest",
            "numbness"
    );

    private static final String HIGH_RISK_RESPONSE =
            "⚠️ Based on what you've described, this may be a HIGH RISK situation.\n\n" +
            "Please take the following steps immediately:\n" +
            "1. **Call emergency services (112 / 911)** or go to the nearest Emergency Department right away.\n" +
            "2. **Do not drive yourself** — ask someone to take you or call an ambulance.\n" +
            "3. Contact our hospital helpdesk at **Ext. 2201** so a nurse can support you.\n\n" +
            "I'm not a doctor, but based on what you've shared, this could be serious. " +
            "Please seek emergency care immediately.\n\n" +
            "[Source: HA_Policy_01_Patient_Triage_and_Emergency_Routing.pdf]";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final DynamicMetadataFilter dynamicMetadataFilter;
    private final AuditService auditService;
    private final HealthcareDocumentReranker documentReranker;
    private final HealthcareDocumentCompressor documentCompressor;
    private final ChatMemory chatMemory;

    @Value("${healthassist.retrieval.top-k:5}")
    private int topK;

    @Value("${healthassist.retrieval.similarity-threshold:0.7}")
    private double similarityThreshold;

    public ChatService(ChatClient chatClient,
                       VectorStore vectorStore,
                       DynamicMetadataFilter dynamicMetadataFilter,
                       AuditService auditService,
                       HealthcareDocumentReranker documentReranker,
                       HealthcareDocumentCompressor documentCompressor,
                       ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.dynamicMetadataFilter = dynamicMetadataFilter;
        this.auditService = auditService;
        this.documentReranker = documentReranker;
        this.documentCompressor = documentCompressor;
        this.chatMemory = chatMemory;
    }

    /**
     * Synchronous RAG pipeline using built-in Spring AI modules:
     * TranslationQueryTransformer → RewriteQueryTransformer → CompressionQueryTransformer
     * → MultiQueryExpander → VectorStoreDocumentRetriever (with dynamic filters)
     * → ContextualQueryAugmenter (allowEmptyContext=false) → LLM Call → Disclaimer
     */
    public ChatResponse chat(String message, String sessionId) {
        String workflowId = "wf-" + UUID.randomUUID();
        log.info("Starting chat pipeline [{}] for session: {}", workflowId, sessionId);

        // Log user query
        auditService.logStage(workflowId, "USER_QUERY",
                "{\"message\": \"%s\", \"sessionId\": \"%s\"}".formatted(sanitize(message), sanitize(sessionId)));

        // US 02: Bypass RAG for high-risk symptoms — respond immediately per KB-004
        if (isHighRiskSymptom(message)) {
            log.info("[{}] High-risk symptom detected — bypassing RAG, returning emergency escalation.", workflowId);
            auditService.logStage(workflowId, "TRIAGE", "{\"risk\": \"HIGH\", \"action\": \"emergency_escalation\"}");
            String finalResponse = DisclaimerUtil.appendDisclaimer(HIGH_RISK_RESPONSE);
            return new ChatResponse(finalResponse,
                    List.of("HA_Policy_01_Patient_Triage_and_Emergency_Routing.pdf"), workflowId,
                    DisclaimerUtil.MEDICAL_DISCLAIMER);
        }

        // Enrich follow-up queries using conversation history so RAG retrieves relevant docs
        String ragQuery = enrichFollowUpQuery(message, sessionId);
        log.info("[{}] RAG query (enriched={}): {}", workflowId, !ragQuery.equals(message), ragQuery);

        // Extract dynamic filters from user message (US 06)
        Filter.Expression filterExpression = dynamicMetadataFilter.buildFilter(ragQuery);
        String filterStr = dynamicMetadataFilter.filterToString(filterExpression);
        auditService.logStage(workflowId, "FILTER",
                "{\"filters\": \"%s\"}".formatted(sanitize(filterStr)));

        // Build the RetrievalAugmentationAdvisor with all built-in modules
        Advisor ragAdvisor = buildRagAdvisor(filterExpression);

        try {
            var aiChatResponse = chatClient.prompt()
                    .user(ragQuery)
                    .advisors(ragAdvisor)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                    .call()
                    .chatResponse();

            String llmResponse = aiChatResponse.getResult().getOutput().getText();

            // Extract token usage
            var usage = aiChatResponse.getMetadata().getUsage();
            long promptTokens = usage != null ? usage.getPromptTokens() : 0;
            long completionTokens = usage != null ? usage.getCompletionTokens() : 0;
            long totalTokens = usage != null ? usage.getTotalTokens() : 0;

            auditService.logStage(workflowId, "LLM_CALL",
                    "{\"model\": \"llama3.2\", \"status\": \"success\", \"promptTokens\": %d, \"completionTokens\": %d, \"totalTokens\": %d}"
                            .formatted(promptTokens, completionTokens, totalTokens));

            // Read citations captured by HealthcareDocumentReranker from retrieved document metadata
            List<String> citations = new ArrayList<>(HealthcareDocumentReranker.CITATIONS.get());
            HealthcareDocumentReranker.CITATIONS.remove(); // clean up ThreadLocal

            String finalResponse = DisclaimerUtil.appendDisclaimer(llmResponse);

            return new ChatResponse(finalResponse, citations, workflowId, DisclaimerUtil.MEDICAL_DISCLAIMER);
        } catch (Exception e) {
            log.error("Chat pipeline failed for workflow {}", workflowId, e);
            auditService.logStage(workflowId, "LLM_CALL",
                    "{\"model\": \"llama3.2\", \"status\": \"error\", \"error\": \"%s\"}"
                            .formatted(sanitize(e.getMessage())));
            throw new RuntimeException("Failed to generate response from AI model", e);
        }
    }

    /**
     * Streaming RAG pipeline — uses the same built-in advisor chain.
     */
    public Flux<String> chatStream(String message, String sessionId) {
        String workflowId = "wf-" + UUID.randomUUID();
        log.info("Starting streaming chat pipeline [{}] for session: {}", workflowId, sessionId);

        auditService.logStage(workflowId, "USER_QUERY",
                "{\"message\": \"%s\", \"sessionId\": \"%s\"}".formatted(sanitize(message), sanitize(sessionId)));

        // US 02: Bypass RAG for high-risk symptoms — stream emergency escalation immediately
        if (isHighRiskSymptom(message)) {
            log.info("[{}] High-risk symptom detected (stream) — bypassing RAG.", workflowId);
            auditService.logStage(workflowId, "TRIAGE", "{\"risk\": \"HIGH\", \"action\": \"emergency_escalation\"}");
            return Flux.just(HIGH_RISK_RESPONSE, "\n\n" + DisclaimerUtil.MEDICAL_DISCLAIMER, "[DONE]");
        }

        // Enrich follow-up queries using conversation history so RAG retrieves relevant docs
        String ragQuery = enrichFollowUpQuery(message, sessionId);

        Filter.Expression filterExpression = dynamicMetadataFilter.buildFilter(ragQuery);
        Advisor ragAdvisor = buildRagAdvisor(filterExpression);

        return chatClient.prompt()
                .user(ragQuery)
                .advisors(ragAdvisor)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                .stream()
                .content()
                .concatWith(Flux.just("\n\n" + DisclaimerUtil.MEDICAL_DISCLAIMER, "[DONE]"));
    }

    /**
     * Builds the RetrievalAugmentationAdvisor using built-in Spring AI modules.
     * Uses chatClient.mutate() so transformers/expanders don't inherit the
     * healthcare system prompt or memory advisors.
     */
    private Advisor buildRagAdvisor(Filter.Expression filterExpression) {

        // Pre-retrieval: Query Transformers (US 07)
        // chatClient.mutate() returns a clean builder — no system prompt, no memory pollution
        var translationTransformer = TranslationQueryTransformer.builder()
                .chatClientBuilder(chatClient.mutate())
                .targetLanguage("en")
                .build();

        var rewriteTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClient.mutate())
                .build();

        var compressionTransformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClient.mutate())
                .build();

        // Pre-retrieval: Multi-Query Expansion (US 07)
        var multiQueryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClient.mutate())
                .includeOriginal(true)
                .numberOfQueries(2)
                .build();

        // Retrieval: VectorStoreDocumentRetriever with dynamic filters (US 06)
        var retrieverBuilder = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(similarityThreshold)
                .topK(topK);

        if (filterExpression != null) {
            retrieverBuilder.filterExpression(filterExpression);
        }

        var documentRetriever = retrieverBuilder.build();

        // Post-retrieval: ContextualQueryAugmenter with allowEmptyContext=false (US 08)
        // documentFormatter injects [Source: filename] so the LLM can cite correctly (US 01/05)
        // Custom augmentation prompt avoids conflicting instructions with the system prompt (llama3.2 fix)
        var queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .promptTemplate(new PromptTemplate("""
                        Reference information is below.

                        ---------------------
                        {context}
                        ---------------------

                        Use the above reference information to answer the query clearly and helpfully. Never mention "context", "provided context", "reference information", or any internal details in your answer — respond naturally as a knowledgeable hospital assistant. If the reference information is not relevant, politely offer to help with hospital-related questions.

                        Query: {query}

                        Answer:
                        """))
                .emptyContextPromptTemplate(new PromptTemplate("""
                        For this query, the best next step is to speak directly with our hospital team who can give you personalised guidance. Please contact the hospital helpdesk or a nurse/doctor directly.

                        Is there anything else I can assist you with today?
                        """))
                .documentFormatter(docs -> {
                    StringBuilder sb = new StringBuilder();
                    for (var doc : docs) {
                        String source = (String) doc.getMetadata().getOrDefault("source_document", "unknown");
                        sb.append("[Source: ").append(source).append("]\n");
                        sb.append(doc.getText()).append("\n\n");
                    }
                    return sb.toString();
                })
                .build();

        // Wire everything into RetrievalAugmentationAdvisor
        return RetrievalAugmentationAdvisor.builder()
                .queryTransformers(translationTransformer, rewriteTransformer, compressionTransformer)
                .queryExpander(multiQueryExpander)
                .documentRetriever(documentRetriever)
                .documentPostProcessors(documentReranker, documentCompressor)
                .queryAugmenter(queryAugmenter)
                .build();
    }

    private boolean isHighRiskSymptom(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        return HIGH_RISK_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private List<String> parseCitations(String text) {
        if (text == null) return List.of();
        Matcher m = Pattern.compile("\\[Source:\\s*([^\\]]+)\\]").matcher(text);
        List<String> citations = new ArrayList<>();
        while (m.find()) {
            String citation = m.group(1).trim();
            if (!citations.contains(citation)) citations.add(citation);
        }
        return citations;
    }

    /**
     * Detects short/ambiguous follow-up messages that need conversation history
     * to form a meaningful RAG query.
     */
    private boolean isFollowUp(String message) {
        if (message == null) return false;
        String lower = message.trim().toLowerCase();
        if (lower.length() < 20) return true;
        return lower.matches("^(yes|no|sure|ok|okay|please|thanks|thank you|tell me more|go ahead|yeah|yep|nope|why|how|what|when|where|can you|could you|do that|do it).*$");
    }

    /**
     * For follow-up messages, retrieves recent conversation history and uses the LLM
     * to rewrite the message as a standalone query suitable for RAG retrieval.
     */
    private String enrichFollowUpQuery(String message, String sessionId) {
        if (!isFollowUp(message)) {
            return message;
        }

        try {
            List<Message> history = chatMemory.get(sessionId);
            if (history == null || history.isEmpty()) {
                return message;
            }

            StringBuilder conversationContext = new StringBuilder();
            for (Message msg : history) {
                if (msg instanceof UserMessage) {
                    conversationContext.append("User: ").append(msg.getText()).append("\n");
                } else if (msg instanceof AssistantMessage) {
                    // Truncate long assistant responses for the rewrite prompt
                    String text = msg.getText();
                    if (text.length() > 300) {
                        text = text.substring(0, 300) + "...";
                    }
                    conversationContext.append("Assistant: ").append(text).append("\n");
                }
            }

            String enrichedQuery = chatClient.mutate().build()
                    .prompt()
                    .user("Given the conversation history below and the latest user message, rewrite the user message as a clear, standalone question that captures the user's intent. Only return the rewritten question, nothing else.\n\nConversation history:\n" + conversationContext + "\nLatest user message: " + message + "\n\nRewritten question:")
                    .call()
                    .content();

            if (enrichedQuery != null && !enrichedQuery.isBlank()) {
                log.info("Enriched follow-up '{}' → '{}'", message, enrichedQuery.trim());
                return enrichedQuery.trim();
            }
        } catch (Exception e) {
            log.warn("Failed to enrich follow-up query, using original message", e);
        }
        return message;
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"").replace("\n", " ");
    }
}
