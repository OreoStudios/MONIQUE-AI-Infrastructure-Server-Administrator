package fr.oreostudios.monique.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface AdminSessionRepository extends JpaRepository<AdminSessionEntity, String> {
    @Modifying
    @Query("delete from AdminSessionEntity s where s.expiresAt <= :now")
    int deleteExpired(Instant now);
}
