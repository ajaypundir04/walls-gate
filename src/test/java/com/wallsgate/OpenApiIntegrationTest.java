package com.wallsgate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"wallsgate.audit.file=", "wallsgate.llm.base-url="})
@AutoConfigureMockMvc
class OpenApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("OpenAPI v3 api-docs JSON endpoint returns valid spec with all paths")
    void apiDocsEndpointReturnsValidSpec() throws Exception {
        String json = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(json.contains("/v1/chat/completions"));
        assertTrue(json.contains("/admin/matters"));
        assertTrue(json.contains("/admin/walls"));
        assertTrue(json.contains("/admin/users"));
        assertTrue(json.contains("/admin/documents"));
        assertTrue(json.contains("/admin/audit"));
        assertTrue(json.contains("walls-gate API"));
    }

    @Test
    @DisplayName("Swagger UI HTML page is accessible")
    void swaggerUiIsAccessible() throws Exception {
        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
