package fr.oreostudios.monique.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoiceSessionControllerTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"noisy","quiet","push_to_talk"})
    void preferencesConfigureNoiseLanguageAndInterruption(String mode) {
        var builder = RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var preferences = org.mockito.Mockito.mock(AssistantPreferences.class);
        var prefs = new AssistantPreferences.Preferences("Elias","auto",mode,false,true,false);
        org.mockito.Mockito.when(preferences.get()).thenReturn(prefs);
        var expectation = server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://api.openai.com/v1/realtime/client_secrets"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.session.instructions").value(org.hamcrest.Matchers.containsString("Elias")))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.session.instructions").value(org.hamcrest.Matchers.containsString("latest utterance")))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.session.audio.input.noise_reduction.type").value("near_field"));
        if (mode.equals("push_to_talk")) expectation.andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.session.audio.input.turn_detection").value(org.hamcrest.Matchers.nullValue()));
        else expectation
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.session.audio.input.turn_detection.interrupt_response").value(false))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.session.audio.input.turn_detection.threshold").value(mode.equals("noisy") ? 0.85 : 0.6));
        expectation.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"value\":\"ek_test\"}",org.springframework.http.MediaType.APPLICATION_JSON));
        var commands = org.mockito.Mockito.mock(fr.oreostudios.monique.execution.CommandService.class);
        var pods = org.mockito.Mockito.mock(fr.oreostudios.monique.pods.PodService.class);
        var controller = new VoiceSessionController(builder,"test-key","gpt-realtime-2.1-mini","marin","test-admin",preferences,commands,pods);
        var response = controller.createSession(null);
        assertEquals(prefs,response.getBody().get("preferences"));
        assertEquals("no-store",response.getHeaders().getCacheControl());
        server.verify();
    }
    @Test
    void executionEnabledWithSelectedPodArmsRunCommandTool() {
        var builder = RestClient.builder();
        var server = org.springframework.test.web.client.MockRestServiceServer.bindTo(builder).build();
        var preferences = org.mockito.Mockito.mock(AssistantPreferences.class);
        org.mockito.Mockito.when(preferences.get()).thenReturn(new AssistantPreferences.Preferences("Elias","auto","noisy",false,true,false));
        var commands = org.mockito.Mockito.mock(fr.oreostudios.monique.execution.CommandService.class);
        org.mockito.Mockito.when(commands.enabled()).thenReturn(true);
        var pods = org.mockito.Mockito.mock(fr.oreostudios.monique.pods.PodService.class);
        var podId = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(pods.find(podId)).thenReturn(new fr.oreostudios.monique.pods.PodModels.PodView(
            podId,"hetzner","203.0.113.10",22,"root","PRIVATE_KEY",true,java.util.List.of(),java.time.Instant.now()));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://api.openai.com/v1/realtime/client_secrets"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.session.tools[0].name").value("run_command"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.session.tool_choice").value("auto"))
            .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath("$.session.instructions").value(org.hamcrest.Matchers.containsString("STANDING AUTHORIZATION")))
            .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"value\":\"ek_test\"}",org.springframework.http.MediaType.APPLICATION_JSON));
        var controller = new VoiceSessionController(builder,"test-key","gpt-realtime-2.1-mini","marin","test-admin",preferences,commands,pods);
        var response = controller.createSession(new VoiceSessionController.SessionRequest(podId));
        assertEquals(Boolean.TRUE,response.getBody().get("execution"));
        server.verify();
    }

    @Test
    void missingApiKeyPreventsSessionCreation() {
        var controller = new VoiceSessionController(
                RestClient.builder(), "",
                "gpt-realtime-2.1-mini", "marin",
                "this-is-a-test-admin-token-with-32-plus-characters", org.mockito.Mockito.mock(AssistantPreferences.class),
                org.mockito.Mockito.mock(fr.oreostudios.monique.execution.CommandService.class),
                org.mockito.Mockito.mock(fr.oreostudios.monique.pods.PodService.class));
        var error = assertThrows(ResponseStatusException.class,
                () -> controller.createSession(null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }
}
