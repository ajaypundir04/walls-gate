package com.wallsgate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallsgate.domain.exception.UnauthorizedException;
import com.wallsgate.gateway.AuditService;
import com.wallsgate.model.Models.*;
import com.wallsgate.policy.PolicyStore;
import com.wallsgate.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdminServiceTest {

    private PolicyStore store;
    private AuditService auditService;
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        store = new PolicyStore();
        auditService = new AuditService(new ObjectMapper(), "");
        adminService = new AdminService(store, auditService, "secret-key-123");
    }

    @Test
    @DisplayName("Successfully authenticates with matching token")
    void testAuthenticateSuccess() {
        assertDoesNotThrow(() -> adminService.authenticate("secret-key-123"));
    }

    @Test
    @DisplayName("Throws UnauthorizedException for invalid or missing tokens")
    void testAuthenticateFailure() {
        assertThrows(UnauthorizedException.class, () -> adminService.authenticate("wrong-token"));
        assertThrows(UnauthorizedException.class, () -> adminService.authenticate(null));
        assertThrows(UnauthorizedException.class, () -> adminService.authenticate(""));
    }

    @Test
    @DisplayName("Successfully registers and retrieves matters")
    void testRegisterAndGetMatters() {
        Matter m = new Matter("M-101", "Acme Restructuring");
        Matter registered = adminService.registerMatter(m);
        assertEquals(m, registered);

        Collection<Matter> matters = adminService.getMatters();
        assertEquals(1, matters.size());
        assertTrue(matters.contains(m));
    }

    @Test
    @DisplayName("Rejects blank matter id or name")
    void testRejectInvalidMatter() {
        assertThrows(IllegalArgumentException.class, () -> adminService.registerMatter(new Matter("", "Valid Name")));
        assertThrows(IllegalArgumentException.class, () -> adminService.registerMatter(new Matter("M-1", "   ")));
        assertThrows(IllegalArgumentException.class, () -> adminService.registerMatter(null));
    }

    @Test
    @DisplayName("Successfully establishes ethical wall between conflicting matters")
    void testEstablishWall() {
        Wall wall = new Wall("M-1", "M-2");
        Wall registered = adminService.establishWall(wall);
        assertEquals(wall, registered);

        Set<Wall> walls = adminService.getWalls();
        assertEquals(1, walls.size());
        assertTrue(walls.contains(wall));
    }

    @Test
    @DisplayName("Rejects self-conflicting ethical wall")
    void testRejectSelfWall() {
        assertThrows(IllegalArgumentException.class, () -> adminService.establishWall(new Wall("M-1", "M-1")));
        assertThrows(IllegalArgumentException.class, () -> adminService.establishWall(new Wall("M-1", "  ")));
    }

    @Test
    @DisplayName("Successfully registers and retrieves users")
    void testRegisterAndGetUsers() {
        User user = new User("lawyer-1", Set.of("M-1", "M-2"));
        User registered = adminService.registerUser(user);
        assertEquals(user, registered);

        Collection<User> users = adminService.getUsers();
        assertEquals(1, users.size());
        assertTrue(users.contains(user));
    }

    @Test
    @DisplayName("Rejects blank user id")
    void testRejectInvalidUser() {
        assertThrows(IllegalArgumentException.class, () -> adminService.registerUser(new User("  ", Set.of())));
        assertThrows(IllegalArgumentException.class, () -> adminService.registerUser(null));
    }

    @Test
    @DisplayName("Successfully ingests and retrieves documents")
    void testIngestAndGetDocuments() {
        Doc doc = new Doc("doc-1", "M-1", "Deposition", "Client testimony content");
        Doc ingested = adminService.ingestDocument(doc);
        assertEquals(doc, ingested);

        Collection<Doc> docs = adminService.getDocuments();
        assertEquals(1, docs.size());
        assertTrue(docs.contains(doc));
    }

    @Test
    @DisplayName("Rejects invalid document parameters")
    void testRejectInvalidDocument() {
        assertThrows(IllegalArgumentException.class, () -> adminService.ingestDocument(new Doc("", "M-1", "Title", "Text")));
        assertThrows(IllegalArgumentException.class, () -> adminService.ingestDocument(new Doc("doc-1", "", "Title", "Text")));
        assertThrows(IllegalArgumentException.class, () -> adminService.ingestDocument(new Doc("doc-1", "M-1", "Title", "  ")));
    }

    @Test
    @DisplayName("Exports audit events and CSV format correctly")
    void testAuditIntegration() {
        auditService.record("user-42", "session-9", "ALLOWED", "", Set.of("M-1"), Set.of("doc-1"), "What is status?");

        assertEquals(1, adminService.getAuditEvents().size());
        String csv = adminService.exportAuditCsv();
        assertTrue(csv.contains("user-42"));
        assertTrue(csv.contains("session-9"));
    }
}
