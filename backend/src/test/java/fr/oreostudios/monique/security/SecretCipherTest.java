package fr.oreostudios.monique.security;

import org.junit.jupiter.api.Test;
import java.util.Base64;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SecretCipherTest {
    private final String key = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptionIsRandomizedAndBoundToPod() {
        var cipher = new SecretCipher(key);
        UUID podId = UUID.randomUUID();
        String first = cipher.encrypt(podId, "some-SSH-credential");
        String second = cipher.encrypt(podId, "some-SSH-credential");
        assertNotEquals(first, second);
        assertEquals("some-SSH-credential", cipher.decrypt(podId, first));
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(UUID.randomUUID(), first));
    }

    @Test
    void invalidKeyCannotInitialize() {
        assertThrows(IllegalStateException.class, () -> new SecretCipher("invalid"));
    }
}
