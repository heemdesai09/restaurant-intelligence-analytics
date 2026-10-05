/**
 * Restaurant Intelligence & Analytics System - College Frontend Application
 * Student: Heem Desai (23162121003)
 */

const API_BASE = ""; // Relative path to backend server

// ==================== INITIALIZATION & NAVIGATION ====================

document.addEventListener("DOMContentLoaded", () => {
  setupNavigation();
  checkConnection();
  loadOverview();
  // Perform initial search to show some data on startup
  searchRestaurants({});
  loadIndexes();
});

function setupNavigation() {
  const tabs = document.querySelectorAll(".nav-tab");
  tabs.forEach((tab) => {
    tab.addEventListener("click", () => {
      tabs.forEach((t) => t.classList.remove("active"));
      tab.classList.add("active");

      const targetId = tab.getAttribute("data-target");
      document.querySelectorAll(".content-section").forEach((sec) => {
        sec.classList.remove("active");
      });
      const targetSec = document.getElementById(targetId);
      if (targetSec) targetSec.classList.add("active");

      // Lazy load analytics when analytics tab is clicked
      if (targetId === "analyticsSection") {
        loadCuisinesAnalytics();
      } else if (targetId === "databaseSection") {
        loadIndexes();
      }
    });
  });
}

function showAlert(message, type = "info") {
  const box = document.getElementById("globalAlert");
  const msgSpan = document.getElementById("alertMessage");
  box.className = `alert-box ${type}`;
  msgSpan.textContent = message;
  box.classList.remove("hidden");
  // Auto scroll to top
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function hideAlert() {
  const box = document.getElementById("globalAlert");
  box.classList.add("hidden");
}

// ==================== HEALTH & OVERVIEW ====================

async function checkConnection() {
  const badge = document.getElementById("connectionBadge");
  const text = document.getElementById("connectionText");

  try {
    const res = await fetch(`${API_BASE}/api/health`);
    const data = await res.json();
    if (res.ok && data.status === "ok") {
      badge.className = "status-badge connected";
      text.textContent = "MongoDB Connected (sample_restaurants)";
    } else {
      badge.className = "status-badge error";
      text.textContent = "Connection Error";
      showAlert("Could not connect to MongoDB Atlas. Please verify MONGODB_URI.", "error");
    }
  } catch (err) {
    badge.className = "status-badge error";
    text.textContent = "Backend Offline";
  }
}

async function loadOverview() {
  try {
    const res = await fetch(`${API_BASE}/api/overview`);
    if (!res.ok) throw new Error("Failed to fetch overview metrics");
    const data = await res.json();

    document.getElementById("kpiTotalRestaurants").textContent = Number(data.totalRestaurants).toLocaleString();
    document.getElementById("kpiCuisines").textContent = Number(data.totalCuisines).toLocaleString();
    document.getElementById("kpiBoroughs").textContent = Number(data.totalBoroughs).toLocaleString();
    document.getElementById("kpiAvgScore").textContent = Number(data.avgScore).toFixed(2);
  } catch (err) {
    console.error("Overview load error:", err);
  }
}

// ==================== RESTAURANT FINDER & MULTI-FILTER ====================

function handleSearch(e) {
  if (e) e.preventDefault();
  const filter = {
    name: document.getElementById("filterName").value.trim(),
    cuisine: document.getElementById("filterCuisine").value.trim(),
    borough: document.getElementById("filterBorough").value.trim(),
    zipcode: document.getElementById("filterZipcode").value.trim(),
    minScore: document.getElementById("filterMinScore").value.trim(),
  };
  searchRestaurants(filter);
}

function resetSearch() {
  document.getElementById("searchForm").reset();
  searchRestaurants({});
}

function loadExampleQuery() {
  document.getElementById("filterCuisine").value = "Indian";
  document.getElementById("filterBorough").value = "Queens";
  document.getElementById("filterMinScore").value = "8";
  document.getElementById("filterName").value = "";
  document.getElementById("filterZipcode").value = "";
  searchRestaurants({
    cuisine: "Indian",
    borough: "Queens",
    minScore: "8",
  });
}

async function searchRestaurants(filter) {
  const tbody = document.getElementById("restaurantsTableBody");
  tbody.innerHTML = `<tr><td colspan="8" class="text-center">Querying MongoDB Atlas...</td></tr>`;

  const params = new URLSearchParams();
  if (filter.name) params.append("name", filter.name);
  if (filter.cuisine) params.append("cuisine", filter.cuisine);
  if (filter.borough) params.append("borough", filter.borough);
  if (filter.zipcode) params.append("zipcode", filter.zipcode);
  if (filter.minScore) params.append("minScore", filter.minScore);
  params.append("limit", "50");

  try {
    const res = await fetch(`${API_BASE}/api/restaurants?${params.toString()}`);
    const restaurants = await res.json();

    if (!res.ok) {
      tbody.innerHTML = `<tr><td colspan="8" class="text-center" style="color:var(--danger)">Error: ${restaurants.error || "Query failed"}</td></tr>`;
      return;
    }

    document.getElementById("resultsCount").textContent = restaurants.length;

    if (restaurants.length === 0) {
      tbody.innerHTML = `<tr><td colspan="8" class="text-center">No restaurants found matching the given criteria.</td></tr>`;
      return;
    }

    tbody.innerHTML = restaurants.map((r) => renderRestaurantRow(r)).join("");
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="8" class="text-center" style="color:var(--danger)">Network error: ${err.message}</td></tr>`;
  }
}

function renderRestaurantRow(r) {
  const latestGrade = r.grades && r.grades.length > 0 ? r.grades[0] : null;
  const gradeLetter = latestGrade ? latestGrade.grade : "-";
  const scoreVal = latestGrade && latestGrade.score !== undefined && latestGrade.score !== null ? latestGrade.score : "-";

  let gradeClass = "grade-other";
  if (gradeLetter === "A") gradeClass = "grade-a";
  else if (gradeLetter === "B") gradeClass = "grade-b";
  else if (gradeLetter === "C") gradeClass = "grade-c";

  const targetId = r.id || r.restaurantId;
  const streetAddr = `${r.address.building || ""} ${r.address.street || ""}`.trim() || "-";

  return `
    <tr>
      <td><code>${escapeHtml(r.restaurantId)}</code></td>
      <td><strong>${escapeHtml(r.name)}</strong></td>
      <td>${escapeHtml(r.cuisine)}</td>
      <td>${escapeHtml(r.borough)}</td>
      <td>${escapeHtml(streetAddr)}</td>
      <td>${escapeHtml(r.address.zipcode || "-")}</td>
      <td>
        <span class="score-badge ${gradeClass}">${escapeHtml(gradeLetter)} (${scoreVal})</span>
      </td>
      <td>
        <div class="table-actions">
          <button class="btn btn-sm btn-secondary" onclick="openEditModal('${targetId}')">Edit</button>
          <button class="btn btn-sm btn-danger" onclick="confirmDelete('${targetId}', '${escapeHtml(r.name)}')">Delete</button>
        </div>
      </td>
    </tr>
  `;
}

// ==================== ADD RESTAURANT ====================

function fillTestPreset(num) {
  if (num === 1) {
    document.getElementById("addName").value = "Heem Test Bistro 01";
    document.getElementById("addCuisine").value = "Indian";
    document.getElementById("addBorough").value = "Queens";
    document.getElementById("addBuilding").value = "72-04";
    document.getElementById("addStreet").value = "Broadway";
    document.getElementById("addZipcode").value = "11372";
    document.getElementById("addScore").value = "12";
    document.getElementById("addGrade").value = "A";
    document.getElementById("addCustomId").value = "HEEM_01";
  } else if (num === 2) {
    document.getElementById("addName").value = "Heem Test Bistro 02";
    document.getElementById("addCuisine").value = "Italian";
    document.getElementById("addBorough").value = "Manhattan";
    document.getElementById("addBuilding").value = "45";
    document.getElementById("addStreet").value = "Spring St";
    document.getElementById("addZipcode").value = "10012";
    document.getElementById("addScore").value = "14";
    document.getElementById("addGrade").value = "B";
    document.getElementById("addCustomId").value = "HEEM_02";
  }
}

async function handleAddRestaurant(e) {
  e.preventDefault();
  const btn = document.getElementById("btnSubmitAdd");
  btn.disabled = true;
  btn.textContent = "Saving...";

  const payload = {
    name: document.getElementById("addName").value.trim(),
    cuisine: document.getElementById("addCuisine").value.trim(),
    borough: document.getElementById("addBorough").value.trim(),
    building: document.getElementById("addBuilding").value.trim(),
    street: document.getElementById("addStreet").value.trim(),
    zipcode: document.getElementById("addZipcode").value.trim(),
    score: document.getElementById("addScore").value ? parseInt(document.getElementById("addScore").value) : null,
    grade: document.getElementById("addGrade").value,
    restaurantId: document.getElementById("addCustomId").value.trim() || undefined,
  };

  try {
    const res = await fetch(`${API_BASE}/api/restaurants`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    const data = await res.json();
    if (res.ok) {
      showAlert(`Success: Restaurant "${data.name}" added to MongoDB! (ID: ${data.restaurantId})`, "success");
      document.getElementById("addRestaurantForm").reset();
      loadOverview();
      // Switch to finder tab to show added record
      document.getElementById("tabFinder").click();
      document.getElementById("filterName").value = data.name;
      searchRestaurants({ name: data.name });
    } else {
      showAlert(`Add Failed: ${data.error || "Unknown server error"}`, "error");
    }
  } catch (err) {
    showAlert(`Error adding restaurant: ${err.message}`, "error");
  } finally {
    btn.disabled = false;
    btn.textContent = "Add Restaurant";
  }
}

// ==================== UPDATE RESTAURANT ====================

async function openEditModal(id) {
  try {
    const res = await fetch(`${API_BASE}/api/restaurants/${id}`);
    if (!res.ok) throw new Error("Could not fetch restaurant details");
    const r = await res.json();

    document.getElementById("editId").value = id;
    document.getElementById("editName").value = r.name || "";
    document.getElementById("editCuisine").value = r.cuisine || "";
    document.getElementById("editBorough").value = r.borough || "Manhattan";
    document.getElementById("editBuilding").value = r.address ? r.address.building || "" : "";
    document.getElementById("editStreet").value = r.address ? r.address.street || "" : "";
    document.getElementById("editZipcode").value = r.address ? r.address.zipcode || "" : "";

    const latest = r.grades && r.grades.length > 0 ? r.grades[0] : null;
    document.getElementById("editScore").value = latest && latest.score !== undefined ? latest.score : "";
    document.getElementById("editGrade").value = latest ? latest.grade : "A";

    document.getElementById("editModal").classList.remove("hidden");
  } catch (err) {
    showAlert(`Failed to load restaurant: ${err.message}`, "error");
  }
}

function closeEditModal() {
  document.getElementById("editModal").classList.add("hidden");
}

async function handleUpdateRestaurant(e) {
  e.preventDefault();
  const id = document.getElementById("editId").value;
  const payload = {
    name: document.getElementById("editName").value.trim(),
    cuisine: document.getElementById("editCuisine").value.trim(),
    borough: document.getElementById("editBorough").value.trim(),
    building: document.getElementById("editBuilding").value.trim(),
    street: document.getElementById("editStreet").value.trim(),
    zipcode: document.getElementById("editZipcode").value.trim(),
    score: document.getElementById("editScore").value ? parseInt(document.getElementById("editScore").value) : null,
    grade: document.getElementById("editGrade").value,
  };

  try {
    const res = await fetch(`${API_BASE}/api/restaurants/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });

    const data = await res.json();
    if (res.ok) {
      closeEditModal();
      showAlert(`Restaurant "${payload.name}" updated successfully in MongoDB Atlas!`, "success");
      handleSearch();
    } else {
      showAlert(`Update Failed: ${data.error || "Unknown error"}`, "error");
    }
  } catch (err) {
    showAlert(`Update failed: ${err.message}`, "error");
  }
}

// ==================== DELETE RESTAURANT ====================

async function confirmDelete(id, name) {
  const confirmed = confirm(`Are you sure you want to delete "${name}"?\n(Record ID: ${id})\n\nThis will remove the document from MongoDB Atlas.`);
  if (!confirmed) return;

  try {
    const res = await fetch(`${API_BASE}/api/restaurants/${id}`, {
      method: "DELETE",
    });
    const data = await res.json();
    if (res.ok) {
      showAlert(`Restaurant "${name}" (ID: ${id}) deleted successfully from MongoDB.`, "success");
      loadOverview();
      handleSearch();
    } else {
      showAlert(`Delete Failed: ${data.error || "Record not found"}`, "error");
    }
  } catch (err) {
    showAlert(`Delete error: ${err.message}`, "error");
  }
}

// ==================== ANALYTICS & AGGREGATIONS ====================

function switchAnalyticsTab(subId) {
  document.querySelectorAll(".sub-tab").forEach((btn) => {
    btn.classList.toggle("active", btn.getAttribute("data-sub") === subId);
  });
  document.querySelectorAll(".sub-content").forEach((content) => {
    content.classList.toggle("active", content.id === subId);
  });

  if (subId === "tabCuisines") loadCuisinesAnalytics();
  else if (subId === "tabBoroughs") loadBoroughsAnalytics();
  else if (subId === "tabAvgScore") loadAvgScoreAnalytics();
  else if (subId === "tabTopRated") loadTopRatedAnalytics();
  else if (subId === "tabGrades") loadGradesAnalytics();
}

async function loadCuisinesAnalytics() {
  const chart = document.getElementById("cuisinesChart");
  const tbody = document.getElementById("cuisinesTableBody");
  chart.innerHTML = `<p class="text-center">Aggregating...</p>`;
  tbody.innerHTML = `<tr><td colspan="2" class="text-center">Running $group pipeline...</td></tr>`;

  try {
    const res = await fetch(`${API_BASE}/api/analytics/cuisines?limit=10`);
    const data = await res.json();

    const maxCount = Math.max(...data.map((d) => d.count), 1);

    chart.innerHTML = data
      .map(
        (d) => `
      <div class="bar-row">
        <span class="bar-label" title="${escapeHtml(d.cuisine)}">${escapeHtml(d.cuisine)}</span>
        <div class="bar-track">
          <div class="bar-fill" style="width: ${(d.count / maxCount) * 100}%;"></div>
        </div>
        <span class="bar-val">${d.count.toLocaleString()}</span>
      </div>
    `
      )
      .join("");

    tbody.innerHTML = data
      .map(
        (d) => `
      <tr>
        <td><strong>${escapeHtml(d.cuisine)}</strong></td>
        <td>${d.count.toLocaleString()}</td>
      </tr>
    `
      )
      .join("");
  } catch (err) {
    chart.innerHTML = `<p style="color:var(--danger)">Failed to load cuisine analytics: ${err.message}</p>`;
  }
}

async function loadBoroughsAnalytics() {
  const chart = document.getElementById("boroughsChart");
  const tbody = document.getElementById("boroughsTableBody");
  chart.innerHTML = `<p class="text-center">Aggregating...</p>`;
  tbody.innerHTML = `<tr><td colspan="2" class="text-center">Running $group pipeline...</td></tr>`;

  try {
    const res = await fetch(`${API_BASE}/api/analytics/boroughs`);
    const data = await res.json();

    const maxCount = Math.max(...data.map((d) => d.count), 1);

    chart.innerHTML = data
      .map(
        (d) => `
      <div class="bar-row">
        <span class="bar-label">${escapeHtml(d.borough)}</span>
        <div class="bar-track">
          <div class="bar-fill" style="width: ${(d.count / maxCount) * 100}%; background-color: var(--accent);"></div>
        </div>
        <span class="bar-val">${d.count.toLocaleString()}</span>
      </div>
    `
      )
      .join("");

    tbody.innerHTML = data
      .map(
        (d) => `
      <tr>
        <td><strong>${escapeHtml(d.borough)}</strong></td>
        <td>${d.count.toLocaleString()}</td>
      </tr>
    `
      )
      .join("");
  } catch (err) {
    chart.innerHTML = `<p style="color:var(--danger)">Failed to load borough analytics: ${err.message}</p>`;
  }
}

async function loadAvgScoreAnalytics() {
  const chart = document.getElementById("avgScoreChart");
  const tbody = document.getElementById("avgScoreTableBody");
  chart.innerHTML = `<p class="text-center">Aggregating...</p>`;
  tbody.innerHTML = `<tr><td colspan="3" class="text-center">Running $unwind and $avg pipeline...</td></tr>`;

  try {
    const res = await fetch(`${API_BASE}/api/analytics/avg-score?limit=10`);
    const data = await res.json();

    const maxScore = Math.max(...data.map((d) => d.avgScore), 1);

    chart.innerHTML = data
      .map(
        (d) => `
      <div class="bar-row">
        <span class="bar-label" title="${escapeHtml(d.cuisine)}">${escapeHtml(d.cuisine)}</span>
        <div class="bar-track">
          <div class="bar-fill" style="width: ${(d.avgScore / maxScore) * 100}%; background-color: var(--warning);"></div>
        </div>
        <span class="bar-val">${d.avgScore.toFixed(2)}</span>
      </div>
    `
      )
      .join("");

    tbody.innerHTML = data
      .map(
        (d) => `
      <tr>
        <td><strong>${escapeHtml(d.cuisine)}</strong></td>
        <td>${d.avgScore.toFixed(2)}</td>
        <td>${d.count.toLocaleString()}</td>
      </tr>
    `
      )
      .join("");
  } catch (err) {
    chart.innerHTML = `<p style="color:var(--danger)">Failed to load avg score analytics: ${err.message}</p>`;
  }
}

async function loadTopRatedAnalytics() {
  const tbody = document.getElementById("topRatedTableBody");
  tbody.innerHTML = `<tr><td colspan="6" class="text-center">Running $unwind, $group, $sort pipeline...</td></tr>`;

  try {
    const res = await fetch(`${API_BASE}/api/analytics/top-rated?limit=10`);
    const data = await res.json();

    tbody.innerHTML = data
      .map(
        (d, idx) => `
      <tr>
        <td><strong>#${idx + 1}</strong></td>
        <td><strong>${escapeHtml(d.name)}</strong></td>
        <td>${escapeHtml(d.cuisine)}</td>
        <td>${escapeHtml(d.borough)}</td>
        <td><span class="score-badge grade-a">${d.avgScore.toFixed(2)}</span></td>
        <td>${d.reviewCount} inspections</td>
      </tr>
    `
      )
      .join("");
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="6" class="text-center" style="color:var(--danger)">Failed: ${err.message}</td></tr>`;
  }
}

async function loadGradesAnalytics() {
  const chart = document.getElementById("gradesChart");
  const tbody = document.getElementById("gradesTableBody");
  chart.innerHTML = `<p class="text-center">Aggregating...</p>`;
  tbody.innerHTML = `<tr><td colspan="2" class="text-center">Running $group pipeline...</td></tr>`;

  try {
    const res = await fetch(`${API_BASE}/api/analytics/grades`);
    const data = await res.json();

    const maxCount = Math.max(...data.map((d) => d.count), 1);

    chart.innerHTML = data
      .map((d) => {
        let barColor = "var(--primary)";
        if (d.grade === "A") barColor = "var(--success)";
        else if (d.grade === "B") barColor = "var(--warning)";
        else if (d.grade === "C") barColor = "var(--danger)";

        return `
        <div class="bar-row">
          <span class="bar-label">Grade ${escapeHtml(d.grade)}</span>
          <div class="bar-track">
            <div class="bar-fill" style="width: ${(d.count / maxCount) * 100}%; background-color: ${barColor};"></div>
          </div>
          <span class="bar-val">${d.count.toLocaleString()}</span>
        </div>
      `;
      })
      .join("");

    tbody.innerHTML = data
      .map(
        (d) => `
      <tr>
        <td><strong>Grade ${escapeHtml(d.grade)}</strong></td>
        <td>${d.count.toLocaleString()}</td>
      </tr>
    `
      )
      .join("");
  } catch (err) {
    chart.innerHTML = `<p style="color:var(--danger)">Failed: ${err.message}</p>`;
  }
}

// ==================== DATABASE & INDEXES ====================

async function loadIndexes() {
  const tbody = document.getElementById("indexesTableBody");
  tbody.innerHTML = `<tr><td colspan="4" class="text-center">Fetching indexes from MongoDB...</td></tr>`;

  try {
    const res = await fetch(`${API_BASE}/api/indexes`);
    const indexes = await res.json();

    if (!res.ok) throw new Error(indexes.error || "Failed to load indexes");

    tbody.innerHTML = indexes
      .map((idx) => {
        let typeBadge = "Single Field";
        if (idx.name.includes("_") && (idx.keys.match(/:/g) || []).length > 1) {
          typeBadge = "<strong>Compound Index</strong>";
        } else if (idx.name === "_id_") {
          typeBadge = "Default Primary Key";
        }

        return `
        <tr>
          <td><code>${escapeHtml(idx.name)}</code></td>
          <td><code>${escapeHtml(idx.keys)}</code></td>
          <td>${typeBadge}</td>
          <td>${idx.isUnique ? '<span class="score-badge grade-a">YES</span>' : "NO"}</td>
        </tr>
      `;
      })
      .join("");
  } catch (err) {
    tbody.innerHTML = `<tr><td colspan="4" class="text-center" style="color:var(--danger)">Failed to load indexes: ${err.message}</td></tr>`;
  }
}

async function reEnsureIndexes() {
  try {
    showAlert("Ensuring indexes on MongoDB Atlas collection...", "info");
    const res = await fetch(`${API_BASE}/api/indexes/ensure`, { method: "POST" });
    const data = await res.json();
    if (res.ok) {
      showAlert(`Indexes verified on MongoDB Atlas: ${data.indexes.join(", ")}`, "success");
      loadIndexes();
    } else {
      showAlert(`Index creation failed: ${data.error}`, "error");
    }
  } catch (err) {
    showAlert(`Error: ${err.message}`, "error");
  }
}

// ==================== UTILITY HELPERS ====================

function escapeHtml(str) {
  if (str === null || str === undefined) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
