package com.belsenai.identity.tools;

import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import static org.assertj.core.api.Assertions.*;

class PreviewAccountSqlTest {
    @TempDir Path temporary;
    private static final char[] PASSWORD = "private individual fixture password".toCharArray();
    @Test void normalizesAndEscapesOneIndividualWithoutPlaintextPassword() {
        String sql = PreviewAccountSql.prepare("  APPROVED@EXAMPLE.TEST  ", "  Sam'; DROP TABLE users;--  ", "UTC", PASSWORD, PASSWORD);
        assertThat(sql).contains("'approved@example.test'", "'Sam''; DROP TABLE users;--'", "SET LOCAL standard_conforming_strings = on;")
                .startsWith("BEGIN;\n").endsWith("COMMIT;\n").doesNotContain(new String(PASSWORD));
        String hash = sql.substring(sql.indexOf("{bcrypt}"), sql.indexOf("'", sql.indexOf("{bcrypt}")));
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches(new String(PASSWORD), hash)).isTrue();
    }
    @Test void rejectsMismatchedPasswordAndInvalidFieldsWithoutEchoingValues() {
        assertThatThrownBy(() -> PreviewAccountSql.prepare("approved@example.test", "User", "UTC", PASSWORD, "different".toCharArray()))
                .hasMessage("Password confirmation does not match.");
        for (String zone : new String[]{"+05:00", "invalid-sensitive-zone"})
            assertThatThrownBy(() -> PreviewAccountSql.prepare("approved@example.test", "User", zone, PASSWORD, PASSWORD))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(zone);
        assertThatThrownBy(() -> PreviewAccountSql.prepare("bad-sensitive-email", "User", "UTC", PASSWORD, PASSWORD))
                .hasMessageNotContaining("bad-sensitive-email");
    }
    @Test void requiresPrivateDirectoryAndCreates0600WithoutOverwriteOrSymlink() throws Exception {
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rwx------"));
        Path sql = temporary.resolve("approved.sql");PreviewAccountSql.writePrivate(sql, "fixture SQL");
        assertThat(Files.getPosixFilePermissions(sql)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
        assertThatThrownBy(() -> PreviewAccountSql.writePrivate(sql, "overwrite")).isInstanceOf(FileAlreadyExistsException.class);
        assertThat(Files.readString(sql)).isEqualTo("fixture SQL");
        Path link = temporary.resolve("link.sql");Files.createSymbolicLink(link, sql);
        assertThatThrownBy(() -> PreviewAccountSql.writePrivate(link, "overwrite")).isInstanceOf(FileAlreadyExistsException.class);
        Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rwxr-xr-x"));
        assertThatThrownBy(() -> PreviewAccountSql.writePrivate(temporary.resolve("other.sql"), "fixture"))
                .hasMessageContaining("private directory");
    }
}
