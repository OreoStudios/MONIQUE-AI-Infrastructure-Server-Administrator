package fr.oreostudios.monique.execution;

import com.jcraft.jsch.*;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class SshExecutor {
    public record Target(String host, int port, String username, String credentialType,
                         String credential, String fingerprint) {
        @Override public String toString() { return "SSH target [credentials hidden]"; }
    }
    public record Result(String status, int exitCode, String output) { }

    static boolean matches(String expected, byte[] key) {
        try {
            String actual = "SHA256:" + Base64.getEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(key));
            return expected != null && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception e) { return false; }
    }

    public Result run(Target target, String command, int seconds) {
        JSch jsch = new JSch();
        Session session = null;
        ChannelExec channel = null;
        LimitedOutput output = new LimitedOutput();
        boolean dispatched = false;
        try {
            jsch.setHostKeyRepository(new HostKeyRepository() {
                public int check(String host, byte[] key) { return matches(target.fingerprint(), key) ? OK : CHANGED; }
                public void add(HostKey key, UserInfo info) { throw new IllegalStateException("Host trust must be configured explicitly"); }
                public void remove(String host, String type) { }
                public void remove(String host, String type, byte[] key) { }
                public String getKnownHostsRepositoryID() { return "MONIQUE pinned SHA256 host key"; }
                public HostKey[] getHostKey() { return new HostKey[0]; }
                public HostKey[] getHostKey(String host, String type) { return new HostKey[0]; }
            });
            if ("PRIVATE_KEY".equals(target.credentialType())) {
                byte[] key = target.credential().getBytes(StandardCharsets.UTF_8);
                try { jsch.addIdentity("pod", key, null, null); }
                finally { java.util.Arrays.fill(key, (byte) 0); }
            }
            session = jsch.getSession(target.username(), target.host(), target.port());
            session.setConfig("StrictHostKeyChecking", "yes");
            session.setConfig("PreferredAuthentications", "PRIVATE_KEY".equals(target.credentialType()) ? "publickey" : "password");
            if ("PASSWORD".equals(target.credentialType())) session.setPassword(target.credential());
            session.setServerAliveInterval(15000);
            session.setServerAliveCountMax(3);
            session.connect(15000);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setPty(false);
            channel.setInputStream(null);
            channel.setOutputStream(output, true);
            channel.setErrStream(output, true);
            channel.setCommand(command);
            dispatched = true;
            channel.connect(10000);
            long deadline = System.nanoTime() + seconds * 1_000_000_000L;
            while (!channel.isClosed()) {
                if (!session.isConnected() || System.nanoTime() > deadline) {
                    return new Result("UNKNOWN", -1, output.text().replace(target.credential(), "[REDACTED]") + "\nConnection lost or deadline reached. Remote processes may still be running; inspect before retrying.");
                }
                Thread.sleep(100);
            }
            int code = channel.getExitStatus();
            return new Result(code == 0 ? "SUCCEEDED" : code < 0 ? "UNKNOWN" : "FAILED", code,
                output.text().replace(target.credential(), "[REDACTED]"));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            // Exception messages may include credentials or server-controlled data.
            return new Result(dispatched ? "UNKNOWN" : "FAILED", -1,
                dispatched ? "SSH interrupted after dispatch. Remote outcome unknown; inspect before retrying."
                    : "SSH connection failed. Verify host fingerprint, network access, username and stored credential. Encrypted private keys are not supported.");
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
            try { jsch.removeAllIdentity(); } catch (JSchException ignored) { }
        }
    }

    static class LimitedOutput extends OutputStream {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private boolean truncated;
        @Override public synchronized void write(int b) { if (bytes.size() < 65536) bytes.write(b); else truncated = true; }
        @Override public synchronized void write(byte[] b, int off, int len) {
            int take = Math.min(len, 65536 - bytes.size());
            bytes.write(b, off, take);
            if (take < len) truncated = true;
        }
        synchronized String text() { return bytes.toString(StandardCharsets.UTF_8) + (truncated ? "\n[Output truncated]" : ""); }
    }
}
