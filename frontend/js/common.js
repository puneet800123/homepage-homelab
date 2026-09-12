/* ============================================================
   homepage-homelab — shared helpers (loaded by both pages)
   ============================================================ */
"use strict";

const API = {
  profile: "/api/profile",
  metrics: "/api/cluster/metrics",
  history: "/api/cluster/history",
  apps: "/api/apps",
  system: "/api/system/info",
};

const POLL_MS = 5000;

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

function pctClass(pct) {
  if (pct == null) return "";
  if (pct >= 90) return "crit";
  if (pct >= 75) return "warn";
  return "";
}

function tempClass(t) {
  if (t == null) return "";
  if (t >= 85) return "crit";
  if (t >= 70) return "warn";
  return "";
}

async function getJson(url) {
  const resp = await fetch(url, { headers: { "Accept": "application/json" } });
  if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
  return resp.json();
}

function stampUpdated() {
  const el = $("last-updated");
  if (el) el.textContent = "Updated " + new Date().toLocaleTimeString();
}

// Mobile nav toggle + auto-close on link click.
function wireNav() {
  const toggle = $("nav-toggle");
  const links = $("nav-links");
  if (!toggle || !links) return;
  toggle.addEventListener("click", () => links.classList.toggle("open"));
  links.querySelectorAll("a").forEach((a) =>
    a.addEventListener("click", () => links.classList.remove("open")));
}

// Update the cluster status pill + inventory counts (used on both pages).
// memTotalSpec (optional) overrides the memory line with the hardware spec.
function renderClusterStatus(m, memTotalSpec) {
  const pill = $("cluster-pill");
  const sub = $("cluster-sub");
  if (pill) {
    if (m && m.prometheusReachable) {
      pill.className = "pill pill-ok";
      pill.textContent = "● Operational";
      if (sub) sub.textContent = "Cluster: K3s · metrics live";
    } else {
      pill.className = "pill pill-crit";
      pill.textContent = "● Metrics unavailable";
      if (sub) sub.textContent = "Cluster: Prometheus unreachable";
    }
  }
  const cl = m && m.cluster;
  if ($("cs-nodes")) $("cs-nodes").textContent = cl && cl.nodes != null ? cl.nodes : "—";
  if ($("cs-pods")) $("cs-pods").textContent = cl && cl.podsTotal != null
    ? `${cl.podsRunning != null ? cl.podsRunning : "?"} / ${cl.podsTotal} running` : "—";
  if ($("cs-cores")) $("cs-cores").textContent = cl && cl.cpuCores != null ? cl.cpuCores : "—";
  if ($("cs-mem")) $("cs-mem").textContent = memTotalSpec
    ? memTotalSpec
    : (cl && cl.memoryTotalBytes != null ? fmtBytes(cl.memoryTotalBytes) : "—");
}

function clusterStatusError() {
  const pill = $("cluster-pill");
  if (pill) { pill.className = "pill pill-crit"; pill.textContent = "● Backend unreachable"; }
  if ($("cluster-sub")) $("cluster-sub").textContent = "Cluster: cannot reach API";
}
