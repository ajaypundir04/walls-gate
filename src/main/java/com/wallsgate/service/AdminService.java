package com.wallsgate.service;

import com.wallsgate.domain.exception.UnauthorizedException;
import com.wallsgate.domain.repository.PolicyRepository;
import com.wallsgate.gateway.AuditService;
import com.wallsgate.model.Models.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Application Service orchestrating administrative domain policies,
 * matter registries, information barriers (ethical walls), credential verification,
 * and compliance audit reporting.
 */
@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final PolicyRepository repository;
    private final AuditService auditService;
    private final byte[] expectedTokenBytes;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminService(PolicyRepository repository,
                        AuditService auditService,
                        @Value("${wallsgate.admin-token:change-me}") String adminToken) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        String token = (adminToken == null || adminToken.isBlank()) ? "change-me" : adminToken;
        this.expectedTokenBytes = token.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Convenience constructor defaulting admin token for tests.
     */
    public AdminService(PolicyRepository repository, AuditService auditService) {
        this(repository, auditService, "change-me");
    }

    /**
     * Verifies administrative credentials using constant-time comparison
     * to protect against timing side-channel attacks.
     *
     * @param token the secret token presented by the caller
     * @throws UnauthorizedException if token is missing or invalid
     */
    public void authenticate(String token) {
        if (token == null || !MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), expectedTokenBytes)) {
            log.warn("Unauthorized administrative access attempt with token: {}", maskToken(token));
            throw new UnauthorizedException("Invalid or missing X-Admin-Token");
        }
    }

    public Collection<Matter> getMatters() {
        return repository.matters();
    }

    public Matter registerMatter(Matter matter) {
        validateMatter(matter);
        repository.putMatter(matter);
        log.info("Registered matter: id='{}', name='{}'", matter.id(), matter.name());
        return matter;
    }

    public Set<Wall> getWalls() {
        return repository.walls();
    }

    public Wall establishWall(Wall wall) {
        validateWall(wall);
        repository.putWall(wall);
        log.info("Established ethical wall between '{}' and '{}'", wall.matterA(), wall.matterB());
        return wall;
    }

    public Collection<User> getUsers() {
        return repository.users();
    }

    public User registerUser(User user) {
        validateUser(user);
        repository.putUser(user);
        log.info("Registered user permissions: id='{}', matters={}", user.id(), user.matters());
        return user;
    }

    public Collection<Doc> getDocuments() {
        return repository.docs();
    }

    public Doc ingestDocument(Doc doc) {
        validateDoc(doc);
        repository.putDoc(doc);
        log.info("Ingested document: id='{}', matterId='{}', title='{}'", doc.id(), doc.matterId(), doc.title());
        return doc;
    }

    public List<AuditService.AuditEvent> getAuditEvents() {
        return auditService.all();
    }

    public String exportAuditCsv() {
        return auditService.toCsv();
    }

    // ------------------------------------------------------------------------
    // Business Validation Invariants
    // ------------------------------------------------------------------------

    private void validateMatter(Matter m) {
        if (m == null || isBlank(m.id()) || isBlank(m.name())) {
            throw new IllegalArgumentException("Matter id and name must not be blank");
        }
    }

    private void validateWall(Wall w) {
        if (w == null || isBlank(w.matterA()) || isBlank(w.matterB())) {
            throw new IllegalArgumentException("Wall matters must not be blank");
        }
        if (w.matterA().trim().equalsIgnoreCase(w.matterB().trim())) {
            throw new IllegalArgumentException("Cannot establish an ethical wall between a matter and itself");
        }
    }

    private void validateUser(User u) {
        if (u == null || isBlank(u.id())) {
            throw new IllegalArgumentException("User id must not be blank");
        }
    }

    private void validateDoc(Doc d) {
        if (d == null || isBlank(d.id()) || isBlank(d.matterId()) || isBlank(d.text())) {
            throw new IllegalArgumentException("Document id, matterId, and text must not be blank");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String maskToken(String token) {
        if (token == null) return "<null>";
        if (token.length() <= 4) return "****";
        return token.substring(0, 2) + "****" + token.substring(token.length() - 2);
    }
}
