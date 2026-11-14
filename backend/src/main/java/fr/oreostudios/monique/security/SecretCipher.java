package fr.oreostudios.monique.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Component
public class SecretCipher {
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public SecretCipher(@Value("${MONIQUE_ENCRYPTION_KEY_BASE64:}") String encodedKey) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MONIQUE_ENCRYPTION_KEY_BASE64 must be base64", e);
        }
        if (decoded.length != 32) {
            throw new IllegalStateException("MONIQUE_ENCRYPTION_KEY_BASE64 must encode exactly 32 random bytes");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(UUID podId, String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException("A nonempty SSH password or private key is required");
        }
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            secureRandom.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(podId.toString().getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array());
        } catch (Exception e) {
            throw new IllegalStateException("Credential encryption failed", e);
        }
    }

    public String decrypt(UUID podId, String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length < NONCE_BYTES + 16) throw new IllegalArgumentException("Invalid ciphertext");
            byte[] nonce = new byte[NONCE_BYTES];
            byte[] encrypted = new byte[combined.length - NONCE_BYTES];
            System.arraycopy(combined, 0, nonce, 0, NONCE_BYTES);
            System.arraycopy(combined, NONCE_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            cipher.updateAAD(podId.toString().getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Do not echo ciphertext or credentials in error output.
            throw new IllegalStateException("Credential decryption failed");
        }
    }
}
