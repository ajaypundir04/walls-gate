package com.wallsgate.policy;

import com.wallsgate.model.Models.Doc;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Permission-aware retrieval: documents the user cannot open are filtered out BEFORE scoring,
 * so they can never reach the model. MVP uses naive keyword overlap; swap in a vector index
 * but keep the filter-first rule (and filter at the index level, not after).
 */
@Component
public class Retriever {
    private static final int TOP_K = 3;
    private final PolicyStore store;

    public Retriever(PolicyStore store) { this.store = store; }

    private record Scored(Doc doc, int score) {}

    public List<Doc> search(String query, Set<String> allowedMatters) {
        Set<String> q = tokens(query);
        return store.docs().stream()
                .filter(d -> allowedMatters.contains(d.matterId()))   // permission filter first
                .map(d -> {
                    Set<String> t = tokens(d.title() + " " + d.text());
                    int s = (int) q.stream().filter(t::contains).count();
                    return new Scored(d, s);
                })
                .filter(s -> s.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(TOP_K)
                .map(Scored::doc)
                .toList();
    }

    private static Set<String> tokens(String s) {
        Set<String> out = new HashSet<>();
        for (String t : s.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
            if (t.length() >= 3) out.add(t);
        }
        return out;
    }
}
