
package com.community;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AuthService {

    private static final int ITERATIONS = 210000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final Set<String> ROLES = Set.of(
            "ADMIN",
            "DONATION_MANAGER",
            "ALLOCATION_MANAGER",
            "VIEWER"
    );

    public static class User {
        public final int userId;
        public final String name;
        public final String email;
        public final String role;

        public User(int userId, String name, String email, String role) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.role = role;
        }
    }

    // Create the first Admin only when no login accounts exist.
    public synchronized void createInitialAdmin(
            String name,
            String email,
            String password) throws SQLException {

        validateDetails(name, email, password);

        try (Connection con = DatabaseConnection.getConnection()) {

            String countSql = "SELECT COUNT(*) FROM app_user";

            try (PreparedStatement ps = con.prepareStatement(countSql);
                 ResultSet rs = ps.executeQuery()) {

                rs.next();

                if (rs.getInt(1) > 0) {
                    throw new IllegalStateException(
                            "An account already exists. Initial setup is closed."
                    );
                }
            }

            insertUser(con, name, email, password, "ADMIN");
        }
    }

    // Admin account creation. Authorization will be enforced by ApiServlet.
    public void createUser(
            String name,
            String email,
            String password,
            String role) throws SQLException {

        validateDetails(name, email, password);

        String selectedRole = role == null
                ? ""
                : role.trim().toUpperCase();

        if (!ROLES.contains(selectedRole)) {
            throw new IllegalArgumentException("Invalid user role.");
        }

        try (Connection con = DatabaseConnection.getConnection()) {
            insertUser(con, name, email, password, selectedRole);
        }
    }

    private void insertUser(
            Connection con,
            String name,
            String email,
            String password,
            String role) throws SQLException {

        String sql = """
                INSERT INTO app_user
                (name, email, password_hash, role, is_active)
                VALUES (?, ?, ?, ?, TRUE)
                """;

        try (PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name.trim());
            ps.setString(2, email.trim().toLowerCase());
            ps.setString(3, hashPassword(password));
            ps.setString(4, role);

            ps.executeUpdate();
        }
    }

    // Returns null when the email or password is incorrect.
    public User authenticate(
            String email,
            String password) throws SQLException {

        if (email == null || password == null
                || email.isBlank() || password.isEmpty()) {
            return null;
        }

        String sql = """
                SELECT user_id, name, email, password_hash, role
                FROM app_user
                WHERE email = ? AND is_active = TRUE
                """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, email.trim().toLowerCase());

            try (ResultSet rs = ps.executeQuery()) {

                if (!rs.next()) {
                    return null;
                }

                String storedHash = rs.getString("password_hash");

                if (!verifyPassword(password, storedHash)) {
                    return null;
                }

                return new User(
                        rs.getInt("user_id"),
                        rs.getString("name"),
                        rs.getString("email"),
                        rs.getString("role")
                );
            }
        }
    }

    private void validateDetails(
            String name,
            String email,
            String password) {

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name is required.");
        }

        if (email == null || email.isBlank()
                || !email.contains("@")) {
            throw new IllegalArgumentException("Enter a valid email.");
        }

        if (password == null || password.length() < 10) {
            throw new IllegalArgumentException(
                    "Password must contain at least 10 characters."
            );
        }
    }

    private String hashPassword(String password) {

        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);

        byte[] hash = deriveKey(password.toCharArray(), salt);

        return "pbkdf2$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(hash);
    }

    private boolean verifyPassword(
            String password,
            String storedHash) {

        try {
            String[] parts = storedHash.split("\\$");

            if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
                return false;
            }

            int iterations = Integer.parseInt(parts[1]);

            if (iterations < 100000 || iterations > 1000000) {
                return false;
            }

            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);

            byte[] actual = deriveKey(
                    password.toCharArray(),
                    salt,
                    iterations
            );

            return MessageDigest.isEqual(expected, actual);

        } catch (Exception e) {
            return false;
        }
    }

    private byte[] deriveKey(char[] password, byte[] salt) {
        return deriveKey(password, salt, ITERATIONS);
    }

    private byte[] deriveKey(
            char[] password,
            byte[] salt,
            int iterations) {

        PBEKeySpec spec = new PBEKeySpec(
                password, salt, iterations, KEY_LENGTH
        );

        try {
            SecretKeyFactory factory =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");

            return factory.generateSecret(spec).getEncoded();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Password security operation failed.", e
            );

        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(password, '\0');
        }
    }
}
