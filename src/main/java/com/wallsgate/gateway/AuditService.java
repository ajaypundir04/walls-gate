package com.wallsgate.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Answers: who asked what, which documents were used, what was blocked, and why. */
@Service
public class AuditService {
    public record AuditEvent(String timestamp, String user, String session, String action,
                             String reason, List<String> matters, List<String> documents,
                             String promptRedacted) {}

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();
    private final ObjectMapper mapper;
    private final Path file;

    public AuditService(ObjectMapper mapper, @Value("${wallsgate.audit.file:}") String path) {
        this.mapper = mapper;
        this.file = (path == null || path.isBlank()) ? null : Path.of(path);
    }

    public void record(String user, String session, String action, String reason,
                       Collection<String> matters, Collection<String> documents, String promptRedacted) {
        AuditEvent e = new AuditEvent(Instant.now().toString(), user, session, action, reason,
                new ArrayList<>(matters), new ArrayList<>(documents), promptRedacted);
        events.add(e);
        if (file != null) {
            try {
                Files.writeString(file, mapper.writeValueAsString(e) + "\n",
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException ex) {
                // Do not fail the request because of logging; surface it loudly instead.
                System.err.println("AUDIT WRITE FAILED: " + ex.getMessage());
            }
        }
    }

    public List<AuditEvent> all() { return List.copyOf(events); }

    public String toCsv() {
        StringBuilder sb = new StringBuilder("timestamp,user,session,action,reason,matters,documents,prompt_redacted\n");
        for (AuditEvent e : events) {
            sb.append(String.join(",",
                    q(e.timestamp()), q(e.user()), q(e.session()), q(e.action()), q(e.reason()),
                    q(String.join(";", e.matters())), q(String.join(";", e.documents())),
                    q(e.promptRedacted()))).append('\n');
        }
        return sb.toString();
    }

    private static String q(String s) {
        String v = s == null ? "" : s.replace("\"", "\"\"");
        // neutralise CSV formula injection when opened in Excel
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return "\"" + v + "\"";
    }
}
