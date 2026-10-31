package com.wallsgate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"wallsgate.audit.file=", "wallsgate.llm.base-url="})
@AutoConfigureMockMvc
class GatewayIntegrationTest {
    @Autowired MockMvc mvc;

    private static final String BODY =
            "{\"messages\":[{\"role\":\"user\",\"content\":\"Summarise Acme Beta settlement strategy\"}]}";

    private String call(String user, String session, int expectedStatus) throws Exception {
        var b = post("/v1/chat/completions").contentType(MediaType.APPLICATION_JSON).content(BODY);
        if (user != null) b = b.header("X-User-Id", user);
        if (session != null) b = b.header("X-Session-Id", session);
        return mvc.perform(b).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
    }

    @Test void authorisedUserSeesOwnMatterDocs() throws Exception {
        assertTrue(call("alice", "s-alice", 200).contains("settlement strategy"));
    }

    @Test void userNeverSeesDocsFromMattersTheyCannotOpen() throws Exception {
        String res = call("bob", "s-bob", 200);
        assertFalse(res.contains("settlement"), "bob must not receive M-1001 content");
    }

    @Test void sessionTouchingTwoWalledMattersIsBlocked() throws Exception {
        assertTrue(call("carol", "s-carol", 403).contains("ethical_wall"));
    }

    @Test void unknownUserRejected() throws Exception {
        call("mallory", null, 401);
        call(null, null, 401);
    }
}
