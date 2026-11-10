package com.wallsgate.admin;

import com.wallsgate.domain.exception.UnauthorizedException;
import com.wallsgate.domain.repository.PolicyRepository;
import com.wallsgate.gateway.AuditService;
import com.wallsgate.model.Models.*;
import com.wallsgate.service.AdminService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Administrative REST presentation adapter managing enterprise policy configuration,
 * matter boundaries, ethical walls, knowledge base documents, and audit logs.
 *
 * <p>Adheres strictly to the Single Responsibility Principle (SRP) by isolating HTTP
 * protocol handling, header extraction, and status code serialization from domain logic,
 * delegating credential verification and policy workflows to {@link AdminService}.</p>
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private static final String HEADER_ADMIN_TOKEN = "X-Admin-Token";
    private final AdminService adminService;

    @org.springframework.beans.factory.annotation.Autowired
    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Backward-compatible convenience constructor for unit tests.
     */
    public AdminController(PolicyRepository store,
                           AuditService audit,
                           String adminToken) {
        this(new AdminService(store, audit, adminToken));
    }

    // ------------------------------------------------------------------------
    // Matters API
    // ------------------------------------------------------------------------

    @GetMapping("/matters")
    public Collection<Matter> matters(@RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
        authenticate(token);
        return adminService.getMatters();
    }

    @PostMapping("/matters")
    @ResponseStatus(HttpStatus.CREATED)
    public Matter addMatter(@RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token,
                            @RequestBody Matter matter) {
        authenticate(token);
        try {
            return adminService.registerMatter(matter);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // Ethical Walls API
    // ------------------------------------------------------------------------

    @GetMapping("/walls")
    public Set<Wall> walls(@RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
        authenticate(token);
        return adminService.getWalls();
    }

    @PostMapping("/walls")
    @ResponseStatus(HttpStatus.CREATED)
    public Wall addWall(@RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token,
                        @RequestBody Wall wall) {
        authenticate(token);
        try {
            return adminService.establishWall(wall);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // Users & Access Control API
    // ------------------------------------------------------------------------

    @GetMapping("/users")
    public Collection<User> users(@RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
        authenticate(token);
        return adminService.getUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public User addUser(@RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token,
                        @RequestBody User user) {
        authenticate(token);
        try {
            return adminService.registerUser(user);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // Knowledge Base Documents API
    // ------------------------------------------------------------------------

    @GetMapping("/documents")
    public Collection<Doc> documents(@RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
        authenticate(token);
        return adminService.getDocuments();
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public Doc addDoc(@RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token,
                      @RequestBody Doc doc) {
        authenticate(token);
        try {
            return adminService.ingestDocument(doc);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // Audit & Compliance Export API
    // ------------------------------------------------------------------------

    @GetMapping("/audit")
    public List<AuditService.AuditEvent> audit(@RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
        authenticate(token);
        return adminService.getAuditEvents();
    }

    @GetMapping(value = "/audit.csv", produces = "text/csv")
    public ResponseEntity<String> auditCsv(@RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
        authenticate(token);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audit.csv")
                .body(adminService.exportAuditCsv());
    }

    // ------------------------------------------------------------------------
    // Authentication Delegation
    // ------------------------------------------------------------------------

    private void authenticate(String token) {
        try {
            adminService.authenticate(token);
        } catch (UnauthorizedException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }
}
