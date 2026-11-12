package com.community;

import java.sql.*;
import java.util.*;

public class DonorService {

    public void create(String name, String phone,
                       String email, String address) throws SQLException {

        String sql = """
                INSERT INTO donor (name, phone, email, address)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, phone);
            ps.setString(3, email);
            ps.setString(4, address);
            ps.executeUpdate();
        }
    }

    public List<Map<String, Object>> list() throws SQLException {

        List<Map<String, Object>> donors = new ArrayList<>();

        String sql = """
                SELECT donor_id, name, phone, email, address
                FROM donor ORDER BY donor_id DESC
                """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();

                row.put("donor_id", rs.getInt("donor_id"));
                row.put("name", rs.getString("name"));
                row.put("phone", rs.getString("phone"));
                row.put("email", rs.getString("email"));
                row.put("address", rs.getString("address"));

                donors.add(row);
            }
        }

        return donors;
    }
}