package com.wallsgate;

import com.wallsgate.model.Models.Doc;
import com.wallsgate.policy.PolicyStore;
import com.wallsgate.policy.Retriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RetrieverTest {

    private PolicyStore store;
    private Retriever retriever;

    @BeforeEach
    void setUp() {
        store = new PolicyStore();
        store.putDoc(new Doc("d1", "M-1001", "Acme Settlement Proposal", "Confidential settlement offer of 2M for Acme lawsuit."));
        store.putDoc(new Doc("d2", "M-1001", "Acme Case Background", "General litigation background regarding Acme claims."));
        store.putDoc(new Doc("d3", "M-2002", "Beta Acquisition Memo", "Acme lawsuit liabilities noted in Beta due diligence."));
        store.putDoc(new Doc("d4", "M-3003", "Gamma Tax Summary", "Gamma advisory corporate structure and tax planning."));
        retriever = new Retriever(store);
    }

    @Test
    @DisplayName("Filter-first rule: Disallowed matters are never returned even with high token overlap")
    void documentsOutsideAllowedMattersAreFilteredOut() {
        // Query strongly matches d3 ("Beta Acquisition Memo"), but user only has access to M-1001
        List<Doc> results = retriever.search("Beta Acquisition due diligence", Set.of("M-1001"));

        assertFalse(results.stream().anyMatch(d -> d.matterId().equals("M-2002")),
                "Must NEVER return documents from unauthorized matters regardless of relevance");
    }

    @Test
    @DisplayName("Matches are ranked by relevance (token overlap)")
    void resultsAreRankedByRelevance() {
        List<Doc> results = retriever.search("Acme Settlement Proposal lawsuit", Set.of("M-1001"));

        assertEquals(2, results.size());
        assertEquals("d1", results.get(0).id(), "d1 has higher token overlap with query than d2");
    }

    @Test
    @DisplayName("Queries with no matching terms return empty list")
    void nonMatchingQueryReturnsEmpty() {
        List<Doc> results = retriever.search("completely unrelated quantum physics", Set.of("M-1001", "M-2002", "M-3003"));
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Empty or whitespace queries return empty list gracefully")
    void emptyQueryReturnsEmpty() {
        assertTrue(retriever.search("", Set.of("M-1001")).isEmpty());
        assertTrue(retriever.search("   ", Set.of("M-1001")).isEmpty());
    }

    @Test
    @DisplayName("Empty allowed matters set returns empty list immediately")
    void emptyAllowedMattersReturnsEmpty() {
        assertTrue(retriever.search("Acme settlement", Set.of()).isEmpty());
    }
}
