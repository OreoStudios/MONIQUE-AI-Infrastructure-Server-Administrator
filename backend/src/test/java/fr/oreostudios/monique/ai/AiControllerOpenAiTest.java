package fr.oreostudios.monique.ai;

import fr.oreostudios.monique.pods.PodService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiControllerOpenAiTest {
    private static AssistantPreferences preferences() {
        var p = org.mockito.Mockito.mock(AssistantPreferences.class);
        org.mockito.Mockito.when(p.get()).thenReturn(AssistantPreferences.defaults());
        return p;
    }

    private static AiController controller(RestClient.Builder builder, String key) {
        return new AiController(mock(PodService.class), builder, "",
                key, "claude-sonnet-4-5", "gpt-6.2-astra", "max", 16000,
                mock(fr.oreostudios.monique.execution.CommandService.class), preferences());
    }

    @Test
    void missingOpenAiKeyReturnsSafeConfigurationError() {
        var api = controller(RestClient.builder(), "");
        var error = assertThrows(AiController.ProviderFailure.class,
                () -> api.chat(new AiController.ChatRequest("Can you plan a backup?", "CODEX", List.of())));
        assertEquals("OPENAI_KEY_MISSING", error.code);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.status);
        assertFalse(error.getMessage().contains("sk-"));
    }

    @Test
    void advancedModelUsesMaximumReasoningAndReturnsText() {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        mockServer.expect(requestTo("https://api.openai.com/v1/responses"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("gpt-6.2-astra"))
                .andExpect(jsonPath("$.reasoning.effort").value("max"))
                .andExpect(jsonPath("$.max_output_tokens").value(16000))
                .andExpect(jsonPath("$.store").value(false))
                .andRespond(withSuccess("""
                    {"status":"completed","output":[
                      {"type":"message","content":[
                        {"type":"output_text","text":"No commands were executed. Check backups first."}
                      ]}
                    ]}
                    """, MediaType.APPLICATION_JSON));
        var response = controller(builder, "test-provider-key")
                .chat(new AiController.ChatRequest("Plan a safe upgrade", "CODEX", List.of()));
        assertEquals("gpt-6.2-astra", response.model());
        assertFalse(response.executed());
        assertTrue(response.answer().contains("Check backups"));
        mockServer.verify();
    }

    @Test
    void revokedProviderKeyIsNotConfusedWithAdminSessionExpiry() {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        mockServer.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"do not expose this raw provider body\"}}"));
        var error = assertThrows(AiController.ProviderFailure.class,
                () -> controller(builder, "bad-provider-key")
                        .chat(new AiController.ChatRequest("Hello", "CODEX", List.of())));
        assertEquals("OPENAI_KEY_REJECTED", error.code);
        assertEquals(HttpStatus.BAD_GATEWAY, error.status);
        assertFalse(error.getMessage().contains("do not expose"));
        mockServer.verify();
    }

    @Test
    void reasoningBudgetExhaustionHasAnActionableErrorCode() {
        var builder = RestClient.builder();
        var mockServer = MockRestServiceServer.bindTo(builder).build();
        mockServer.expect(requestTo("https://api.openai.com/v1/responses"))
                .andRespond(withSuccess("""
                    {"status":"incomplete","incomplete_details":{"reason":"max_output_tokens"},"output":[]}
                    """, MediaType.APPLICATION_JSON));
        var error = assertThrows(AiController.ProviderFailure.class,
                () -> controller(builder, "test-provider-key")
                        .chat(new AiController.ChatRequest("Explain the situation", "CODEX", List.of())));
        assertEquals("OPENAI_OUTPUT_LIMIT", error.code);
        mockServer.verify();
    }
}
