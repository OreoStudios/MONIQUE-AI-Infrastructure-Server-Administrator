package fr.oreostudios.monique.pods;

import fr.oreostudios.monique.pods.PodModels.AddressInput;
import fr.oreostudios.monique.pods.PodModels.AddressView;
import fr.oreostudios.monique.pods.PodModels.CreatePod;
import fr.oreostudios.monique.pods.PodModels.PodView;
import fr.oreostudios.monique.security.SecretCipher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PodService {
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;

    public PodService(JdbcTemplate jdbc, SecretCipher cipher) {
        this.jdbc = jdbc;
        this.cipher = cipher;
    }

    public List<PodView> list() {
        return jdbc.query("""
            SELECT id, name, ssh_host, ssh_port, ssh_username, credential_type, created_at
            FROM pods ORDER BY created_at DESC
            """, (rs, n) -> new PodView(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("ssh_host"),
                rs.getInt("ssh_port"),
                rs.getString("ssh_username"),
                rs.getString("credential_type"),
                true,
                addresses(rs.getObject("id", UUID.class)),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
            ));
    }

    public PodView find(UUID id) {
        return list().stream().filter(p -> p.id().equals(id)).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pod not found"));
    }

    @Transactional
    public PodView create(CreatePod input) {
        String method = input.credentialType().toUpperCase(Locale.ROOT);
        if (!Set.of("PASSWORD", "PRIVATE_KEY").contains(method)) {
            throw new IllegalArgumentException("credentialType must be PASSWORD or PRIVATE_KEY");
        }
        String host = input.sshHost().trim();
        if (!host.matches("[a-zA-Z0-9.:-]+") || host.startsWith("-")) {
            throw new IllegalArgumentException("Invalid SSH host or IP");
        }
        List<AddressInput> ips = input.addresses() == null ? List.of() : input.addresses();
        if (ips.size() > 64) throw new IllegalArgumentException("Maximum 64 addresses per pod");
        Set<String> unique = new HashSet<>();
        for (AddressInput address : ips) {
            String ip = AddressValidator.ip(address.address());
            AddressValidator.role(address.role());
            if (!unique.add(ip)) throw new IllegalArgumentException("Duplicate IP address on pod");
        }

        UUID id = UUID.randomUUID();
        String ciphertext = cipher.encrypt(id, input.credential());
        jdbc.update("""
            INSERT INTO pods(id, name, ssh_host, ssh_port, ssh_username, credential_type, secret_ciphertext)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, id, input.name().trim(), host, input.sshPort(), input.sshUsername().trim(), method, ciphertext);
        for (AddressInput a : ips) {
            jdbc.update("INSERT INTO pod_addresses(pod_id, address, role) VALUES (?, ?, ?)",
                        id, a.address(), AddressValidator.role(a.role()));
        }
        audit("POD_CREATED", id, "Pod inventory created; no SSH operation performed");
        return find(id);
    }

    @Transactional
    public void delete(UUID id) {
        int changed = jdbc.update("DELETE FROM pods WHERE id = ?", id);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Pod not found");
        audit("POD_DELETED", id, "Pod inventory deleted; remote host untouched");
    }

    private List<AddressView> addresses(UUID podId) {
        return jdbc.query(
            "SELECT address, role FROM pod_addresses WHERE pod_id = ? ORDER BY address",
            (rs, n) -> new AddressView(rs.getString("address"), rs.getString("role")),
            podId
        );
    }

    private void audit(String event, UUID podId, String detail) {
        jdbc.update("INSERT INTO audit_events(id, event, pod_id, details) VALUES (?, ?, ?, ?)",
                    UUID.randomUUID(), event, podId, detail);
    }
}
