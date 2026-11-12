
package com.community;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@WebServlet("/api/*")
public class ApiServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final Gson gson = new Gson();
    private final AuthService authService = new AuthService();

    private final DonorService donorService = new DonorService();
    private final BeneficiaryService beneficiaryService =
            new BeneficiaryService();
    private final AllocationService allocationService =
            new AllocationService();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        prepareResponse(response);
        String path = request.getPathInfo();

        try {
            if (path == null || path.equals("/")) {
                send(response, 200, Map.of(
                        "application", "Community Donation Ledger",
                        "status", "Running",
                        "message", "API is working successfully"
                ));
                return;
            }

            if (path.equals("/setup-status")) {
                send(response, 200, Map.of(
                        "setupRequired", !hasAccounts()
                ));
                return;
            }

            if (path.equals("/me")) {
                if (!requireLogin(request, response)) return;

                HttpSession session = request.getSession(false);

                send(response, 200, Map.of(
                        "userId", session.getAttribute("userId"),
                        "name", session.getAttribute("name"),
                        "email", session.getAttribute("email"),
                        "role", session.getAttribute("role")
                ));
                return;
            }

            if (!requireLogin(request, response)) return;

            switch (path) {
                case "/donations":
                    send(response, 200, DonationService.list());
                    break;

                case "/donors":
                    send(response, 200, donorService.list());
                    break;

                case "/beneficiaries":
                    send(response, 200, beneficiaryService.list());
                    break;

                case "/allocations":
                    send(response, 200, allocationService.list());
                    break;

                default:
                    sendError(response, 404, "API endpoint not found.");
            }

        } catch (Exception e) {
            handleError(response, e);
        }
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        prepareResponse(response);
        String path = request.getPathInfo();

        try {
            if (path == null) {
                sendError(response, 400, "Invalid API endpoint.");
                return;
            }

            // First Admin setup: available only before the first account.
            if (path.equals("/setup")) {
                if (hasAccounts()) {
                    sendError(response, 403,
                            "Initial setup is already completed.");
                    return;
                }

                JsonObject data = readJson(request);

                authService.createInitialAdmin(
                        getString(data, "name"),
                        getString(data, "email"),
                        getString(data, "password")
                );

                send(response, 201, Map.of(
                        "message", "Initial Admin account created. Please log in."
                ));
                return;
            }

            // Login
            if (path.equals("/login")) {
                JsonObject data = readJson(request);

                AuthService.User user = authService.authenticate(
                        getString(data, "email"),
                        getString(data, "password")
                );

                if (user == null) {
                    sendError(response, 401,
                            "Incorrect email or password.");
                    return;
                }

                HttpSession oldSession = request.getSession(false);
                if (oldSession != null) {
                    oldSession.invalidate();
                }

                HttpSession session = request.getSession(true);
                request.changeSessionId();

                session.setAttribute("userId", user.userId);
                session.setAttribute("name", user.name);
                session.setAttribute("email", user.email);
                session.setAttribute("role", user.role);

                send(response, 200, Map.of(
                        "message", "Login successful.",
                        "name", user.name,
                        "email", user.email,
                        "role", user.role
                ));
                return;
            }

            // Logout
            if (path.equals("/logout")) {
                HttpSession session = request.getSession(false);

                if (session != null) {
                    session.invalidate();
                }

                send(response, 200,
                        Map.of("message", "Logged out successfully."));
                return;
            }

            if (!requireLogin(request, response)) return;

            // Only Admin can create additional accounts.
            if (path.equals("/users")) {
                if (!requireRole(request, response, "ADMIN")) return;

                JsonObject data = readJson(request);

                authService.createUser(
                        getString(data, "name"),
                        getString(data, "email"),
                        getString(data, "password"),
                        getString(data, "role")
                );

                send(response, 201,
                        Map.of("message", "User account created successfully."));
                return;
            }

            switch (path) {

                case "/donations": {
                    if (!requireRole(request, response,
                            "ADMIN", "DONATION_MANAGER")) return;

                    JsonObject data = readJson(request);

                    DonationService.create(
                            getString(data, "name"),
                            getString(data, "phone"),
                            getString(data, "email"),
                            getString(data, "address"),
                            1L,
                            getString(data, "donation_date"),
                            getString(data, "donation_type"),
                            getDecimal(data, "amount"),
                            getString(data, "description")
                    );

                    send(response, 201, Map.of(
                            "message", "Donation created successfully."
                    ));
                    break;
                }

                case "/donors": {
                    if (!requireRole(request, response,
                            "ADMIN", "DONATION_MANAGER")) return;

                    JsonObject data = readJson(request);

                    donorService.create(
                            getString(data, "name"),
                            getString(data, "phone"),
                            getString(data, "email"),
                            getString(data, "address")
                    );

                    send(response, 201, Map.of(
                            "message", "Donor created successfully."
                    ));
                    break;
                }

                case "/beneficiaries": {
                    if (!requireRole(request, response,
                            "ADMIN", "DONATION_MANAGER")) return;

                    JsonObject data = readJson(request);

                    beneficiaryService.create(
                            getString(data, "name"),
                            getString(data, "category"),
                            getString(data, "contact"),
                            getString(data, "address")
                    );

                    send(response, 201, Map.of(
                            "message", "Beneficiary created successfully."
                    ));
                    break;
                }

                case "/allocations": {
                    if (!requireRole(request, response,
                            "ADMIN", "ALLOCATION_MANAGER")) return;

                    JsonObject data = readJson(request);

                    allocationService.create(
                            getInt(data, "donation_id"),
                            getInt(data, "beneficiary_id"),
                            1,
                            getDecimal(data, "amount_allocated"),
                            getString(data, "purpose")
                    );

                    send(response, 201, Map.of(
                            "message", "Allocation created successfully."
                    ));
                    break;
                }

                case "/allocation-status": {
                    if (!requireRole(request, response,
                            "ADMIN", "ALLOCATION_MANAGER")) return;

                    JsonObject data = readJson(request);

                    allocationService.updateStatus(
                            getInt(data, "allocation_id"),
                            getString(data, "status")
                    );

                    send(response, 200, Map.of(
                            "message", "Allocation status updated successfully."
                    ));
                    break;
                }

                default:
                    sendError(response, 404, "API endpoint not found.");
            }

        } catch (Exception e) {
            handleError(response, e);
        }
    }

    private boolean hasAccounts() throws SQLException {
        String sql = "SELECT COUNT(*) FROM app_user";

        try (Connection con = DatabaseConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private boolean requireLogin(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        HttpSession session = request.getSession(false);

        if (session == null || session.getAttribute("userId") == null) {
            sendError(response, 401, "Please log in first.");
            return false;
        }

        return true;
    }

    private boolean requireRole(
            HttpServletRequest request,
            HttpServletResponse response,
            String... allowedRoles) throws IOException {

        HttpSession session = request.getSession(false);

        if (session == null) {
            sendError(response, 401, "Please log in first.");
            return false;
        }

        Object roleObject = session.getAttribute("role");

        if (roleObject == null ||
                !Set.of(allowedRoles).contains(roleObject.toString())) {

            sendError(response, 403,
                    "You do not have permission to perform this action.");
            return false;
        }

        return true;
    }

    private void prepareResponse(HttpServletResponse response) {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
    }

    private JsonObject readJson(HttpServletRequest request)
            throws IOException {

        StringBuilder json = new StringBuilder();

        try (BufferedReader reader = request.getReader()) {
            String line;

            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
        }

        if (json.toString().isBlank()) {
            throw new IllegalArgumentException("Request body is empty.");
        }

        return JsonParser.parseString(json.toString()).getAsJsonObject();
    }

    private String getString(JsonObject data, String key) {
        if (data == null || !data.has(key) || data.get(key).isJsonNull()) {
            return null;
        }

        return data.get(key).getAsString();
    }

    private BigDecimal getDecimal(JsonObject data, String key) {
        String value = getString(data, key);

        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        return new BigDecimal(value);
    }

    private int getInt(JsonObject data, String key) {
        if (data == null || !data.has(key) || data.get(key).isJsonNull()) {
            throw new IllegalArgumentException(key + " is required.");
        }

        return data.get(key).getAsInt();
    }

    private void send(
            HttpServletResponse response,
            int status,
            Object result) throws IOException {

        response.setStatus(status);
        response.getWriter().write(gson.toJson(result));
    }

    private void sendError(
            HttpServletResponse response,
            int status,
            String message) throws IOException {

        response.setStatus(status);
        response.getWriter().write(
                gson.toJson(Map.of("error", message))
        );
    }

    private void handleError(
            HttpServletResponse response,
            Exception e) throws IOException {

        e.printStackTrace();

        if (e instanceof IllegalArgumentException) {
            sendError(response, 400, e.getMessage());
        } else if (e instanceof IllegalStateException) {
            sendError(response, 409, e.getMessage());
        } else if (e instanceof SQLException) {
            sendError(response, 500,
                    "A database operation failed. Check the server log.");
        } else {
            sendError(response, 500,
                    "An unexpected server error occurred.");
        }
    }
}
