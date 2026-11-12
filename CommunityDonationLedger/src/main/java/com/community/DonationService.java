package com.community;

import java.sql.*;
import java.math.BigDecimal;
import java.util.*;

public class DonationService {

    public static void create(
            String name,
            String phone,
            String email,
            String address,
            long adminId,
            String date,
            String type,
            BigDecimal amount,
            String description) throws SQLException {

        String donorSql =
                "INSERT INTO donor (name, phone, email, address) VALUES (?, ?, ?, ?)";

        String donationSql =
                "INSERT INTO donation (donor_id, admin_id, donation_date, donation_type, amount, description) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                long donorId;

                try (PreparedStatement ps = con.prepareStatement(
                        donorSql, Statement.RETURN_GENERATED_KEYS)) {

                    ps.setString(1, name);
                    ps.setString(2, phone);
                    ps.setString(3, email);
                    ps.setString(4, address);

                    ps.executeUpdate();

                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) {
                            throw new SQLException("Donor creation failed.");
                        }

                        donorId = rs.getLong(1);
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(donationSql)) {

                    ps.setLong(1, donorId);
                    ps.setLong(2, adminId);
                    ps.setDate(3, java.sql.Date.valueOf(date));
                    ps.setString(4, type);
                    ps.setBigDecimal(5, amount);
                    ps.setString(6, description);

                    ps.executeUpdate();
                }

                con.commit();

            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    public static List<Map<String, Object>> list() throws SQLException {

        List<Map<String, Object>> donations = new ArrayList<>();

        String sql =
                "SELECT d.donation_id, dr.name AS donor_name, " +
                "d.donation_date, d.donation_type, d.amount, d.description " +
                "FROM donation d JOIN donor dr ON d.donor_id = dr.donor_id " +
                "ORDER BY d.donation_id DESC";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {

                Map<String, Object> row = new LinkedHashMap<>();

                row.put("donation_id", rs.getLong("donation_id"));
                row.put("donor_name", rs.getString("donor_name"));
                row.put("donation_date", rs.getDate("donation_date").toString());
                row.put("donation_type", rs.getString("donation_type"));
                row.put("amount", rs.getBigDecimal("amount"));
                row.put("description", rs.getString("description"));

                donations.add(row);
            }
        }

        return donations;
    }
}