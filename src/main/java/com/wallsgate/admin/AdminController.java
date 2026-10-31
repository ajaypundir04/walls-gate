package com.wallsgate.admin;

import com.wallsgate.gateway.AuditService;
import com.wallsgate.model.Models.*;
import com.wallsgate.policy.PolicyStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Minimal admin API to define matters, walls, user access and documents; plus audit export. */
@RestController
@RequestMapping("/admin")
public class AdminController {
    private final PolicyStore store;
    private final AuditService audit;
    private final String adminToken;

    public AdminController(PolicyStore store, AuditService audit,
                           @Value("${wallsgate.admin-token:change-me}") String adminToken) {
        this.store = store; this.audit = audit; this.adminToken = adminToken;
    }

    private void auth(String token) {
        boolean ok = token != null && MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8), adminToken.getBytes(StandardCharsets.UTF_8));
        if (!ok) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "bad admin token");
    }

    @GetMapping("/matters")
    public Collection<Matter> matters(@RequestHeader(value = "X-Admin-Token", required = false) String t) {
        auth(t); return store.matters();
    }

    @PostMapping("/matters")
    public Matter addMatter(@RequestHeader(value = "X-Admin-Token", required = false) String t, @RequestBody Matter m) {
        auth(t); store.putMatter(m); return m;
    }

    @GetMapping("/walls")
    public Set<Wall> walls(@RequestHeader(value = "X-Admin-Token", required = false) String t) {
        auth(t); return store.walls();
    }

    @PostMapping("/walls")
    public Wall addWall(@RequestHeader(value = "X-Admin-Token", required = false) String t, @RequestBody Wall w) {
        auth(t); store.putWall(w); return w;
    }

    @GetMapping("/users")
    public Collection<User> users(@RequestHeader(value = "X-Admin-Token", required = false) String t) {
        auth(t); return store.users();
    }

    @PostMapping("/users")
    public User addUser(@RequestHeader(value = "X-Admin-Token", required = false) String t, @RequestBody User u) {
        auth(t); store.putUser(u); return u;
    }

    @PostMapping("/documents")
    public Doc addDoc(@RequestHeader(value = "X-Admin-Token", required = false) String t, @RequestBody Doc d) {
        auth(t); store.putDoc(d); return d;
    }

    @GetMapping("/audit")
    public List<AuditService.AuditEvent> audit(@RequestHeader(value = "X-Admin-Token", required = false) String t) {
        auth(t); return audit.all();
    }

    @GetMapping(value = "/audit.csv", produces = "text/csv")
    public ResponseEntity<String> auditCsv(@RequestHeader(value = "X-Admin-Token", required = false) String t) {
        auth(t);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit.csv")
                .body(audit.toCsv());
    }
}
