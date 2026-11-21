package com.belsenai.identity.tools;

import com.belsenai.identity.dto.RegisterRequest;
import jakarta.validation.Validation;
import java.io.Console;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/** Explicit OFFLINE operator utility. No Spring context, server, database access or startup hook. */
public final class PreviewAccountSql {
    private PreviewAccountSql() { }
    public static void main(String[] args) {
        Console console = System.console();
        if (args.length != 1 || console == null) {
            System.err.println("Use an interactive terminal and one new SQL output path inside your private 0700 directory.");
            System.exit(1);return;
        }
        char[] password = null, repeated = null;
        int exit = 0;
        try {
            Path output = Path.of(args[0]).toAbsolutePath().normalize();
            requirePrivateDirectory(output.getParent());
            if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) throw new IllegalArgumentException("Output must be a new file.");
            String email = console.readLine("Approved individual email: ");
            String name = console.readLine("Display name (use fictional demonstration data): ");
            String timezone = console.readLine("IANA timezone: ");
            password = console.readPassword("Individual password (not echoed): ");
            repeated = console.readPassword("Repeat password: ");
            writePrivate(output, prepare(email, name, timezone, password, repeated));
            console.printf("Created private SQL with a salted BCrypt hash. Review it, apply through protected psql stdin, then remove it. No account or session has been created.%n");
        } catch (IllegalArgumentException invalid) {
            System.err.println(invalid.getMessage());exit = 1;
        } catch (IOException | UnsupportedOperationException failure) {
            System.err.println("SQL preparation failed. Check the private directory, POSIX permissions and unused output path.");exit = 1;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (repeated != null) Arrays.fill(repeated, '\0');
        }
        if (exit != 0) System.exit(exit);
    }

    static String prepare(String email, String name, String timezone, char[] password, char[] repeated) {
        if (password == null || repeated == null || !Arrays.equals(password, repeated))
            throw new IllegalArgumentException("Password confirmation does not match.");
        var input = new RegisterRequest(email, new String(password), name, timezone);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            if (!factory.getValidator().validate(input).isEmpty())
                throw new IllegalArgumentException("Invalid account fields. Use a valid email/name/IANA timezone and a password of 12 characters through 72 UTF-8 bytes.");
        }
        String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(input.password());
        Instant now = Instant.now();
        return "BEGIN;\nSET LOCAL standard_conforming_strings = on;\n"
                + "INSERT INTO users (id,email,password_hash,display_name,timezone,created_at,updated_at) VALUES ("
                + literal(UUID.randomUUID().toString()) + "," + literal(input.email()) + "," + literal(hash) + ","
                + literal(input.displayName()) + "," + literal(input.timezone()) + "," + literal(now.toString()) + "," + literal(now.toString())
                + ");\nCOMMIT;\n";
    }

    static void requirePrivateDirectory(Path directory) throws IOException {
        if (directory == null || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------"))
                || !Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS).getName().equals(System.getProperty("user.name")))
            throw new IllegalArgumentException("Output requires your existing private directory with mode 0700.");
    }

    static void writePrivate(Path output, String sql) throws IOException {
        requirePrivateDirectory(output.getParent());
        try (var file = FileChannel.open(output, Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS),
                PosixFilePermissions.asFileAttribute(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)))) {
            ByteBuffer bytes = StandardCharsets.UTF_8.encode(sql);
            while (bytes.hasRemaining()) file.write(bytes);
            file.force(true);
        }
    }
    private static String literal(String value) { return "'" + value.replace("'", "''") + "'"; }
}
