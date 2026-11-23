package fr.oreostudios.monique.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "admin_sessions")
public class AdminSessionEntity {
    @Id
    @Column(name = "id_hash", length = 64, nullable = false)
    private String idHash;

    @Column(name = "csrf_hash", length = 64, nullable = false)
    private String csrfHash;

    @Column(name = "admin_fingerprint", length = 64, nullable = false)
    private String adminFingerprint;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected AdminSessionEntity() {}

    public AdminSessionEntity(String idHash, String csrfHash, String adminFingerprint, Instant expiresAt) {
        this.idHash = idHash;
        this.csrfHash = csrfHash;
        this.adminFingerprint = adminFingerprint;
        this.expiresAt = expiresAt;
    }

    public String getIdHash() { return idHash; }
    public String getCsrfHash() { return csrfHash; }
    public String getAdminFingerprint() { return adminFingerprint; }
    public Instant getExpiresAt() { return expiresAt; }
}
