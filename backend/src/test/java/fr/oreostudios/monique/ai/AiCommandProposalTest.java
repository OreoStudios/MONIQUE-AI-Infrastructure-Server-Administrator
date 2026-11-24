package fr.oreostudios.monique.ai;

import fr.oreostudios.monique.execution.CommandService;
import fr.oreostudios.monique.pods.PodService;
import fr.oreostudios.monique.pods.PodModels.PodView;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiCommandProposalTest {
    private static AssistantPreferences preferences() {
        var p = org.mockito.Mockito.mock(AssistantPreferences.class);
        org.mockito.Mockito.when(p.get()).thenReturn(AssistantPreferences.defaults());
        return p;
    }

    @ParameterizedTest @ValueSource(strings={"CODEX","CLAUDE"})
    void modelCanOnlyProposeOnSelectedPodAndNeverApprove(String provider) {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var commands = mock(CommandService.class);
        var pods = mock(PodService.class);
        UUID pod = UUID.randomUUID();
        when(commands.enabled()).thenReturn(true);
        when(pods.find(pod)).thenReturn(new PodView(pod,"test","localhost",22,"operator","PASSWORD",true,List.of(),Instant.now()));
        var job = new CommandService.Job(UUID.randomUUID(),pod,"uname -a","Inspect OS","PENDING",30,"",null,Instant.now().toString());
        when(commands.propose(pod,"uname -a","Inspect OS",30)).thenReturn(job);
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        String response = provider.equals("CODEX") ? json.writeValueAsString(Map.of("status","completed","output",List.of(
            Map.of("type","function_call","name","propose_command","arguments",json.writeValueAsString(
                Map.of("command","uname -a","purpose","Inspect OS","timeout_seconds",30)))))) : """
            {"stop_reason":"tool_use","content":[{"type":"tool_use","name":"propose_command",
            "input":{"command":"uname -a","purpose":"Inspect OS","timeout_seconds":30}}]}
            """;
        server.expect(requestTo(provider.equals("CODEX") ? "https://api.openai.com/v1/responses" : "https://api.anthropic.com/v1/messages"))
            .andExpect(jsonPath("$.tools[0].name").value("propose_command"))
            .andRespond(withSuccess(response,MediaType.APPLICATION_JSON));
        var controller = new AiController(pods,builder,"test-key","test-key","claude-sonnet-4-5","gpt-6.2-astra","max",16000,commands,preferences());
        var answer = controller.chat(new AiController.ChatRequest("Inspect my VPS",provider,List.of(pod)));
        assertFalse(answer.executed()); assertEquals(List.of(job),answer.jobs());
        verify(commands).propose(pod,"uname -a","Inspect OS",30);
        verify(commands,never()).approve(any());
        server.verify();
    }
}
