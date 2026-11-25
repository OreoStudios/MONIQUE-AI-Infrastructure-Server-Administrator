package fr.oreostudios.monique.execution;

import fr.oreostudios.monique.security.SecretCipher;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.awaitility.Awaitility.await;

class CommandServiceTest {
    JdbcTemplate jdbc;
    CommandService service;
    SshExecutor ssh;
    UUID pod;
    DriverManagerDataSource source;
    String pin = "SHA256:" + "A".repeat(43);
    @BeforeEach void setup() throws Exception {
        source = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(source);
        String schema;
        try (var in = getClass().getResourceAsStream("/schema.sql")) { schema = new String(in.readAllBytes(), StandardCharsets.UTF_8); }
        schema = schema.replaceAll("(?m)^--[^\\r\\n]*", "").replace("TIMESTAMPTZ", "TIMESTAMP WITH TIME ZONE")
            .replace("CREATE UNIQUE INDEX IF NOT EXISTS command_jobs_running_pod ON command_jobs(pod_id) WHERE status = 'RUNNING';", "");
        for (String sql : schema.split(";")) if (!sql.isBlank()) jdbc.execute(sql);
        var cipher = new SecretCipher(Base64.getEncoder().encodeToString(new byte[32]));
        ssh = mock(SshExecutor.class);
        service = new CommandService(jdbc,cipher,ssh,new TransactionTemplate(new JdbcTransactionManager(source)),true,false);
        pod = UUID.randomUUID();
        jdbc.update("INSERT INTO pods(id,name,ssh_host,ssh_port,ssh_username,credential_type,secret_ciphertext) VALUES (?,?,?,?,?,?,?)",
            pod,"test","127.0.0.1",22,"operator","PASSWORD",cipher.encrypt(pod,"secret-password"));
    }
    @AfterEach void close() { if (service != null) service.close(); }

    @Test void proposalsNeverExecuteAndRequirePinnedHost() {
        var job = service.propose(pod,"uname -a","Inspect OS",30);
        assertEquals("PENDING", job.status());
        assertThrows(IllegalArgumentException.class, () -> service.approve(job.id()));
        verifyNoInteractions(ssh);
        assertFalse(jdbc.queryForObject("SELECT command_ciphertext FROM command_jobs WHERE id = ?",String.class,job.id()).contains("uname"));
        service.pin(pod,pin);
        assertEquals("CANCELED", service.get(job.id()).status());
        assertThrows(IllegalArgumentException.class, () -> service.approve(job.id()));
    }

    @Test void approvalRunsExactlyOnceAndSerializesCommandsPerPod() throws Exception {
        service.pin(pod,pin);
        CountDownLatch finish = new CountDownLatch(1);
        when(ssh.run(any(),anyString(),anyInt())).thenAnswer(i -> {
            finish.await(); return new SshExecutor.Result("SUCCEEDED",0,"done secret-password");
        });
        var first = service.propose(pod,"echo hello","Test",30);
        var second = service.propose(pod,"echo later","Test",30);
        try {
            assertEquals("RUNNING",service.approve(first.id()).status());
            assertThrows(IllegalArgumentException.class, () -> service.approve(first.id()));
            assertThrows(IllegalArgumentException.class, () -> service.approve(second.id()));
            assertThrows(IllegalArgumentException.class, () -> service.pin(pod,pin));
        } finally { finish.countDown(); }
        await().atMost(Duration.ofSeconds(5)).until(() -> service.get(first.id()).status().equals("SUCCEEDED"));
        assertEquals("done [REDACTED]",service.get(first.id()).output());
        assertThrows(IllegalArgumentException.class, () -> service.approve(first.id()));
        verify(ssh,times(1)).run(any(),eq("echo hello"),eq(30));
    }

    @Test void trustOnFirstUseRunsWithoutPinAndLearnsHostKey() throws Exception {
        var tofu = new CommandService(jdbc,new SecretCipher(Base64.getEncoder().encodeToString(new byte[32])),ssh,
            new TransactionTemplate(new JdbcTransactionManager(source)),true,true);
        try {
            when(ssh.run(any(),anyString(),anyInt())).thenReturn(new SshExecutor.Result("SUCCEEDED",0,"ok","SHA256:"+"a".repeat(43)));
            var job = tofu.propose(pod,"hostname","Inspect",30);
            assertEquals("RUNNING", tofu.approve(job.id()).status());
            await().atMost(Duration.ofSeconds(5)).until(() -> tofu.get(job.id()).status().equals("SUCCEEDED"));
            assertEquals("SHA256:"+"a".repeat(43), jdbc.queryForObject("SELECT ssh_fingerprint FROM pods WHERE id = ?",String.class,pod));
        } finally { tofu.close(); }
    }

    @Test void rejectedExpiredAndUnknownJobsCannotRunAgain() {
        service.pin(pod,pin);
        var rejected = service.propose(pod,"id","Inspect",30);
        service.reject(rejected.id());
        assertThrows(IllegalArgumentException.class, () -> service.approve(rejected.id()));
        var expired = service.propose(pod,"id","Inspect",30);
        jdbc.update("UPDATE command_jobs SET created_at = ? WHERE id = ?",java.time.OffsetDateTime.now().minusHours(1),expired.id());
        assertThrows(IllegalArgumentException.class, () -> service.approve(expired.id()));
        var interrupted = service.propose(pod,"id","Inspect",30);
        jdbc.update("UPDATE command_jobs SET status = 'RUNNING' WHERE id = ?",interrupted.id());
        service.recover();
        assertEquals("UNKNOWN",service.get(interrupted.id()).status());
        var next = service.propose(pod,"id","Inspect",30);
        assertThrows(IllegalArgumentException.class, () -> service.approve(next.id()));
        service.acknowledge(interrupted.id());
        assertEquals("ACKNOWLEDGED",service.get(interrupted.id()).status());
        verifyNoInteractions(ssh);
    }
}
