package com.wallsgate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallsgate.admin.AdminController;
import com.wallsgate.gateway.AuditService;
import com.wallsgate.model.Models.*;
import com.wallsgate.policy.PolicyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AdminControllerTest {

    private PolicyStore store;
    private AuditService audit;
    private AdminController controller;
    private static final String VALID_TOKEN = "secret-admin-key";

    @BeforeEach
    void setUp() {
        store = new PolicyStore();
        audit = new AuditService(new ObjectMapper(), "");
        controller = new AdminController(store, audit, VALID_TOKEN);
    }

    @Test
    @DisplayName("Rejects requests without admin token with 401 UNAUTHORIZED")
    void missingTokenRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.matters(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("Rejects requests with incorrect admin token with 401 UNAUTHORIZED")
    void incorrectTokenRejected() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                controller.matters("wrong-token"));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
    }

    @Test
    @DisplayName("Allows adding and reading matters with valid token")
    void managesMatters() {
        Matter m = new Matter("M-9999", "Special Project");
        controller.addMatter(VALID_TOKEN, m);

        Collection<Matter> matters = controller.matters(VALID_TOKEN);
        assertEquals(1, matters.size());
        assertTrue(matters.stream().anyMatch(item -> item.id().equals("M-9999")));
    }

    @Test
    @DisplayName("Allows adding and reading walls with valid token")
    void managesWalls() {
        controller.addWall(VALID_TOKEN, new Wall("M-1", "M-2"));

        Set<Wall> walls = controller.walls(VALID_TOKEN);
        assertEquals(1, walls.size());
        assertTrue(walls.contains(new Wall("M-1", "M-2")));
    }

    @Test
    @DisplayName("Allows adding and reading users with valid token")
    void managesUsers() {
        controller.addUser(VALID_TOKEN, new User("dev-user", Set.of("M-1")));

        Collection<User> users = controller.users(VALID_TOKEN);
        assertEquals(1, users.size());
        assertTrue(users.stream().anyMatch(u -> u.id().equals("dev-user")));
    }

    @Test
    @DisplayName("Allows adding documents with valid token")
    void managesDocuments() {
        Doc d = new Doc("d-99", "M-1", "Title", "Text");
        controller.addDoc(VALID_TOKEN, d);

        assertEquals(1, store.docs().size());
    }

    @Test
    @DisplayName("Exports audit logs as CSV attachment with valid token")
    void exportsAuditCsv() {
        audit.record("alice", "s-1", "ALLOWED", "", Set.of("M-1"), Set.of("d1"), "test");

        ResponseEntity<String> response = controller.auditCsv(VALID_TOKEN);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("attachment; filename=audit.csv"));
        assertTrue(response.getBody().contains("alice"));
    }
}
