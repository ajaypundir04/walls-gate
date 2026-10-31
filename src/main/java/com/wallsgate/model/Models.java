package com.wallsgate.model;

import java.util.Set;

/** Plain domain types used across the gateway. */
public final class Models {
    private Models() {}

    public record Matter(String id, String name) {}

    /** Information barrier between two matters. Order-insensitive. */
    public record Wall(String matterA, String matterB) {
        public Wall {
            if (matterA.compareTo(matterB) > 0) {
                String t = matterA;
                matterA = matterB;
                matterB = t;
            }
        }
    }

    public record Doc(String id, String matterId, String title, String text) {}

    public record User(String id, Set<String> matters) {}
}
