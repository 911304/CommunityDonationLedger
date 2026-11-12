
package com.community;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AllocationService {

    public void create(
            int donationId,
            int beneficiaryId,
            int adminId,
            BigDecimal amountAllocated,
            String purpose) throws SQLException {

        if (amountAllocated == null || amountAllocated.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Allocation amount must be greater than zero.");
        }

        if (purpose == null || purpose.trim().isEmpty()) {
            throw new IllegalArgumentException("Purpose is required.");
        }

        String donationSql =
                "SELECT amount FROM donation WHERE donation_id = ? FOR UPDATE";

        String allocatedSql =
                "SELECT COALESCE(SUM(amount_allocated), 0) AS total " +
                "FROM allocation WHERE donation_id = ? AND status <> 'CANCELLED'";

        String beneficiarySql =
                "SELECT beneficiary_id FROM beneficiary WHERE beneficiary_id = ?";

        String insertSql =
                "INSERT INTO allocation " +
                "(donation_id, beneficiary_id, admin_id, allocation_date, amount_allocated, purpose, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'ALLOCATED')";

        try (Connection con = DatabaseConnection.getConnection()) {

            con.setAutoCommit(false);

            try {
                BigDecimal donationAmount;

                try (PreparedStatement ps = con.prepareStatement(donationSql)) {
                    ps.setInt(1, donationId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Donation not found.");
                        }

                        donationAmount = rs.getBigDecimal("amount");
                    }
                }

                BigDecimal alreadyAllocated;

                try (PreparedStatement ps = con.prepareStatement(allocatedSql)) {
                    ps.setInt(1, donationId);

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        alreadyAllocated = rs.getBigDecimal("total");
                    }
                }

                if (alreadyAllocated.add(amountAllocated).compareTo(donationAmount) > 0) {
                    throw new SQLException("Allocation exceeds the available donation balance.");
                }

                try (PreparedStatement ps = con.prepareStatement(beneficiarySql)) {
                    ps.setInt(1, beneficiaryId);

                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Beneficiary not found.");
                        }
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                    ps.setInt(1, donationId);
                    ps.setInt(2, beneficiaryId);
                    ps.setInt(3, adminId);
                    ps.setDate(4, new Date(System.currentTimeMillis()));
                    ps.setBigDecimal(5, amountAllocated);
                    ps.setString(6, purpose.trim());

                    ps.executeUpdate();
                }

                con.commit();

            } catch (Exception e) {
                con.rollback();

                if (e instanceof SQLException) {
                    throw (SQLException) e;
                }

                throw new SQLException("Allocation failed: " + e.getMessage(), e);

            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    public List<Map<String, Object>> list() throws SQLException {

        List<Map<String, Object>> allocations = new ArrayList<>();

        String sql =
                "SELECT a.allocation_id, a.donation_id, b.name AS beneficiary_name, " +
                "a.amount_allocated, a.purpose, a.status, a.allocation_date " +
                "FROM allocation a " +
                "JOIN beneficiary b ON a.beneficiary_id = b.beneficiary_id " +
                "ORDER BY a.allocation_id DESC";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();

                row.put("allocation_id", rs.getInt("allocation_id"));
                row.put("donation_id", rs.getInt("donation_id"));
                row.put("beneficiary_name", rs.getString("beneficiary_name"));
                row.put("amount_allocated", rs.getBigDecimal("amount_allocated"));
                row.put("purpose", rs.getString("purpose"));
                row.put("status", rs.getString("status"));
                row.put("allocation_date", rs.getDate("allocation_date"));

                allocations.add(row);
            }
        }

        return allocations;
    }

    public void updateStatus(int allocationId, String status) throws SQLException {

        String normalizedStatus = status == null ? "" : status.trim().toUpperCase();

        if (!normalizedStatus.equals("PENDING")
                && !normalizedStatus.equals("ALLOCATED")
                && !normalizedStatus.equals("FULFILLED")
                && !normalizedStatus.equals("CANCELLED")) {
            throw new IllegalArgumentException("Invalid allocation status.");
        }

        String sql =
                "UPDATE allocation SET status = ? WHERE allocation_id = ?";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, normalizedStatus);
            ps.setInt(2, allocationId);

            if (ps.executeUpdate() == 0) {
                throw new SQLException("Allocation not found.");
            }
        }
    }
}
