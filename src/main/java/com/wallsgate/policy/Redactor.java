package com.wallsgate.policy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regex-based DLP. MVP stand-in for Microsoft Presidio (run Presidio as a sidecar
 * and call it from here once you need NER-based detection and more languages).
 */
@Component
public class Redactor {
    private final Map<String, Pattern> patterns = new LinkedHashMap<>();

    public Redactor(@Value("${wallsgate.dlp.client-names:}") String clientNames,
                    @Value("${wallsgate.dlp.matter-number-regex:}") String matterRegex) {
        patterns.put("EMAIL", Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
        patterns.put("IBAN", Pattern.compile("\\b[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?\\b"));
        patterns.put("PHONE", Pattern.compile("(?<!\\w)\\+?\\d[\\d /-]{7,}\\d"));
        if (matterRegex != null && !matterRegex.isBlank()) {
            patterns.put("MATTER_NO", Pattern.compile(matterRegex));
        }
        int i = 0;
        for (String name : clientNames.split(",")) {
            String n = name.trim();
            if (!n.isEmpty()) {
                patterns.put("CLIENT#" + (i++),
                        Pattern.compile("\\b" + Pattern.quote(n) + "\\b", Pattern.CASE_INSENSITIVE));
            }
        }
    }

    public String redact(String text) {
        if (text == null) return null;
        String out = text;
        for (var e : patterns.entrySet()) {
            String label = e.getKey().startsWith("CLIENT#") ? "CLIENT" : e.getKey();
            out = e.getValue().matcher(out).replaceAll(Matcher.quoteReplacement("[REDACTED_" + label + "]"));
        }
        return out;
    }
}
