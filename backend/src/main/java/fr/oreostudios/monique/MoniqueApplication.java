package fr.oreostudios.monique;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class MoniqueApplication {
    // The WebMVC starter alone does not supply a RestClient.Builder bean in Boot 4.
    // Provider-bound AI requests use the shared builder; no secrets are configured here.
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    public static void main(String[] args) {
        SpringApplication.run(MoniqueApplication.class, args);
    }
}
