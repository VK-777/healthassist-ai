package com.healthassist.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class DynamicMetadataFilter {

    private static final Logger log = LoggerFactory.getLogger(DynamicMetadataFilter.class);

    private static final Map<String, String> DEPARTMENT_KEYWORDS = Map.ofEntries(
            Map.entry("cardiology", "cardiology"),
            Map.entry("cardiac", "cardiology"),
            Map.entry("heart", "cardiology"),
            Map.entry("pulmonology", "pulmonology"),
            Map.entry("respiratory", "pulmonology"),
            Map.entry("lung", "pulmonology"),
            Map.entry("breathing", "pulmonology"),
            Map.entry("gastroenterology", "gastroenterology"),
            Map.entry("stomach", "gastroenterology"),
            Map.entry("digestive", "gastroenterology"),
            Map.entry("abdominal", "gastroenterology"),
            Map.entry("neurology", "neurology"),
            Map.entry("neurological", "neurology"),
            Map.entry("brain", "neurology"),
            Map.entry("stroke", "neurology"),
            Map.entry("headache", "neurology"),
            Map.entry("emergency", "emergency"),
            Map.entry("er", "emergency"),
            Map.entry("urgent", "emergency"),
            Map.entry("oncology", "oncology"),
            Map.entry("cancer", "oncology"),
            Map.entry("general medicine", "general"),
            Map.entry("general", "general")
    );

    private static final Map<String, String> SYMPTOM_KEYWORDS = Map.ofEntries(
            Map.entry("chest pain", "cardiac"),
            Map.entry("chest tightness", "cardiac"),
            Map.entry("palpitation", "cardiac"),
            Map.entry("shortness of breath", "respiratory"),
            Map.entry("difficulty breathing", "respiratory"),
            Map.entry("cough", "respiratory"),
            Map.entry("wheezing", "respiratory"),
            Map.entry("stomach pain", "gastrointestinal"),
            Map.entry("nausea", "gastrointestinal"),
            Map.entry("vomiting", "gastrointestinal"),
            Map.entry("diarrhea", "gastrointestinal"),
            Map.entry("indigestion", "gastrointestinal"),
            Map.entry("headache", "neurological"),
            Map.entry("dizziness", "neurological"),
            Map.entry("seizure", "neurological"),
            Map.entry("numbness", "neurological"),
            Map.entry("stroke", "neurological"),
            Map.entry("bleeding", "emergency"),
            Map.entry("severe pain", "emergency")
    );

    private static final Map<String, String> FACILITY_KEYWORDS = Map.of(
            "main hospital", "main-hospital",
            "north wing", "north-wing",
            "south wing", "south-wing",
            "east wing", "east-wing"
    );

    private static final Map<String, String> INSURANCE_KEYWORDS = Map.of(
            "premium", "premium",
            "basic", "basic",
            "standard", "standard"
    );

    /**
     * Parse the user message and build a dynamic filter expression using Spring AI's built-in FilterExpressionBuilder.
     * Returns null if no filters apply.
     */
    public Filter.Expression buildFilter(String userMessage) {
        String lowerMessage = userMessage.toLowerCase();
        FilterExpressionBuilder b = new FilterExpressionBuilder();
        List<Filter.Expression> expressions = new ArrayList<>();

        for (Map.Entry<String, String> entry : DEPARTMENT_KEYWORDS.entrySet()) {
            if (containsWord(lowerMessage, entry.getKey())) {
                expressions.add(b.eq("department", entry.getValue()).build());
                log.debug("Detected department filter: {}", entry.getValue());
                break;
            }
        }

        for (Map.Entry<String, String> entry : SYMPTOM_KEYWORDS.entrySet()) {
            if (lowerMessage.contains(entry.getKey())) {
                expressions.add(b.eq("symptom_category", entry.getValue()).build());
                log.debug("Detected symptom_category filter: {}", entry.getValue());
                break;
            }
        }

        for (Map.Entry<String, String> entry : FACILITY_KEYWORDS.entrySet()) {
            if (lowerMessage.contains(entry.getKey())) {
                expressions.add(b.eq("facility", entry.getValue()).build());
                log.debug("Detected facility filter: {}", entry.getValue());
                break;
            }
        }

        for (Map.Entry<String, String> entry : INSURANCE_KEYWORDS.entrySet()) {
            if (containsWord(lowerMessage, entry.getKey())) {
                expressions.add(b.eq("insurance_plan", entry.getValue()).build());
                log.debug("Detected insurance_plan filter: {}", entry.getValue());
                break;
            }
        }

        if (expressions.isEmpty()) {
            return null;
        }

        Filter.Expression combined = expressions.get(0);
        for (int i = 1; i < expressions.size(); i++) {
            combined = new Filter.Expression(
                    Filter.ExpressionType.AND, combined, expressions.get(i));
        }

        log.info("Applied metadata filter: {}", filterToString(combined));
        return combined;
    }

    public String filterToString(Filter.Expression expression) {
        if (expression == null) return "none";

        if (expression.type() == Filter.ExpressionType.AND) {
            return filterToString((Filter.Expression) expression.left())
                    + " AND "
                    + filterToString((Filter.Expression) expression.right());
        } else if (expression.type() == Filter.ExpressionType.OR) {
            return filterToString((Filter.Expression) expression.left())
                    + " OR "
                    + filterToString((Filter.Expression) expression.right());
        } else if (expression.type() == Filter.ExpressionType.EQ) {
            Filter.Key key = (Filter.Key) expression.left();
            Filter.Value val = (Filter.Value) expression.right();
            return key.key() + " == '" + val.value() + "'";
        }
        return expression.toString();
    }

    private boolean containsWord(String text, String word) {
        return Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }
}
