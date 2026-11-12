
const API = `${window.location.pathname.replace(/\/index\.html$/, "").replace(/\/$/, "")}/api`;

const $ = id => document.getElementById(id);

let dashboardData = {
    donations: [],
    allocations: [],
    beneficiaries: []
};

let currentUser = null;

const permissions = {
    ADMIN: ["dashboard", "donations", "beneficiaries", "allocations", "reports"],
    DONATION_MANAGER: ["dashboard", "donations", "beneficiaries", "reports"],
    ALLOCATION_MANAGER: ["dashboard", "allocations", "reports"],
    VIEWER: ["dashboard", "donations", "beneficiaries", "allocations", "reports"]
};

const canCreateDonation = () =>
    ["ADMIN", "DONATION_MANAGER"].includes(currentUser?.role);

const canManageAllocation = () =>
    ["ADMIN", "ALLOCATION_MANAGER"].includes(currentUser?.role);

// MONEY FORMAT
function money(value) {
    return "₹" + Number(value || 0).toLocaleString("en-IN", {
        minimumFractionDigits: 2,
        maximumFractionDigits: 2
    });
}

// HTML SECURITY
function escapeHTML(value) {
    return String(value ?? "").replace(/[&<>"']/g, character => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[character]);
}

// TOAST
function showToast(message, type = "success") {
    const toast = $("toast");
    if (!toast) return;

    toast.textContent = message;
    toast.className = `toast show ${type}`;

    clearTimeout(showToast.timer);

    showToast.timer = setTimeout(() => {
        toast.className = "toast";
    }, 3500);
}

// API REQUEST
async function apiRequest(endpoint, options = {}) {
    const response = await fetch(`${API}${endpoint}`, {
        ...options,
        credentials: "same-origin",
        headers: {
            ...(options.body ? { "Content-Type": "application/json" } : {}),
            ...(options.headers || {})
        }
    });

    const text = await response.text();
    let data = {};

    try {
        data = text ? JSON.parse(text) : {};
    } catch {
        throw new Error("Server returned an invalid response.");
    }

    if (response.status === 401 && endpoint !== "/login") {
        window.location.href = API.replace(/\/api$/, "") + "/login.html";
        throw new Error("Your session has expired. Please log in again.");
    }

    if (!response.ok) {
        throw new Error(data.error || data.message || `Request failed (${response.status})`);
    }

    return data;
}

// USER DETAILS AND ROLE ACCESS
function applyRolePermissions() {
    const allowed = permissions[currentUser?.role] || [];

    document.querySelectorAll(".nav-link").forEach(button => {
        const permitted = allowed.includes(button.dataset.page);
        button.hidden = !permitted;
    });

    document.querySelectorAll("[data-go]").forEach(button => {
        button.hidden = !allowed.includes(button.dataset.go);
    });

    const donationForm = $("donation-form");
    const beneficiaryForm = $("beneficiary-form");
    const allocationForm = $("allocation-form");

    if (donationForm) donationForm.hidden = !canCreateDonation();
    if (beneficiaryForm) beneficiaryForm.hidden = !canCreateDonation();
    if (allocationForm) allocationForm.hidden = !canManageAllocation();

    document.querySelectorAll(".status-select").forEach(select => {
        select.disabled = !canManageAllocation();
    });

    const name = $("logged-user-name");
    const role = $("logged-user-role");
    const avatar = $("logged-user-avatar");

    if (name) name.textContent = currentUser.name;
    if (role) role.textContent = currentUser.role.replaceAll("_", " ");
    if (avatar) avatar.textContent = currentUser.name.charAt(0).toUpperCase();

    const firstAllowed = allowed.includes("dashboard")
        ? "dashboard"
        : allowed[0];

    showPage(firstAllowed || "dashboard");
}

// NAVIGATION
function showPage(pageName) {
    const allowed = permissions[currentUser?.role] || [];

    if (!allowed.includes(pageName)) {
        showToast("You do not have permission to access this page.", "error");
        return;
    }

    document.querySelectorAll(".page").forEach(page => {
        page.classList.toggle("active", page.id === pageName);
    });

    document.querySelectorAll(".nav-link").forEach(button => {
        button.classList.toggle("active", button.dataset.page === pageName);
    });

    const titles = {
        dashboard: "Dashboard",
        donations: "Donations",
        beneficiaries: "Beneficiaries",
        allocations: "Allocations",
        reports: "Reports"
    };

    if ($("page-title")) $("page-title").textContent = titles[pageName] || "Dashboard";

    if (pageName === "dashboard") loadDashboard();
    if (pageName === "donations") loadDonations();
    if (pageName === "beneficiaries") loadBeneficiaries();
    if (pageName === "allocations") loadAllocations();
    if (pageName === "reports") loadReports();
}

document.querySelectorAll(".nav-link").forEach(button => {
    button.addEventListener("click", () => showPage(button.dataset.page));
});

document.querySelectorAll("[data-go]").forEach(button => {
    button.addEventListener("click", () => showPage(button.dataset.go));
});

// DASHBOARD
async function loadDashboard() {
    try {
        const [donations, allocations, beneficiaries] = await Promise.all([
            apiRequest("/donations"),
            apiRequest("/allocations"),
            apiRequest("/beneficiaries")
        ]);

        dashboardData = { donations, allocations, beneficiaries };

        const totalDonations = donations.reduce(
            (sum, item) => sum + Number(item.amount || 0), 0
        );

        const totalAllocated = allocations
            .filter(item => item.status !== "CANCELLED")
            .reduce((sum, item) => sum + Number(item.amount_allocated || 0), 0);

        if ($("total-donations")) $("total-donations").textContent = money(totalDonations);
        if ($("total-allocated")) $("total-allocated").textContent = money(totalAllocated);
        if ($("remaining-balance")) $("remaining-balance").textContent = money(totalDonations - totalAllocated);
        if ($("beneficiary-count")) $("beneficiary-count").textContent = beneficiaries.length;

        const recent = [...donations].slice(0, 5);

        if ($("recent-donations")) {
            $("recent-donations").innerHTML = recent.length
                ? recent.map(donation => `
                    <tr>
                        <td>#${escapeHTML(donation.donation_id)}</td>
                        <td>${escapeHTML(donation.donor_name || "-")}</td>
                        <td>${escapeHTML(donation.donation_date || "-")}</td>
                        <td>${escapeHTML(donation.donation_type || "-")}</td>
                        <td>${money(donation.amount)}</td>
                    </tr>
                `).join("")
                : `<tr><td colspan="5">No donations recorded yet.</td></tr>`;
        }
    } catch (error) {
        showToast(error.message, "error");
    }
}

// DONATIONS
async function loadDonations() {
    try {
        const donations = await apiRequest("/donations");

        $("donation-table").innerHTML = donations.length
            ? donations.map(donation => `
                <tr>
                    <td>#${escapeHTML(donation.donation_id)}</td>
                    <td>${escapeHTML(donation.donor_name || "-")}</td>
                    <td>${escapeHTML(donation.donation_date || "-")}</td>
                    <td>${escapeHTML(donation.donation_type || "-")}</td>
                    <td>${money(donation.amount)}</td>
                    <td>${escapeHTML(donation.description || "-")}</td>
                </tr>
            `).join("")
            : `<tr><td colspan="6">No donation records found.</td></tr>`;
    } catch (error) {
        showToast(error.message, "error");
    }
}

// ADD DONATION
$("donation-form").addEventListener("submit", async event => {
    event.preventDefault();

    if (!canCreateDonation()) {
        showToast("You cannot create donations.", "error");
        return;
    }

    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form).entries());

    const payload = {
        name: (values.donorName || "").trim(),
        phone: (values.phone || "").trim(),
        email: (values.email || "").trim(),
        address: (values.address || "").trim(),
        donation_date: values.donationDate,
        donation_type: values.donationType,
        amount: Number(values.amount),
        description: (values.description || "").trim()
    };

    if (!payload.name || !payload.donation_date || !payload.donation_type ||
        !Number.isFinite(payload.amount) || payload.amount <= 0) {
        showToast("Please enter valid donation details.", "error");
        return;
    }

    try {
        await apiRequest("/donations", {
            method: "POST",
            body: JSON.stringify(payload)
        });

        form.reset();
        showToast("Donation saved successfully!");
        await loadDonations();
        await loadDashboard();
    } catch (error) {
        showToast(error.message, "error");
    }
});

// BENEFICIARIES
async function loadBeneficiaries() {
    try {
        const beneficiaries = await apiRequest("/beneficiaries");

        $("beneficiary-table").innerHTML = beneficiaries.length
            ? beneficiaries.map(item => `
                <tr>
                    <td>#${escapeHTML(item.beneficiary_id)}</td>
                    <td>${escapeHTML(item.name)}</td>
                    <td>${escapeHTML(item.category)}</td>
                    <td>${escapeHTML(item.contact || "-")}</td>
                    <td>${escapeHTML(item.address || "-")}</td>
                </tr>
            `).join("")
            : `<tr><td colspan="5">No beneficiaries registered.</td></tr>`;
    } catch (error) {
        showToast(error.message, "error");
    }
}

// ADD BENEFICIARY
$("beneficiary-form").addEventListener("submit", async event => {
    event.preventDefault();

    if (!canCreateDonation()) {
        showToast("You cannot register beneficiaries.", "error");
        return;
    }

    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form).entries());

    const payload = {
        name: (values.name || "").trim(),
        category: values.category,
        contact: (values.contact || "").trim(),
        address: (values.address || "").trim()
    };

    if (!payload.name || !payload.category) {
        showToast("Please enter beneficiary name and category.", "error");
        return;
    }

    try {
        await apiRequest("/beneficiaries", {
            method: "POST",
            body: JSON.stringify(payload)
        });

        form.reset();
        showToast("Beneficiary registered successfully!");
        await loadBeneficiaries();
        await loadDashboard();
    } catch (error) {
        showToast(error.message, "error");
    }
}

// ALLOCATIONS
async function loadAllocations() {
    try {
        const allocations = await apiRequest("/allocations");

        $("allocation-table").innerHTML = allocations.length
            ? allocations.map(item => `
                <tr>
                    <td>#${escapeHTML(item.allocation_id)}</td>
                    <td>#${escapeHTML(item.donation_id)}</td>
                    <td>${escapeHTML(item.beneficiary_name || "-")}</td>
                    <td>${money(item.amount_allocated)}</td>
                    <td>${escapeHTML(item.purpose || "-")}</td>
                    <td>${escapeHTML(item.allocation_date || "-")}</td>
                    <td>
                        <select class="status-select"
                                data-allocation-id="${escapeHTML(item.allocation_id)}"
                                ${canManageAllocation() ? "" : "disabled"}>
                            ${["PENDING", "ALLOCATED", "FULFILLED", "CANCELLED"].map(status => `
                                <option value="${status}" ${item.status === status ? "selected" : ""}>
                                    ${status}
                                </option>
                            `).join("")}
                        </select>
                    </td>
                </tr>
            `).join("")
            : `<tr><td colspan="7">No allocations recorded.</td></tr>`;
    } catch (error) {
        showToast(error.message, "error");
    }
}

// ADD ALLOCATION
$("allocation-form").addEventListener("submit", async event => {
    event.preventDefault();

    if (!canManageAllocation()) {
        showToast("You cannot create allocations.", "error");
        return;
    }

    const form = event.currentTarget;
    const values = Object.fromEntries(new FormData(form).entries());

    const payload = {
        donation_id: Number(values.donationId),
        beneficiary_id: Number(values.beneficiaryId),
        amount_allocated: Number(values.amount),
        purpose: (values.purpose || "").trim()
    };

    if (!Number.isInteger(payload.donation_id) || payload.donation_id <= 0 ||
        !Number.isInteger(payload.beneficiary_id) || payload.beneficiary_id <= 0 ||
        !Number.isFinite(payload.amount_allocated) || payload.amount_allocated <= 0 ||
        !payload.purpose) {
        showToast("Please enter valid allocation details.", "error");
        return;
    }

    try {
        await apiRequest("/allocations", {
            method: "POST",
            body: JSON.stringify(payload)
        });

        form.reset();
        showToast("Donation allocated successfully!");
        await loadAllocations();
        await loadDashboard();
        await loadReports();
    } catch (error) {
        showToast(error.message, "error");
    }
});

// UPDATE ALLOCATION STATUS
$("allocation-table").addEventListener("change", async event => {
    const select = event.target.closest(".status-select");
    if (!select) return;

    if (!canManageAllocation()) {
        showToast("You cannot update allocation status.", "error");
        await loadAllocations();
        return;
    }

    try {
        await apiRequest("/allocation-status", {
            method: "POST",
            body: JSON.stringify({
                allocation_id: Number(select.dataset.allocationId),
                status: select.value
            })
        });

        showToast("Allocation status updated.");
        await loadAllocations();
        await loadDashboard();
        await loadReports();
    } catch (error) {
        showToast(error.message, "error");
        await loadAllocations();
    }
});

// REPORTS
async function loadReports() {
    try {
        const [donations, allocations] = await Promise.all([
            apiRequest("/donations"),
            apiRequest("/allocations")
        ]);

        $("report-table").innerHTML = donations.length
            ? donations.map(donation => {
                const allocated = allocations
                    .filter(item =>
                        Number(item.donation_id) === Number(donation.donation_id) &&
                        item.status !== "CANCELLED"
                    )
                    .reduce((sum, item) => sum + Number(item.amount_allocated || 0), 0);

                const amount = Number(donation.amount || 0);

                return `
                    <tr>
                        <td>#${escapeHTML(donation.donation_id)}</td>
                        <td>${escapeHTML(donation.donor_name || "-")}</td>
                        <td>${money(amount)}</td>
                        <td>${money(allocated)}</td>
                        <td><strong>${money(amount - allocated)}</strong></td>
                    </tr>
                `;
            }).join("")
            : `<tr><td colspan="5">No report data available.</td></tr>`;
    } catch (error) {
        showToast(error.message, "error");
    }
}

$("refresh-reports").addEventListener("click", loadReports);

// LOGOUT
async function logout() {
    try {
        await apiRequest("/logout", { method: "POST" });
    } catch (error) {
        console.error("Logout:", error);
    }

    window.location.href = API.replace(/\/api$/, "") + "/login.html";
}

// INITIALIZE APPLICATION
document.addEventListener("DOMContentLoaded", async () => {
    try {
        currentUser = await apiRequest("/me");

        applyRolePermissions();

        const today = new Date();
        const localDate = new Date(
            today.getTime() - today.getTimezoneOffset() * 60000
        ).toISOString().slice(0, 10);

        document.querySelectorAll(
            'input[name="donationDate"], input[name="allocationDate"]'
        ).forEach(input => {
            input.value = localDate;
        });

        const logoutButton = $("logout-button");
        if (logoutButton) logoutButton.addEventListener("click", logout);

    } catch (error) {
        console.error(error);
        if (!window.location.pathname.endsWith("/login.html")) {
            window.location.href = API.replace(/\/api$/, "") + "/login.html";
        }
    }
});
