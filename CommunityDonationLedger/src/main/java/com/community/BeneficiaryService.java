package com.community;

import java.sql.*;
import java.util.*;

public class BeneficiaryService {

    public void create(String name, String category,
                       String contact, String address) throws SQLException {

        String sql = """
                INSERT INTO beneficiary (name, category, contact, address)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setString(2, category);
            ps.setString(3, contact);
            ps.setString(4, address);

            ps.executeUpdate();
        }
    }

    public List<Map<String, Object>> list() throws SQLException {

        List<Map<String, Object>> beneficiaries = new ArrayList<>();

        String sql = """
                SELECT beneficiary_id, name, category, contact, address
                FROM beneficiary
                ORDER BY beneficiary_id DESC
                """;

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();

                row.put("beneficiary_id", rs.getInt("beneficiary_id"));
                row.put("name", rs.getString("name"));
                row.put("category", rs.getString("category"));
                row.put("contact", rs.getString("contact"));
                row.put("address", rs.getString("address"));

                beneficiaries.add(row);
            }
        }

        return beneficiaries;
    }
}