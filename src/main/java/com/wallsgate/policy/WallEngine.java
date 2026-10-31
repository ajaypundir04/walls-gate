package com.wallsgate.policy;

import com.wallsgate.model.Models.Wall;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which matters each session has touched and flags any session that would
 * combine two matters separated by a wall.
 */
@Component
public class WallEngine {
    public record Decision(boolean violation, List<String> conflicts, Set<String> matters) {}

    private final PolicyStore store;
    private final Map<String, Set<String>> sessions = new ConcurrentHashMap<>();

    public WallEngine(PolicyStore store) { this.store = store; }

    public Decision evaluate(String sessionId, Set<String> candidateMatters) {
        Set<String> combined = new TreeSet<>(sessions.getOrDefault(sessionId, Set.of()));
        combined.addAll(candidateMatters);
        List<String> conflicts = new ArrayList<>();
        for (Wall w : store.walls()) {
            if (combined.contains(w.matterA()) && combined.contains(w.matterB())) {
                conflicts.add(w.matterA() + " <-> " + w.matterB());
            }
        }
        Collections.sort(conflicts);
        return new Decision(!conflicts.isEmpty(), conflicts, combined);
    }

    /** Record matters as touched. Call only for requests that proceed (allowed or shadow). */
    public void commit(String sessionId, Set<String> matters) {
        sessions.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).addAll(matters);
    }
}
