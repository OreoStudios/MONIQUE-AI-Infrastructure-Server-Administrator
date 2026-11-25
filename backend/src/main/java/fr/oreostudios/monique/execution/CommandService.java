package fr.oreostudios.monique.execution;

import fr.oreostudios.monique.security.SecretCipher;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

@Service
public class CommandService {
    private final JdbcTemplate jdbc;
    private final SecretCipher cipher;
    private final SshExecutor ssh;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final boolean trustOnFirstUse;
    private final ExecutorService workers = Executors.newFixedThreadPool(4);
    private final Semaphore slots = new Semaphore(4);

    public CommandService(JdbcTemplate jdbc, SecretCipher cipher, SshExecutor ssh,
                          TransactionTemplate tx, @Value("${MONIQUE_EXECUTION_ENABLED:false}") boolean enabled,
                          @Value("${MONIQUE_SSH_TRUST_ON_FIRST_USE:false}") boolean trustOnFirstUse) {
        this.jdbc = jdbc; this.cipher = cipher; this.ssh = ssh; this.tx = tx; this.enabled = enabled;
        this.trustOnFirstUse = trustOnFirstUse;
    }
    public boolean enabled() { return enabled; }
    public boolean trustOnFirstUse() { return trustOnFirstUse; }
    private void requireEnabled() { if (!enabled) throw new IllegalArgumentException("Enable MONIQUE_EXECUTION_ENABLED in the backend before running SSH commands."); }
    public record Job(UUID id, UUID podId, String command, String purpose, String status,
                      int timeoutSeconds, String output, Integer exitCode, String createdAt) { }
    public record Trust(String fingerprint) { }
    public Trust trust(UUID podId) {
        var found = jdbc.query("SELECT ssh_fingerprint FROM pods WHERE id = ?", (rs, n) -> new Trust(rs.getString(1)), podId);
        if (found.isEmpty()) throw new IllegalArgumentException("Pod not found");
        return found.getFirst();
    }
    public void pin(UUID podId, String fingerprint) {
        if (fingerprint == null || !fingerprint.matches("SHA256:[A-Za-z0-9+/]{43}"))
            throw new IllegalArgumentException("Enter a SHA256 SSH host fingerprint from your trusted VPS console.");
        tx.executeWithoutResult(s -> {
            lockPod(podId);
            if (jdbc.queryForObject("SELECT count(*) FROM command_jobs WHERE pod_id = ? AND status = 'RUNNING'", Integer.class, podId) > 0)
                throw new IllegalArgumentException("Wait for the active SSH job before changing host trust.");
            jdbc.update("UPDATE pods SET ssh_fingerprint = ? WHERE id = ?", fingerprint, podId);
            jdbc.update("UPDATE command_jobs SET status = 'CANCELED', finished_at = NOW() WHERE pod_id = ? AND status = 'PENDING'", podId);
            audit(podId, "SSH_HOST_PINNED", "Host fingerprint saved; pending approvals invalidated");
        });
    }
    private void lockPod(UUID podId) {
        if (jdbc.queryForList("SELECT id FROM pods WHERE id = ? FOR UPDATE", podId).isEmpty())
            throw new IllegalArgumentException("Pod not found");
    }
    public Job propose(UUID podId, String command, String purpose, int timeout) {
        requireEnabled();
        if (podId == null || command == null || command.isBlank() || command.length() > 12000 || command.indexOf('\0') >= 0
            || purpose == null || purpose.isBlank() || purpose.length() > 2000 || timeout < 10 || timeout > 1800)
            throw new IllegalArgumentException("Provide a command (up to 12000 characters), purpose and timeout of 10..1800 seconds.");
        return tx.execute(s -> {
            lockPod(podId);
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO command_jobs(id,pod_id,command_ciphertext,purpose,timeout_seconds) VALUES (?,?,?,?,?)",
                id, podId, cipher.encrypt(id, command), purpose, timeout);
            audit(podId, "COMMAND_PROPOSED", "Pending command " + id);
            return get(id);
        });
    }
    public List<Job> list(UUID podId) {
        return jdbc.query("SELECT * FROM command_jobs WHERE pod_id = ? ORDER BY CASE WHEN status IN ('RUNNING','UNKNOWN') THEN 0 ELSE 1 END, created_at DESC LIMIT 20", (rs, n) -> {
            UUID id = rs.getObject("id", UUID.class);
            String output = rs.getString("output_ciphertext");
            return new Job(id, podId, cipher.decrypt(id, rs.getString("command_ciphertext")), rs.getString("purpose"),
                rs.getString("status"), rs.getInt("timeout_seconds"), output == null ? "" : cipher.decrypt(id, output),
                (Integer) rs.getObject("exit_code"), rs.getObject("created_at", OffsetDateTime.class).toString());
        }, podId);
    }
    public Job get(UUID id) {
        var ids = jdbc.query("SELECT pod_id FROM command_jobs WHERE id = ?", (rs, n) -> rs.getObject(1, UUID.class), id);
        if (ids.isEmpty()) throw new IllegalArgumentException("Command job not found");
        // Direct lookup must still work when more than 20 jobs exist.
        return jdbc.queryForObject("SELECT * FROM command_jobs WHERE id = ?", (rs,n) -> {
            String out = rs.getString("output_ciphertext");
            return new Job(id, ids.getFirst(), cipher.decrypt(id, rs.getString("command_ciphertext")), rs.getString("purpose"),
                rs.getString("status"), rs.getInt("timeout_seconds"), out == null ? "" : cipher.decrypt(id,out),
                (Integer) rs.getObject("exit_code"), rs.getObject("created_at",OffsetDateTime.class).toString());
        }, id);
    }
    public Job reject(UUID id) {
        return tx.execute(s -> {
            Job job = get(id);
            if (jdbc.update("UPDATE command_jobs SET status = 'REJECTED', finished_at = NOW() WHERE id = ? AND status = 'PENDING'", id) != 1)
                throw new IllegalArgumentException("Only pending commands can be rejected.");
            audit(job.podId(), "COMMAND_REJECTED", "Rejected command " + id);
            return get(id);
        });
    }
    public Job approve(UUID id) {
        requireEnabled();
        if (!slots.tryAcquire()) throw new IllegalArgumentException("All SSH workers are busy. Retry later.");
        try {
            Job job = get(id);
            SshExecutor.Target target = tx.execute(s -> {
                lockPod(job.podId());
                if (jdbc.queryForObject("SELECT count(*) FROM command_jobs WHERE pod_id = ? AND status IN ('RUNNING','UNKNOWN')", Integer.class, job.podId()) > 0)
                    throw new IllegalArgumentException("This pod has a running or unknown command. Inspect and acknowledge unknown outcomes before continuing.");
                var t = jdbc.queryForObject("SELECT * FROM pods WHERE id = ?", (rs,n) -> new SshExecutor.Target(
                    rs.getString("ssh_host"),rs.getInt("ssh_port"),rs.getString("ssh_username"),rs.getString("credential_type"),
                    cipher.decrypt(job.podId(),rs.getString("secret_ciphertext")),rs.getString("ssh_fingerprint")), job.podId());
                if (t.fingerprint() == null && !trustOnFirstUse) throw new IllegalArgumentException("Verify and save the SSH host fingerprint first.");
                if (jdbc.update("UPDATE command_jobs SET status = 'RUNNING' WHERE id = ? AND status = 'PENDING' AND created_at > ?", id, OffsetDateTime.now().minusMinutes(30)) != 1)
                    throw new IllegalArgumentException("Command already handled or approval expired (30 minutes). Create a new proposal.");
                audit(job.podId(), "COMMAND_APPROVED", "Approved exact command " + id);
                return t;
            });
            Job running = get(id);
            try { workers.submit(() -> {
                try {
                    var result = ssh.run(target, job.command(), job.timeoutSeconds());
                    tx.executeWithoutResult(s -> {
                        // Trust on first use: pin the host key captured on this first connection.
                        if (result.learnedFingerprint() != null
                            && jdbc.update("UPDATE pods SET ssh_fingerprint = ? WHERE id = ? AND ssh_fingerprint IS NULL", result.learnedFingerprint(), job.podId()) == 1)
                            audit(job.podId(), "SSH_HOST_PINNED", "Host fingerprint learned on first connection (trust on first use)");
                        String output = result.output().replace(target.credential(), "[REDACTED]");
                        jdbc.update("UPDATE command_jobs SET status = ?, output_ciphertext = ?, exit_code = ?, finished_at = NOW() WHERE id = ? AND status = 'RUNNING'",
                            result.status(), cipher.encrypt(id, output.isBlank() ? "[No output]" : output), result.exitCode(), id);
                        audit(job.podId(), "COMMAND_FINISHED", "Command " + id + " " + result.status() + " exit=" + result.exitCode());
                    });
                } catch (RuntimeException e) {
                    org.slf4j.LoggerFactory.getLogger(CommandService.class).error("Failed to persist SSH result for job {}. Remote outcome requires inspection.", id);
                    jdbc.update("UPDATE command_jobs SET status = 'UNKNOWN', finished_at = NOW() WHERE id = ? AND status = 'RUNNING'", id);
                } finally { slots.release(); }
            }); } catch (RejectedExecutionException e) {
                jdbc.update("UPDATE command_jobs SET status = 'UNKNOWN', finished_at = NOW() WHERE id = ? AND status = 'RUNNING'", id);
                throw new IllegalArgumentException("Worker stopped; inspect the job before continuing.");
            }
            return running;
        } catch (RuntimeException e) { slots.release(); throw e; }
    }
    public Job acknowledge(UUID id) {
        return tx.execute(s -> {
            Job job = get(id);
            lockPod(job.podId());
            if (jdbc.update("UPDATE command_jobs SET status = 'ACKNOWLEDGED' WHERE id = ? AND status = 'UNKNOWN'", id) != 1)
                throw new IllegalArgumentException("Only unknown outcomes can be acknowledged.");
            audit(job.podId(), "COMMAND_ACKNOWLEDGED", "Operator inspected remote outcome for " + id);
            return get(id);
        });
    }
    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        // Single backend deployment. Never replay a command after a crash/redeploy.
        jdbc.update("UPDATE command_jobs SET status = 'UNKNOWN', finished_at = NOW() WHERE status = 'RUNNING'");
    }
    private void audit(UUID pod, String event, String detail) {
        jdbc.update("INSERT INTO audit_events(id,event,pod_id,details) VALUES (?,?,?,?)",UUID.randomUUID(),event,pod,detail);
    }
    @PreDestroy public void close() { workers.shutdownNow(); }
}
