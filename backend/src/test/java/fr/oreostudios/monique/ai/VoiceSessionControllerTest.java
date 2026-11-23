package fr.oreostudios.monique.ai;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VoiceSessionControllerTest {
    @Test
    void missingApiKeyPreventsSessionCreation() {
        var controller = new VoiceSessionController(
                RestClient.builder(), "",
                "gpt-realtime-2.1-mini", "marin",
                "this-is-a-test-admin-token-with-32-plus-characters");
        var error = assertThrows(ResponseStatusException.class,
                controller::createSession);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }
}
