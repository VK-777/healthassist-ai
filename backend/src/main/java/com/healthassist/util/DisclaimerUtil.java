package com.healthassist.util;

import java.util.regex.Pattern;

public final class DisclaimerUtil {

    public static final String MEDICAL_DISCLAIMER =
            "⚠️ This is not a medical diagnosis. Please consult a qualified medical professional for personalised advice.";

    // Matches common LLM-generated disclaimer variations
    private static final Pattern DISCLAIMER_PATTERN = Pattern.compile(
            "(?i)(⚠️\\s*)?(?:disclaimer:?\\s*)?this is not a medical diagnosis[^\\n]*", Pattern.CASE_INSENSITIVE);

    private DisclaimerUtil() {
        // Utility class
    }

    /**
     * Strips any LLM-generated disclaimer variants from the response,
     * then appends the canonical disclaimer.
     */
    public static String appendDisclaimer(String response) {
        if (response == null || response.isBlank()) {
            return response;
        }
        // Remove any LLM-generated disclaimer variants first
        String cleaned = DISCLAIMER_PATTERN.matcher(response).replaceAll("").stripTrailing();
        return cleaned + "\n\n" + MEDICAL_DISCLAIMER;
    }
}
