package com.wallsgate;

import com.wallsgate.policy.Redactor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RedactorTest {
    @Test void redactsEmailIbanAndClientNames() {
        Redactor r = new Redactor("Acme", "");
        String out = r.redact("Mail j.doe@example.com, IBAN DE89 3704 0044 0532 0130 00, client acme.");
        assertFalse(out.contains("j.doe@example.com"));
        assertFalse(out.contains("DE89"));
        assertFalse(out.toLowerCase().contains("acme"));
    }
}
