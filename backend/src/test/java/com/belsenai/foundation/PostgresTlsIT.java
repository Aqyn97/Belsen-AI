package com.belsenai.foundation;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.Base64;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.images.builder.Transferable;
import static org.assertj.core.api.Assertions.*;

/** Real PG17 TLS, not a mock socket; exercise the proposed JDBC SSL factory. */
class PostgresTlsIT {
    @TempDir Path directory;

    @Test void javaTrustStoreRequiresBothTrustedCertificateAndMatchingHostname() throws Exception {
        Path store = directory.resolve("server.p12");
        String password = java.util.UUID.randomUUID().toString();
        var keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
                "-genkeypair", "-alias", "fixture", "-keyalg", "RSA", "-keysize", "2048", "-storetype", "PKCS12",
                "-keystore", store.toString(), "-storepass", password, "-keypass", password,
                "-dname", "CN=localhost", "-ext", "SAN=dns:localhost", "-validity", "2", "-noprompt")
                .redirectErrorStream(true).redirectOutput(directory.resolve("keytool.log").toFile()).start();
        assertThat(keytool.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(keytool.exitValue()).isZero();
        var keys = KeyStore.getInstance("PKCS12");
        try (var stream = Files.newInputStream(store)) { keys.load(stream, password.toCharArray()); }
        var cert = keys.getCertificate("fixture");
        byte[] key = keys.getKey("fixture", password.toCharArray()).getEncoded();
        var encoder = Base64.getMimeEncoder(64, new byte[]{'\n'});
        String certPem = "-----BEGIN CERTIFICATE-----\n" + encoder.encodeToString(cert.getEncoded()) + "\n-----END CERTIFICATE-----\n";
        String keyPem = "-----BEGIN PRIVATE KEY-----\n" + encoder.encodeToString(key) + "\n-----END PRIVATE KEY-----\n";
        SSLContext original = SSLContext.getDefault();
        try (var pg = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.11-bookworm"))) {
            pg.withStartupTimeout(Duration.ofSeconds(60)).start();
            pg.copyFileToContainer(Transferable.of(certPem.getBytes(StandardCharsets.US_ASCII), 0644), "/tmp/server.crt");
            pg.copyFileToContainer(Transferable.of(keyPem.getBytes(StandardCharsets.US_ASCII), 0600), "/tmp/server.key");
            assertThat(pg.execInContainer("chown", "postgres:postgres", "/tmp/server.key").getExitCode()).isZero();
            try (var connection = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                 var statement = connection.createStatement()) {
                statement.execute("ALTER SYSTEM SET ssl_cert_file='/tmp/server.crt'");
                statement.execute("ALTER SYSTEM SET ssl_key_file='/tmp/server.key'");
                statement.execute("ALTER SYSTEM SET ssl='on'");
                statement.execute("SELECT pg_reload_conf()");
            }
            String options = "?sslmode=verify-full&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory&connectTimeout=5&socketTimeout=5";
            String matching = "jdbc:postgresql://localhost:" + pg.getMappedPort(5432) + "/" + pg.getDatabaseName() + options;
            var trust = KeyStore.getInstance("PKCS12"); trust.load(null, null); trust.setCertificateEntry("fixture", cert);
            var managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()); managers.init(trust);
            var trusted = SSLContext.getInstance("TLS"); trusted.init(null, managers.getTrustManagers(), null); SSLContext.setDefault(trusted);
            // Reload is asynchronous; retry only connection establishment, with a bounded deadline.
            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            while (true) {
                try (var c = DriverManager.getConnection(matching, pg.getUsername(), pg.getPassword()); var s = c.createStatement()) {
                    try (var result = s.executeQuery("SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()")) {
                        assertThat(result.next()).isTrue(); assertThat(result.getBoolean(1)).isTrue();
                    }
                    break;
                } catch (java.sql.SQLException pendingReload) {
                    if (System.nanoTime() > deadline) throw pendingReload;
                    Thread.sleep(50);
                }
            }
            assertThatThrownBy(() -> DriverManager.getConnection(matching.replace("//localhost:", "//127.0.0.1:"), pg.getUsername(), pg.getPassword()))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("hostname");
            SSLContext.setDefault(original);
            assertThatThrownBy(() -> DriverManager.getConnection(matching, pg.getUsername(), pg.getPassword()))
                    .isInstanceOf(java.sql.SQLException.class);
        } finally { SSLContext.setDefault(original); }
    }
}
