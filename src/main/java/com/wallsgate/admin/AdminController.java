package com.wallsgate.admin;

import com.wallsgate.config.OpenApiConfig;
import com.wallsgate.domain.exception.UnauthorizedException;
import com.wallsgate.domain.repository.PolicyRepository;
import com.wallsgate.gateway.AuditService;
import com.wallsgate.model.Models.*;
import com.wallsgate.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Admin", description = "Administrative endpoints for matter boundaries, ethical walls, ACLs, and compliance logs")
@SecurityRequirement(name = OpenApiConfig.SECURITY_ADMIN_TOKEN)
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
    @Operation(summary = "List all matters", description = "Retrieves all registered legal matters or confidential projects.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved matters list"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing X-Admin-Token header")
    })
    public Collection<Matter> matters(
            @Parameter(name = HEADER_ADMIN_TOKEN, in = ParameterIn.HEADER, description = "Admin authorization token", required = false)
            @RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
        authenticate(token);
        return adminService.getMatters();
    }

    @PostMapping("/matters")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a matter", description = "Creates or registers a new confidential matter boundary.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Matter registered successfully"),
            @ApiResponse(responseCode = "400", description = "Matter id or name is blank"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing X-Admin-Token header")
    })
    public Matter addMatter(
            @Parameter(name = HEADER_ADMIN_TOKEN, in = ParameterIn.HEADER, description = "Admin authorization token", required = false)
            @RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token,
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
    @Operation(summary = "List all ethical walls", description = "Retrieves all active information barriers separating conflicted matters.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved ethical walls"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing X-Admin-Token header")
    })
    public Set<Wall> walls(
            @Parameter(name = HEADER_ADMIN_TOKEN, in = ParameterIn.HEADER, description = "Admin authorization token", required = false)
            @RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
        authenticate(token);
        return adminService.getWalls();
    }

    @PostMapping("/walls")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Establish an ethical wall", description = "Creates an information barrier between two conflicted matters.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ethical wall established successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid matter IDs or attempt to wall a matter against itself"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing X-Admin-Token header")
    })
    public Wall addWall(
            @Parameter(name = HEADER_ADMIN_TOKEN, in = ParameterIn.HEADER, description = "Admin authorization token", required = false)
            @RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token,
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
    @Operation(summary = "List all users", description = "Retrieves all registered users and their authorized matter scopes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved users list"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing X-Admin-Token header")
    })
    public Collection<User> users(
            @Parameter(name = HEADER_ADMIN_TOKEN, in = ParameterIn.HEADER, description = "Admin authorization token", required = false)
            @RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
        authenticate(token);
        return adminService.getUsers();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register or update user permissions", description = "Sets or updates the matter access scopes for a user.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User permissions registered successfully"),
            @ApiResponse(responseCode = "400", description = "User id is blank"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing X-Admin-Token header")
    })
    public User addUser(
            @Parameter(name = HEADER_ADMIN_TOKEN, in = ParameterIn.HEADER, description = "Admin authorization token", required = false)
            @RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token,
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
    @Operation(summary = "List all documents", description = "Retrieves all documents currently stored in the knowledge base.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved documents list"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing X-Admin-Token header")
    })
    public Collection<Doc> documents(
            @Parameter(name = HEADER_ADMIN_TOKEN, in = ParameterIn.HEADER, description = "Admin authorization token", required = false)
            @RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
        authenticate(token);
        return adminService.getDocuments();
    }

    @PostMapping("/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ingest a document", description = "Stores a new document associated with a specific matter ID.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Document ingested successfully"),
            @ApiResponse(responseCode = "400", description = "Document id, matterId, or text is blank"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing X-Admin-Token header")
    })
    public Doc addDoc(
            @Parameter(name = HEADER_ADMIN_TOKEN, in = ParameterIn.HEADER, description = "Admin authorization token", required = false)
            @RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token,
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
    @Operation(summary = "Retrieve audit events stream", description = "Returns the structured JSON audit trail of all gateway completion requests.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved audit events"),
            @ApiResponse(responseCode = "401", description = "Invalid or missing X-Admin-Token header")
    })
    public List<AuditService.AuditEvent> audit(
            @Parameter(name = HEADER_ADMIN_TOKEN, in = ParameterIn.HEADER, description = "Admin authorization token", required = false)
            @RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
        authenticate(token);
        return adminService.getAuditEvents();
    }

    @GetMapping(value = "/audit.csv", produces = "text/csv")
    @Operation(summary = "Export audit log as CSV", description = "Exports the audit trail as a CSV attachment, neutralized against formula injection.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Downloadable CSV audit export",
                    content = @Content(mediaType = "text/csv", schema = @Schema(type = "string"))
            ),
            @ApiResponse(responseCode = "401", description = "Invalid or missing X-Admin-Token header")
    })
    public ResponseEntity<String> auditCsv(
            @Parameter(name = HEADER_ADMIN_TOKEN, in = ParameterIn.HEADER, description = "Admin authorization token", required = false)
            @RequestHeader(value = HEADER_ADMIN_TOKEN, required = false) String token) {
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
