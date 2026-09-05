/* ============================================================
   homepage-homelab frontend
   Fetches profile once, polls cluster metrics + app status.
   Every live element degrades gracefully on error.
   ============================================================ */
"use strict";

const API = {
  profile: "/api/profile",
  metrics: "/api/cluster/metrics",
  apps: "/api/apps",
};

const POLL_MS = 5000;

/* ------------------------------ helpers ------------------------------ */

function $(id) { return document.getElementById(id); }

function fmtBytes(bytes) {
  if (bytes == null || isNaN(bytes)) return "—";
  const units = ["B", "KB", "MB", "GB", "TB", "PB"];
  let v = Math.abs(bytes), i = 0;
  while (v >= 1024 && i < units.length - 1) { v /= 1024; i++; }
  return `${v.toFixed(v < 10 && i > 0 ? 1 : 0)} ${units[i]}`;
}

function fmtRate(bytesPerSec) {
  if (bytesPerSec == null || isNaN(bytesPerSec)) return "—";
  return `${fmtBytes(bytesPerSec)}/s`;
}

// Return a threshold class for a 0-100 percentage.
function pctClass(pct) {
  if (pct == null) return "";
  if (pct >= 90) return "crit";
  if (pct >= 75) return "warn";
  return "";
}

// Return a threshold class for a temperature in Celsius.
function tempClass(t) {
  if (t == null) return "";
  if (t >= 85) return "crit";
  if (t >= 70) return "warn";
  return "";
}

function setBar(el, pct, cls) {
  if (!el) return;
  el.style.width = pct == null ? "0%" : `${Math.min(100, Math.max(0, pct))}%`;
  el.classList.remove("warn", "crit");
  if (cls) el.classList.add(cls);
}

async function getJson(url) {
  const resp = await fetch(url, { headers: { "Accept": "application/json" } });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json();
}

/* ------------------------------ profile ------------------------------ */

function isSet(v) { return v && v !== "CHANGE_ME"; }

function renderProfile(p) {
  const nameLink = $("name-link");
  nameLink.textContent = p.name || "—";
  const li = p.links || {};

  if (isSet(li.linkedin)) { nameLink.href = li.linkedin; }
  else { nameLink.removeAttribute("href"); }

  $("title").textContent = p.title || "";
  $("summary").textContent = p.summary || "";

  wireLink("resume-link", li.resume);
  wireLink("cover-link", li.coverLetter);
  wireLink("github-link", li.github);
  wireLink("linkedin-link2", li.linkedin);

  renderCerts(p.certifications || []);
}

function wireLink(id, url) {
  const el = $(id);
  if (!el) return;
  if (isSet(url)) {
    el.href = url;
    el.classList.remove("disabled");
  } else {
    el.removeAttribute("href");
    el.classList.add("disabled");
    el.title = "Link not configured yet";
  }
}

function renderCerts(certs) {
  const list = $("certs-list");
  if (!certs.length) {
    list.innerHTML = `<li class="muted">No certifications listed yet.</li>`;
    return;
  }
  list.innerHTML = certs.map((c) => {
    const meta = [c.issuer, c.year].filter(Boolean).join(" · ");
    const name = c.verifyUrl
      ? `<a href="${c.verifyUrl}" target="_blank" rel="noopener">${c.name}</a>`
      : c.name;
    return `<li><div class="cert-name">${name}</div>` +
           (meta ? `<div class="cert-meta">${meta}</div>` : "") + `</li>`;
  }).join("");
}

/* --------------------------- cluster metrics -------------------------- */

function renderMetrics(m) {
  // Quick-status pill
  const pill = $("cluster-pill");
  const sub = $("cluster-sub");
  if (m && m.prometheusReachable) {
    pill.className = "pill pill-ok";
    pill.textContent = "● Operational";
    sub.textContent = "Cluster: K3s · metrics live";
  } else {
    pill.className = "pill pill-crit";
    pill.textContent = "● Metrics unavailable";
    sub.textContent = "Cluster: Prometheus unreachable";
  }

  // CPU
  const cpu = m && m.cpuUsagePercent;
  $("cpu-val").textContent = cpu == null ? "—" : cpu.toFixed(1);
  setBar($("cpu-bar"), cpu, pctClass(cpu));

  // CPU temp
  const t = m && m.cpuTempCelsius;
  $("cputemp-val").textContent = t == null ? "—" : Math.round(t);
  // Map temp roughly onto the bar (30-95C range).
  const tPct = t == null ? null : ((t - 30) / (95 - 30)) * 100;
  setBar($("cputemp-bar"), tPct, tempClass(t));

  // Memory
  const mem = m && m.memory;
  if (mem && mem.totalBytes) {
    $("mem-val").textContent = `${fmtBytes(mem.usedBytes)} / ${fmtBytes(mem.totalBytes)}`;
    setBar($("mem-bar"), mem.usedPercent, pctClass(mem.usedPercent));
  } else {
    $("mem-val").textContent = "—";
    setBar($("mem-bar"), null);
  }

  // Network
  const net = m && m.network;
  $("net-rx").textContent = net ? fmtRate(net.rxBytesPerSec) : "—";
  $("net-tx").textContent = net ? fmtRate(net.txBytesPerSec) : "—";

  // Disks
  renderDisks((m && m.disks) || []);
}

function renderDisks(disks) {
  const grid = $("disks-grid");
  if (!disks.length) {
    grid.innerHTML = `<div class="card muted">No disks configured.</div>`;
    return;
  }
  grid.innerHTML = disks.map((d) => {
    const pct = d.usedPercent;
    const cls = pctClass(pct);
    const temp = d.tempCelsius != null
      ? `<span class="disk-temp ${tempClass(d.tempCelsius)}">${Math.round(d.tempCelsius)}°C</span>`
      : "";
    const usage = (d.usedBytes != null && d.totalBytes != null)
      ? `${fmtBytes(d.usedBytes)} / ${fmtBytes(d.totalBytes)}`
      : "—";
    return `
      <div class="card">
        <div class="disk-name">${d.label || d.mountpoint} ${temp}</div>
        <div class="disk-mount">${d.mountpoint || ""}</div>
        <div class="bar"><div class="bar-fill ${cls}" style="width:${pct == null ? 0 : Math.min(100, pct)}%"></div></div>
        <div class="disk-usage">${usage}${pct != null ? ` · ${pct.toFixed(0)}%` : ""}</div>
      </div>`;
  }).join("");
}

function metricsError() {
  $("cluster-pill").className = "pill pill-crit";
  $("cluster-pill").textContent = "● Backend unreachable";
  $("cluster-sub").textContent = "Cluster: cannot reach dashboard API";
}

/* ------------------------------- apps -------------------------------- */

const HEALTH_PILL = {
  OK:             ["pill-ok",      "● OK"],
  DEGRADED:       ["pill-warn",    "● Degraded"],
  UNREACHABLE:    ["pill-crit",    "● Unreachable"],
  NOT_CONFIGURED: ["pill-loading", "○ Not configured"],
};

function renderApps(apps) {
  const grid = $("apps-grid");
  if (!apps || !apps.length) {
    grid.innerHTML = `<div class="card muted">No applications configured.</div>`;
    return;
  }
  grid.innerHTML = apps.map((a) => {
    const [cls, label] = HEALTH_PILL[a.health] || HEALTH_PILL.NOT_CONFIGURED;
    const stats = a.stats && Object.keys(a.stats).length
      ? `<div class="app-stats">` +
        Object.entries(a.stats).map(([k, v]) => `<div><span>${k}</span><span>${v}</span></div>`).join("") +
        `</div>`
      : "";
    return `
      <div class="card app-card">
        <div class="app-head">
          <span class="app-name">${a.name}</span>
          <span class="pill ${cls}">${label}</span>
        </div>
        ${stats}
      </div>`;
  }).join("");
}

function appsError() {
  $("apps-grid").innerHTML = `<div class="card muted">Cannot reach dashboard API.</div>`;
}

/* ------------------------------ polling ------------------------------ */

async function pollMetrics() {
  try {
    renderMetrics(await getJson(API.metrics));
  } catch (e) {
    metricsError();
  }
}

async function pollApps() {
  try {
    renderApps(await getJson(API.apps));
  } catch (e) {
    appsError();
  }
}

function stampUpdated() {
  $("last-updated").textContent = "Updated " + new Date().toLocaleTimeString();
}

async function tick() {
  await Promise.all([pollMetrics(), pollApps()]);
  stampUpdated();
}

async function init() {
  try {
    renderProfile(await getJson(API.profile));
  } catch (e) {
    $("name-link").textContent = "Puneet Singh";
    $("summary").textContent = "Profile unavailable.";
  }
  await tick();
  setInterval(tick, POLL_MS);
}

document.addEventListener("DOMContentLoaded", init);
