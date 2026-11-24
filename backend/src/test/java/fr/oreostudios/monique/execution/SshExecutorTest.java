package fr.oreostudios.monique.execution;

import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.command.Command;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.junit.jupiter.api.*;
import java.io.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SshExecutorTest {
    SshServer server;
    String pin;
    AtomicInteger authentications = new AtomicInteger();
    AtomicInteger executions = new AtomicInteger();
    @BeforeEach void start() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        var pair = generator.generateKeyPair();
        pin = KeyUtils.getFingerPrint(pair.getPublic());
        server = SshServer.setUpDefaultServer();
        server.setHost("127.0.0.1"); server.setPort(0);
        server.setKeyPairProvider(session -> List.of(pair));
        server.setPasswordAuthenticator((user,pass,session) -> { authentications.incrementAndGet(); return user.equals("operator") && pass.equals("test-secret"); });
        server.setCommandFactory((channel,command) -> new Command() {
            OutputStream out; OutputStream err; ExitCallback callback;
            public void setInputStream(InputStream in) { }
            public void setOutputStream(OutputStream stream) { out = stream; }
            public void setErrorStream(OutputStream stream) { err = stream; }
            public void setExitCallback(ExitCallback cb) { callback = cb; }
            public void start(ChannelSession session, Environment env) throws IOException {
                executions.incrementAndGet();
                if (command.equals("hang")) return;
                out.write("hello test-secret\n".getBytes()); out.flush();
                err.write("stderr\n".getBytes()); err.flush();
                callback.onExit(command.equals("fail") ? 7 : 0);
            }
            public void destroy(ChannelSession session) { }
        });
        server.start();
    }
    @AfterEach void stop() throws Exception { server.stop(true); }
    SshExecutor.Target target(String fingerprint) { return new SshExecutor.Target("127.0.0.1",server.getPort(),"operator","PASSWORD","test-secret",fingerprint); }
    @Test void verifiedSshCapturesExitAndRedactsCredential() {
        var result = new SshExecutor().run(target(pin),"fail",10);
        assertEquals("FAILED",result.status()); assertEquals(7,result.exitCode());
        assertTrue(result.output().contains("hello [REDACTED]"));
        assertTrue(result.output().contains("stderr"));
        assertEquals(1,executions.get());
    }
    @Test void mismatchedHostKeyRejectsBeforeAuthenticationOrExecution() {
        var result = new SshExecutor().run(target("SHA256:" + "A".repeat(43)),"test",10);
        assertEquals("FAILED",result.status());
        assertEquals(0,authentications.get()); assertEquals(0,executions.get());
    }
    @Test void deadlineIsUnknownRatherThanFalseSuccess() {
        var result = new SshExecutor().run(target(pin),"hang",1);
        assertEquals("UNKNOWN",result.status()); assertEquals(-1,result.exitCode());
    }
    @Test void outputIsBounded() throws Exception {
        var out = new SshExecutor.LimitedOutput();
        out.write(new byte[100000]);
        assertTrue(out.text().endsWith("[Output truncated]"));
        assertTrue(out.text().length() < 66000);
    }
    @Test void storedPrivateKeyAuthenticatesOverRealSsh() throws Exception {
        server.setPublickeyAuthenticator((user,key,session) -> user.equals("operator"));
        var key = com.jcraft.jsch.KeyPair.genKeyPair(new com.jcraft.jsch.JSch(),com.jcraft.jsch.KeyPair.RSA,2048);
        var encoded = new ByteArrayOutputStream();
        key.writePrivateKey(encoded);
        try {
            var target = new SshExecutor.Target("127.0.0.1",server.getPort(),"operator","PRIVATE_KEY",
                encoded.toString(java.nio.charset.StandardCharsets.UTF_8),pin);
            var result = new SshExecutor().run(target,"success",10);
            assertEquals("SUCCEEDED",result.status()); assertEquals(0,result.exitCode());
        } finally { key.dispose(); }
    }
}
