package com.wallsgate.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

/** Plain domain types used across the gateway. */
public final class Models {
    private Models() {}

    @Schema(description = "Legal matter or confidential project boundary")
    public record Matter(
            @Schema(description = "Unique matter identifier", example = "M-1001") String id,
            @Schema(description = "Descriptive matter name", example = "Acme Corp Restructuring") String name
    ) {}

    /** Information barrier between two matters. Order-insensitive. */
    @Schema(description = "Ethical wall / information barrier preventing simultaneous access to two conflicting matters")
    public record Wall(
            @Schema(description = "First conflicting matter ID", example = "M-1001") String matterA,
            @Schema(description = "Second conflicting matter ID", example = "M-2002") String matterB
    ) {
        public Wall {
            if (matterA != null && matterB != null && matterA.compareTo(matterB) > 0) {
                String t = matterA;
                matterA = matterB;
                matterB = t;
            }
        }
    }

    @Schema(description = "Knowledge base document belonging to a specific matter")
    public record Doc(
            @Schema(description = "Unique document identifier", example = "doc-01") String id,
            @Schema(description = "Owning matter identifier", example = "M-1001") String matterId,
            @Schema(description = "Document title or heading", example = "Acme Settlement Proposal") String title,
            @Schema(description = "Document body text content", example = "Confidential terms of settlement...") String text
    ) {}

    @Schema(description = "Enterprise user and their authorized matter scopes")
    public record User(
            @Schema(description = "User or agent identifier", example = "alice") String id,
            @Schema(description = "Set of matter IDs the user is permitted to query", example = "[\"M-1001\", \"M-3003\"]") Set<String> matters
    ) {}
}
