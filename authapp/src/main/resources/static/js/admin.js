const API_BASE = window.location.origin;
const TOKEN_KEY = "adminAccessToken"; // Updated to match login.html storage

// State
let currentPage = 0;
let currentView = 'dashboard';

// --- INIT ---
document.addEventListener("DOMContentLoaded", () => {
    checkAuth();
    loadDashboard(); // Default view
});

function checkAuth() {
    // Check localStorage (persists across tabs)
    const token = localStorage.getItem(TOKEN_KEY);

    if (!token) {
        window.location.href = "login.html";
        return;
    }

    // Display admin name if stored
    try {
        const storedUser = localStorage.getItem("user");
        if (storedUser) {
            const adminUser = JSON.parse(storedUser);
            if (adminUser.name) {
                document.getElementById("adminUsername").textContent = adminUser.name;
            }
        }
    } catch (e) {
        console.warn("Failed to parse user", e);
    }
}

function handleLogout() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem("user");
    window.location.href = "login.html";
}

function getAuthHeaders() {
    const token = localStorage.getItem(TOKEN_KEY);
    return {
        "Authorization": "Bearer " + token,
        "Content-Type": "application/json"
    };
}

// --- NAVIGATION ---
function switchView(view, el) {
    // Update Active Link
    document.querySelectorAll('.nav-link').forEach(link => link.classList.remove('active'));
    if (el) el.classList.add('active');

    currentView = view;

    if (view === 'dashboard') {
        loadDashboard();
    } else if (view === 'users') {
        loadUsers();
    } else if (view === 'audit') {
        loadAuditLogs();
    }
}

// --- IMAGE STORAGE VIEW ---
async function loadImages() {
    document.getElementById("pageTitle").textContent = "Image Storage";
    const content = document.getElementById("contentArea");
    content.innerHTML = '<div class="loading">Loading image stats...</div>';

    try {
        const res = await fetch(`${API_BASE}/api/admin/storage/image/summary`, {
            headers: getAuthHeaders()
        });
        const data = await res.json();

        // Helper
        const formatBytes = (bytes) => {
            if (!bytes) return "0 B";
            const k = 1024;
            const sizes = ["B", "KB", "MB", "GB", "TB"];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
        };

        content.innerHTML = `
            <div class="stats-grid">
                <div class="stat-card">
                    <div class="stat-label">Total Images</div>
                    <div class="stat-value">${data.totalFiles || 0}</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">Total Size</div>
                    <div class="stat-value" style="color: #60a5fa">${formatBytes(data.totalBytes || 0)}</div>
                </div>
            </div>
            <div style="margin-top: 30px; padding: 20px; background: #1e293b; border-radius: 8px;">
                <h3 style="margin-bottom: 10px;">Note</h3>
                <p style="color: #ccc;">Detailed file listing is not currently available via the Admin API.</p>
            </div>
        `;
    } catch (e) {
        content.innerHTML = `<div class="error-message">Failed to load image stats</div>`;
    }
}

// --- VIDEO STORAGE VIEW ---
async function loadVideos() {
    document.getElementById("pageTitle").textContent = "Video Storage";
    const content = document.getElementById("contentArea");
    content.innerHTML = '<div class="loading">Loading video stats...</div>';

    try {
        const res = await fetch(`${API_BASE}/api/admin/storage/video/summary`, {
            headers: getAuthHeaders()
        });
        const data = await res.json();

        // Helper
        const formatBytes = (bytes) => {
            if (!bytes) return "0 B";
            const k = 1024;
            const sizes = ["B", "KB", "MB", "GB", "TB"];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
        };

        content.innerHTML = `
            <div class="stats-grid">
                <div class="stat-card">
                    <div class="stat-label">Total Videos</div>
                    <div class="stat-value">${data.totalFiles || 0}</div>
                </div>
                <div class="stat-card">
                    <div class="stat-label">Total Size</div>
                    <div class="stat-value" style="color: #60a5fa">${formatBytes(data.totalBytes || 0)}</div>
                </div>
            </div>
             <div style="margin-top: 30px; padding: 20px; background: #1e293b; border-radius: 8px;">
                <h3 style="margin-bottom: 10px;">Note</h3>
                <p style="color: #ccc;">Detailed file listing is not currently available via the Admin API.</p>
            </div>
        `;
    } catch (e) {
        content.innerHTML = `<div class="error-message">Failed to load video stats</div>`;
    }
}

// --- DASHBOARD VIEW ---
async function loadDashboard() {
    document.getElementById("pageTitle").textContent = "Overview";
    const content = document.getElementById("contentArea");
    content.innerHTML = '<div class="loading">Loading stats...</div>';

    try {
        // Use FULL dashboard endpoint for Overview + Storage stats
        const res = await fetch(`${API_BASE}/api/admin/dashboard/full`, {
            headers: getAuthHeaders()
        });

        if (res.status === 401) {
            handleLogout();
            return;
        }

        const stats = await res.json();
        renderDashboard(stats);

    } catch (err) {
        console.error("Dashboard load failed", err);
        content.innerHTML = `<div class="error-message">Failed to load dashboard: ${err.message}</div>`;
    }
}

function renderDashboard(stats) {
    const content = document.getElementById("contentArea");

    // Helper for bytes
    const formatBytes = (bytes) => {
        if (!bytes) return "0 B";
        const k = 1024;
        const sizes = ["B", "KB", "MB", "GB", "TB"];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
    };

    content.innerHTML = `
        <h3 style="margin-bottom: 15px; color: var(--text-secondary);">User Statistics</h3>
        <div class="stats-grid" style="grid-template-columns: repeat(3, 1fr);">
            <div class="stat-card">
                <div class="stat-label">Total Users</div>
                <div class="stat-value">${stats.totalUsers || 0}</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Active</div>
                <div class="stat-value" style="color: var(--success)">${stats.activeUsers || 0}</div>
            </div>
             <div class="stat-card">
                <div class="stat-label">Blocked</div>
                <div class="stat-value" style="color: var(--danger)">${stats.blockedUsers || 0}</div>
            </div>
        </div>

        <h3 style="margin: 30px 0 15px 0; color: var(--text-secondary);">Storage Overview</h3>
        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-label">Total Storage Used</div>
                <div class="stat-value" style="color: #60a5fa">${formatBytes(stats.totalStorageBytes || 0)}</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Video Files</div>
                <div class="stat-value">${stats.videoTotalFiles || 0}</div>
                <div style="font-size: 13px; color: #888; margin-top: 5px;">${formatBytes(stats.videoTotalBytes || 0)}</div>
            </div>
            <div class="stat-card">
                <div class="stat-label">Image Files</div>
                <div class="stat-value">${stats.imageTotalFiles || 0}</div>
                <div style="font-size: 13px; color: #888; margin-top: 5px;">${formatBytes(stats.imageTotalBytes || 0)}</div>
            </div>
        </div>
        
        <div class="stat-card" style="margin-top: 20px;">
            <h3>Recent System Activity</h3>
            <p style="color: #666; font-size: 14px; margin-top: 10px;">(Check Audit Logs for details)</p>
        </div>
    `;
}

// --- USERS VIEW ---
async function loadUsers(page = 0) {
    document.getElementById("pageTitle").textContent = "User Management";
    const content = document.getElementById("contentArea");
    content.innerHTML = '<div class="loading">Loading users...</div>';

    try {
        // Fetch users using the correct endpoint and pagination
        const res = await fetch(`${API_BASE}/api/admin/users?page=${page}&size=20`, {
            headers: getAuthHeaders()
        });

        if (!res.ok) throw new Error("Failed to fetch users");

        const data = await res.json();
        renderUserTable(data.content, data); // List and Page info

    } catch (err) {
        content.innerHTML = `<div class="error-message">${err.message}</div>`;
    }
}

function renderUserTable(users, pageInfo) {
    const content = document.getElementById("contentArea");

    // Re-declare helper here or move to global scope. Using local for safety.
    const formatBytes = (bytes) => {
        if (!bytes) return "0 B";
        const k = 1024;
        const sizes = ["B", "KB", "MB", "GB", "TB"];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
    };

    let rows = "";
    if (users.length === 0) {
        rows = "<tr><td colspan='8' style='text-align:center'>No users found</td></tr>";
    } else {
        rows = users.map(user => {
            const statusLower = (user.status || '').toLowerCase();
            let statusClass = 'status-inactive';
            if (statusLower === 'active') statusClass = 'status-active';
            else if (statusLower === 'blocked' || statusLower === 'banned') statusClass = 'status-banned';

            return `
            <tr>
                <td>${user.userId}</td>
                <td>
                    <div style="font-weight:600">
                        ${user.name}
                      
                    </div>
                    <div style="font-size:12px;color:#888">${user.email}   ${user.emailVerified ?
                    '<ion-icon name="checkmark-circle" style="color:var(--success); vertical-align:middle; margin-left:5px;" title="Email Verified"></ion-icon>' :
                    '<ion-icon name="close-circle" style="color:var(--danger); vertical-align:middle; margin-left:5px;" title="Email Not Verified"></ion-icon>'
                }</div>
                </td>
                <td>
                    ${user.phone || '-'}
                    ${user.phone ? (user.phoneVerified ?
                    '<ion-icon name="checkmark-circle" style="color:var(--success); vertical-align:middle; margin-left:5px;" title="Phone Verified"></ion-icon>' :
                    '<ion-icon name="close-circle" style="color:var(--danger); vertical-align:middle; margin-left:5px;" title="Phone Not Verified"></ion-icon>'
                ) : ''}
                </td>
                <td><span class="status-badge ${statusClass}">${user.status || 'UNKNOWN'}</span></td>
                 <td>
                    <div style="font-weight:500">${user.imageTotalFiles || 0} Files</div>
                    <div style="font-size:11px;color:#888">${formatBytes(user.imageTotalBytes || 0)}</div>
                </td>
                <td>
                    <div style="font-weight:500">${user.videoTotalFiles || 0} Files</div>
                    <div style="font-size:11px;color:#888">${formatBytes(user.videoTotalBytes || 0)}</div>
                </td>
                <td>${new Date(user.createdTimestamp).toLocaleDateString()}</td>
                <td>
                    <button class="action-btn btn-secondary" onclick="viewUser(${user.userId})" style="background:#3b82f6"><ion-icon name="eye-outline"></ion-icon></button>
                    ${statusLower === 'active' ?
                    `<button class="action-btn btn-danger" onclick="blockUser(${user.userId})">Block</button>` :
                    (statusLower === 'blocked' || statusLower === 'banned') ?
                        `<button class="action-btn btn-secondary" style="background:#10b981" onclick="unblockUser(${user.userId})">Unblock</button>` :
                        `<button class="action-btn btn-secondary" style="background:#10b981" onclick="unblockUser(${user.userId})">Activate</button>`
                }
                </td>
            </tr>
            `;
        }).join("");
    }

    content.innerHTML = `
        <div style="margin-bottom: 20px; display: flex; justify-content: space-between;">
            <input type="text" placeholder="Search user by name/email..." 
                   style="padding: 10px; border-radius: 6px; border: 1px solid #333; background: #1e293b; color: white; width: 300px;"
                   onkeyup="if(event.key === 'Enter') searchUsers(this.value)">
        </div>

        <div class="table-container">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>User</th>
                        <th>Phone</th>
                        <th>Status</th>
                        <th>Image Storage</th>
                        <th>Video Storage</th>
                        <th>Joined</th>
                        <th>Actions</th>
                    </tr>
                </thead>
                <tbody>
                    ${rows}
                </tbody>
            </table>
        </div>
        
        <div style="margin-top: 20px; display: flex; justify-content: center; gap: 10px;">
             <button class="action-btn btn-secondary" ${pageInfo.first ? 'disabled' : ''} onclick="loadUsers(${pageInfo.number - 1})">Previous</button>
             <span style="align-self:center; color:#888">Page ${pageInfo.number + 1} of ${pageInfo.totalPages}</span>
             <button class="action-btn btn-secondary" ${pageInfo.last ? 'disabled' : ''} onclick="loadUsers(${pageInfo.number + 1})">Next</button>
        </div>
    `;
}

async function searchUsers(query) {
    if (!query) {
        loadUsers(0);
        return;
    }

    // Call Search API
    try {
        const res = await fetch(`${API_BASE}/api/admin/users/search?query=${encodeURIComponent(query)}`, {
            headers: getAuthHeaders()
        });
        const data = await res.json();
        renderUserTable(data.content, data);
    } catch (e) {
        console.error(e);
        showToast("Search failed", "error");
    }
}

/* --- TOAST NOTIFICATIONS --- */
function showToast(message, type = 'success') {
    let container = document.getElementById('toastContainer');
    if (!container) {
        container = document.createElement('div');
        container.id = 'toastContainer';
        container.className = 'toast-container';
        document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    let iconName = 'checkmark-circle-outline';
    if (type === 'error') iconName = 'alert-circle-outline';
    if (type === 'warning') iconName = 'warning-outline';

    toast.innerHTML = `<ion-icon name="${iconName}"></ion-icon> <span>${message}</span>`;

    container.appendChild(toast);

    // Trigger animation
    setTimeout(() => toast.classList.add('show'), 10);

    // Remove after 3 seconds
    setTimeout(() => {
        toast.classList.remove('show');
        setTimeout(() => toast.remove(), 400);
    }, 3000);
}




async function viewUser(userId) {
    const modal = document.getElementById("userModal");
    const content = document.getElementById("modalUserContent");

    modal.classList.remove("hidden");
    content.innerHTML = '<div class="loading">Loading details...</div>';

    try {
        const res = await fetch(`${API_BASE}/api/admin/users/${userId}/full-details`, {
            headers: getAuthHeaders()
        });

        if (!res.ok) throw new Error("Failed to fetch details");

        const data = await res.json();

        // Helper
        const formatBytes = (bytes) => {
            if (!bytes) return "0 B";
            const k = 1024;
            const sizes = ["B", "KB", "MB", "GB", "TB"];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + " " + sizes[i];
        };

        const renderRow = (label, value) => `
            <div class="detail-row">
                <span class="detail-label">${label}</span>
                <span class="detail-value">${value}</span>
            </div>
        `;

        const verificationIcon = (isVerified) => isVerified ?
            '<ion-icon name="checkmark-circle" style="color:var(--success); vertical-align: middle; margin-right: 4px;"></ion-icon> Verified' :
            '<ion-icon name="close-circle" style="color:var(--danger); vertical-align: middle; margin-right: 4px;"></ion-icon> Unverified';

        content.innerHTML = `
            <div style="background: #1e293b; padding: 20px; border-radius: 12px; margin-bottom: 20px; display: flex; align-items: center; gap: 20px;">
                <div style="width: 70px; height: 70px; border-radius: 50%; background: #3b82f6; display: flex; align-items: center; justify-content: center; font-size: 28px; font-weight: bold; color: white;">
                    ${data.name.substring(0, 2).toUpperCase()}
                </div>
                <div>
                    <h3 style="margin: 0; font-size: 24px;">${data.name}</h3>
                    <p style="margin: 5px 0 0 0; color: #94a3b8; font-size: 14px;">User ID: #${data.userId}</p>
                    <div style="margin-top: 8px;">
                        <span class="status-badge ${data.status === 'Active' ? 'status-active' : 'status-banned'}">${data.status}</span>
                    </div>
                </div>
            </div>

            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px; background: #0f172a; padding: 20px; border-radius: 12px; border: 1px solid #1e293b;">
                ${renderRow("Email", data.email)}
                ${renderRow("Email Status", verificationIcon(data.emailVerified))}
                ${renderRow("Phone", data.phone || '-')}
                ${renderRow("Phone Status", verificationIcon(data.phoneVerified))}
                <div style="grid-column: span 2;">
                    ${renderRow("Joined Date", new Date(data.createdTimestamp).toLocaleString())}
                </div>
            </div>
            
            <h3 style="margin: 24px 0 16px 0; color: var(--primary-color); display: flex; align-items: center; gap: 8px;">
                <ion-icon name="server-outline"></ion-icon> Storage Statistics
            </h3>
            
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 15px;">
                <div style="background: #1e293b; padding: 15px; border-radius: 10px; border-left: 4px solid #3b82f6;">
                    <div style="color: #94a3b8; font-size: 12px; margin-bottom: 5px; text-transform: uppercase;">Image Storage</div>
                    <div style="font-size: 18px; font-weight: bold;">${data.imageTotalFiles} Files</div>
                    <div style="color: #64748b; font-size: 13px; margin-top: 4px;">Size: ${formatBytes(data.imageTotalBytes)}</div>
                </div>
                <div style="background: #1e293b; padding: 15px; border-radius: 10px; border-left: 4px solid #f59e0b;">
                    <div style="color: #94a3b8; font-size: 12px; margin-bottom: 5px; text-transform: uppercase;">Video Storage</div>
                    <div style="font-size: 18px; font-weight: bold;">${data.videoTotalFiles} Files</div>
                    <div style="color: #64748b; font-size: 13px; margin-top: 4px;">Size: ${formatBytes(data.videoTotalBytes)}</div>
                </div>
            </div>
        `;

    } catch (e) {
        content.innerHTML = `<div class="error-message">${e.message}</div>`;
    }
}

function closeUserModal() {
    document.getElementById("userModal").classList.add("hidden");
}

// --- RESPONSIVE SIDEBAR ---
function toggleSidebar() {
    const sidebar = document.getElementById("adminSidebar");
    const overlay = document.querySelector(".sidebar-overlay");
    if (sidebar && overlay) {
        sidebar.classList.toggle("open");
        overlay.classList.toggle("active");
    }
}

function toggleSidebarOnMobile() {
    if (window.innerWidth <= 768) {
        toggleSidebar();
    }
}



// --- CUSTOM CONFIRM MODAL ---
let confirmCallback = null;

function showCustomConfirm(message, onConfirm) {
    document.getElementById("customConfirmMessage").textContent = message;
    confirmCallback = onConfirm;
    document.getElementById("customConfirmModal").classList.remove("hidden");
}

function closeCustomConfirm() {
    document.getElementById("customConfirmModal").classList.add("hidden");
    confirmCallback = null; // Clear callback
}

document.getElementById("customConfirmOkBtn").addEventListener("click", () => {
    if (confirmCallback) confirmCallback();
    closeCustomConfirm();
});

// --- USER ACTIONS ---

function blockUser(userId) {
    document.getElementById("blockUserIdInput").value = userId;
    document.getElementById("blockReasonSelect").value = "Violation of terms"; // default
    document.getElementById("blockUserModal").classList.remove("hidden");
}

function closeBlockUserModal() {
    document.getElementById("blockUserModal").classList.add("hidden");
    document.getElementById("blockUserIdInput").value = "";
}

async function confirmBlockUser() {
    const userId = document.getElementById("blockUserIdInput").value;
    const reason = document.getElementById("blockReasonSelect").value;

    if (!userId || !reason) return;

    showCustomConfirm("Are you sure you want to block this user for: " + reason + "?", async () => {
        try {
            const res = await fetch(`${API_BASE}/api/admin/users/${userId}/block?reason=${encodeURIComponent(reason)}`, {
                method: "POST",
                headers: getAuthHeaders()
            });

            if (res.ok) {
                showToast("User blocked successfully", "success");
                closeBlockUserModal();
                loadUsers(currentPage);
            } else {
                const txt = await res.text();
                showToast("Failed to block: " + txt, "error");
            }
        } catch (e) {
            showToast("Error: " + e.message, "error");
        }
    });
}

async function unblockUser(userId) {
    showCustomConfirm("Are you sure you want to unblock this user?", async () => {
        try {
            const res = await fetch(`${API_BASE}/api/admin/users/${userId}/unblock`, {
                method: "POST",
                headers: getAuthHeaders()
            });

            if (res.ok) {
                showToast("User unblocked successfully", "success");
                loadUsers(currentPage);
            } else {
                const txt = await res.text();
                showToast("Failed to unblock: " + txt, "error");
            }
        } catch (e) {
            showToast("Error: " + e.message, "error");
        }
    });
}

async function makeInactiveUser(userId) {
    showCustomConfirm("Are you sure you want to make this user inactive?", async () => {
        try {
            const res = await fetch(`${API_BASE}/api/admin/users/${userId}/inactive`, {
                method: "POST",
                headers: getAuthHeaders()
            });

            if (res.ok) {
                showToast("User marked inactive successfully", "success");
                loadUsers(currentPage);
            } else {
                const txt = await res.text();
                showToast("Failed to mark inactive: " + txt, "error");
            }
        } catch (e) {
            showToast("Error: " + e.message, "error");
        }
    });
}

// --- AUDIT LOGS ---
let allAuditLogs = [];

async function loadAuditLogs() {
    document.getElementById("pageTitle").textContent = "System Audit Logs";
    const content = document.getElementById("contentArea");

    // Render the layout once
    content.innerHTML = `
        <div style="margin-bottom: 20px; display: flex; justify-content: space-between;">
            <input type="text" id="auditSearchInput" placeholder="Search logs by action, reason, user ID..." 
                   style="padding: 10px; border-radius: 6px; border: 1px solid #333; background: #1e293b; color: white; width: 400px;"
                   onkeyup="searchAuditLogs(this.value)">
        </div>

        <div class="table-container">
            <table class="data-table">
                <thead>
                    <tr>
                        <th>ID</th>
                        <th>Action</th>
                        <th>Target User</th>
                        <th>Details</th>
                        <th>Timestamp</th>
                    </tr>
                </thead>
                <tbody id="auditLogsTableBody">
                    <tr><td colspan='5' style='text-align:center;'>Loading logs...</td></tr>
                </tbody>
            </table>
        </div>
    `;

    try {
        const res = await fetch(`${API_BASE}/api/admin/audit-logs`, {
            headers: getAuthHeaders()
        });
        allAuditLogs = await res.json();

        renderAuditLogsTable(allAuditLogs);
    } catch (e) {
        document.getElementById("auditLogsTableBody").innerHTML = `<tr><td colspan='5' class="error-message">Failed to load logs</td></tr>`;
    }
}

function searchAuditLogs(query) {
    if (!query) {
        renderAuditLogsTable(allAuditLogs);
        return;
    }

    query = query.toLowerCase();
    const filtered = allAuditLogs.filter(log =>
        (log.action && log.action.toLowerCase().includes(query)) ||
        (log.description && log.description.toLowerCase().includes(query)) ||
        (log.targetUserId && log.targetUserId.toString().includes(query)) ||
        (log.auditId && log.auditId.toString().includes(query))
    );
    renderAuditLogsTable(filtered);
}

function renderAuditLogsTable(logs) {
    const tbody = document.getElementById("auditLogsTableBody");
    if (!tbody) return;

    if (!logs || logs.length === 0) {
        tbody.innerHTML = "<tr><td colspan='5' style='text-align:center;'>No logs found matching your search.</td></tr>";
        return;
    }

    const rows = logs.map(log => {
        let actionHtml = log.action;
        if (log.action === 'BLOCK_USER') {
            actionHtml = `<span class="status-badge status-banned" style="padding: 4px 8px; font-size: 11px;">BLOCK_USER</span>`;
        } else if (log.action === 'UNBLOCK_USER') {
            actionHtml = `<span class="status-badge status-active" style="padding: 4px 8px; font-size: 11px;">UNBLOCK_USER</span>`;
        } else if (log.action === 'INACTIVE_USER') {
            actionHtml = `<span class="status-badge status-inactive" style="padding: 4px 8px; font-size: 11px;">INACTIVE_USER</span>`;
        } else {
            actionHtml = `<span style="background: #334155; color: white; padding: 4px 8px; border-radius: 12px; font-size: 11px;">${log.action}</span>`;
        }

        let detailsHtml = log.description || '-';
        if (log.action === 'BLOCK_USER' && log.description) {
            // Emphasize reason
            detailsHtml = `<span style="color: #ef4444; font-weight: 500;">${log.description}</span>`;
        }

        return `
        <tr>
            <td>${log.auditId}</td>
            <td>${actionHtml}</td>
            <td style="font-weight: 600;">${log.targetUserId || '-'}</td>
            <td>${detailsHtml}</td>
            <td style="font-size: 12px; color: #888;">${new Date(log.createdTimestamp).toLocaleString()}</td>
        </tr>
        `;
    }).join("");

    tbody.innerHTML = rows;
}
