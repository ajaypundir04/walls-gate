package com.wallsgate.policy;

import com.wallsgate.model.Models.*;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;

/** Synthetic demo data only. Never load real client data into a demo. */
@Component
public class SeedData {
    private final PolicyStore store;
    private final boolean enabled;

    public SeedData(PolicyStore store, @Value("${wallsgate.seed:true}") boolean enabled) {
        this.store = store;
        this.enabled = enabled;
    }

    @PostConstruct
    void load() {
        if (!enabled) return;
        store.putMatter(new Matter("M-1001", "Acme v. Beta litigation"));
        store.putMatter(new Matter("M-2002", "Beta Corp acquisition"));
        store.putMatter(new Matter("M-3003", "Gamma GmbH tax advisory"));
        store.putWall(new Wall("M-1001", "M-2002"));

        store.putUser(new User("alice", Set.of("M-1001", "M-3003")));
        store.putUser(new User("bob", Set.of("M-2002")));
        store.putUser(new User("carol", Set.of("M-1001", "M-2002"))); // access to both = wall test

        store.putDoc(new Doc("d1", "M-1001", "Acme v. Beta settlement strategy",
                "Acme settlement strategy confidential. Authorise an offer up to 2 million euro against Beta. Contact j.doe@example.com."));
        store.putDoc(new Doc("d2", "M-2002", "Beta due diligence memo",
                "Beta Corp acquisition due diligence. Liabilities include a reserve for pending Acme litigation."));
        store.putDoc(new Doc("d3", "M-3003", "Gamma engagement letter",
                "Engagement letter for Gamma GmbH tax advisory. IBAN DE89 3704 0044 0532 0130 00."));
    }
}
