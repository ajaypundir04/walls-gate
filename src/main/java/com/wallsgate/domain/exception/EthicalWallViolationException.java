package com.wallsgate.domain.exception;

import java.util.List;
import java.util.Set;

public class EthicalWallViolationException extends DomainException {
    private final List<String> conflicts;
    private final Set<String> matters;

    public EthicalWallViolationException(String message, List<String> conflicts, Set<String> matters) {
        super(message);
        this.conflicts = (conflicts == null) ? List.of() : List.copyOf(conflicts);
        this.matters = (matters == null) ? Set.of() : Set.copyOf(matters);
    }

    public List<String> getConflicts() {
        return conflicts;
    }

    public Set<String> getMatters() {
        return matters;
    }
}
