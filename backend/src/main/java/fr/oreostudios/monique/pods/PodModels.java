package fr.oreostudios.monique.pods;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PodModels {
    private PodModels() { }

    public record AddressInput(@NotBlank String address, @NotBlank String role) { }

    public record CreatePod(
        @NotBlank @Size(max=100) String name,
        @NotBlank @Size(max=255) String sshHost,
        @Min(1) @Max(65535) int sshPort,
        @NotBlank @Size(max=80) String sshUsername,
        @NotBlank String credentialType,
        @NotBlank @Size(max=32000) String credential,
        List<@Valid AddressInput> addresses
    ) { }

    public record AddressView(String address, String role) { }

    public record PodView(
        UUID id, String name, String sshHost, int sshPort, String sshUsername,
        String credentialType, boolean hasCredential, List<AddressView> addresses, Instant createdAt
    ) { }
}
