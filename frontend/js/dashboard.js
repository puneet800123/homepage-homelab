/* ============================================================
   Dashboard page — live cluster metrics, gauges, storage,
   trend charts, self-hosted app status, system/hardware.
   Depends on common.js.
   ============================================================ */
"use strict";

// Hardware disk specs keyed by mountpoint, merged into the Storage bars.
let SYS_DISKS = {};
// Hardware memory total (marketed spec, e.g. "24 GB").
let SYS_MEM_TOTAL = null;

/* ------------------------------ gauges ------------------------------- */

function drawGauge(canvasId, value, opts) {
  const cv = $(canvasId);
  if (!cv) return;
  const o = opts || {};
  const max = o.max || 100;
  const unit = o.unit || "";
  const dpr = window.devicePixelRatio || 1;
  if (cv.width !== 180 * dpr) { cv.width = 180 * dpr; cv.height = 140 * dpr; }
  const ctx = cv.getContext("2d");
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, 180, 140);

  const cx = 90, cy = 88, r = 62;
  const startA = Math.PI * 0.75;
  const endA = Math.PI * 2.25;
  const frac = value == null ? 0 : Math.min(1, Math.max(0, value / max));

  ctx.beginPath();
  ctx.arc(cx, cy, r, startA, endA);
  ctx.strokeStyle = "#1c2432";
  ctx.lineWidth = 12;
  ctx.lineCap = "round";
  ctx.stroke();

  const color = o.color || "#38bdf8";
  if (value != null) {
    ctx.beginPath();
    ctx.arc(cx, cy, r, startA, startA + (endA - startA) * frac);
    ctx.strokeStyle = color;
    ctx.lineWidth = 12;
    ctx.lineCap = "round";
    ctx.stroke();
  }

  ctx.fillStyle = "#eef2f7";
  ctx.font = "700 30px ui-monospace, monospace";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(value == null ? "—" : `${Math.round(value)}`, cx, cy - 4);
  ctx.fillStyle = "#93a1b3";
  ctx.font = "13px ui-monospace, monospace";
  ctx.fillText(unit, cx, cy + 20);
}

function pctColor(pct) {
  if (pct == null) return "#38bdf8";
  if (pct >= 90) return "#f87171";
  if (pct >= 75) return "#f5b942";
  return "#38bdf8";
}
function tempColor(t) {
  if (t == null) return "#38bdf8";
  if (t >= 85) return "#f87171";
  if (t >= 70) return "#f5b942";
  return "#34d399";
}

/* --------------------------- cluster metrics ------------------------- */

function renderMetrics(m) {
  renderClusterStatus(m, SYS_MEM_TOTAL);

  const cpu = m && m.cpuUsagePercent;
  drawGauge("gauge-cpu", cpu, { max: 100, unit: "% used", color: pctColor(cpu) });

  const t = m && m.cpuTempCelsius;
  drawGauge("gauge-cputemp", t, { max: 100, unit: "°C", color: tempColor(t) });

  const mem = m && m.memory;
  const memPct = mem ? mem.usedPercent : null;
  drawGauge("gauge-mem", memPct, { max: 100, unit: "% used", color: pctColor(memPct) });
  if ($("mem-sub")) $("mem-sub").textContent = (mem && mem.totalBytes)
    ? `${fmtBytes(mem.usedBytes)} / ${fmtBytes(mem.totalBytes)}` : "—";

  renderDiskBars((m && m.disks) || []);
}

function renderDiskBars(disks) {
  const box = $("disk-bars");
  if (!box) return;
  if (!disks.length) {
    box.innerHTML = `<div class="muted">No disks configured.</div>`;
    return;
  }
  box.innerHTML = disks.map((d) => {
    const pct = d.usedPercent;
    const cls = pctClass(pct);
    const usage = (d.usedBytes != null && d.totalBytes != null)
      ? `${fmtBytes(d.usedBytes)} / ${fmtBytes(d.totalBytes)}` : "—";
    const temp = d.tempCelsius != null
      ? ` · <span class="disk-bar-temp ${tempClass(d.tempCelsius)}">${Math.round(d.tempCelsius)}°C</span>` : "";
    const w = pct == null ? 0 : Math.min(100, pct);
    const hw = SYS_DISKS[d.mountpoint];
    const spec = hw
      ? `<div class="disk-bar-spec">${[hw.model, hw.capacity, hw.type].filter(Boolean).join(" · ")}</div>`
      : "";
    return `
      <div class="disk-bar">
        <div class="disk-bar-head">
          <span class="disk-bar-name">${d.label || d.mountpoint}<span class="disk-bar-mount">${d.mountpoint || ""}</span></span>
          <span class="disk-bar-usage">${usage}${pct != null ? ` · ${pct.toFixed(0)}%` : ""}${temp}</span>
        </div>
        ${spec}
        <div class="disk-bar-track"><div class="disk-bar-fill ${cls}" style="width:${w}%"></div></div>
      </div>`;
  }).join("");
}

/* ------------------------- system / hardware ------------------------- */

function renderSystem(s) {
  const box = $("sysinfo");
  if (!s) return;

  SYS_DISKS = {};
  (s.disks || []).forEach((d) => { if (d.mountpoint) SYS_DISKS[d.mountpoint] = d; });
  SYS_MEM_TOTAL = s.memory && s.memory.total ? s.memory.total : null;
  if (SYS_MEM_TOTAL && $("cs-mem")) $("cs-mem").textContent = SYS_MEM_TOTAL;

  if (!box) return;
  const cards = [];
  cards.push(specCard("Platform", [
    ["System", s.system], ["OS", s.os], ["Kubernetes", s.kubernetes],
  ].filter(([, v]) => v)));
  if (s.cpu) cards.push(specCard("Processor", [
    ["Model", s.cpu.model], ["Threads", s.cpu.cores], ["Base Clock", s.cpu.baseClock],
  ].filter(([, v]) => v != null && v !== "")));
  if (s.memory) cards.push(specCard("Memory", [
    ["Total", s.memory.total], ["Type", s.memory.type],
    ["Rated Speed", s.memory.speed], ["Configured", s.memory.configuredSpeed],
  ].filter(([, v]) => v)));
  box.innerHTML = cards.join("");
}

function specCard(title, rows) {
  const body = rows.map(([k, v]) =>
    `<li class="spec-row"><span class="spec-key">${k}</span><span class="spec-val">${v}</span></li>`).join("");
  return `<div class="card spec-card"><div class="card-head">${title}</div><ul class="spec-list">${body}</ul></div>`;
}

/* --------------------------- trend charts ---------------------------- */

function drawChart(canvasId, points, opts) {
  const cv = $(canvasId);
  if (!cv) return;
  const dpr = window.devicePixelRatio || 1;
  const w = cv.clientWidth || 280;
  const h = cv.clientHeight || 90;
  cv.width = w * dpr;
  cv.height = h * dpr;
  const ctx = cv.getContext("2d");
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.clearRect(0, 0, w, h);

  const color = (opts && opts.color) || "#38bdf8";
  const pad = 4;
  if (!points || points.length < 2) {
    ctx.fillStyle = "#93a1b3";
    ctx.font = "12px monospace";
    ctx.fillText("no data", 8, h / 2);
    return;
  }
  const vals = points.map((p) => p.v);
  let max = (opts && opts.max) || Math.max(...vals);
  if (!max || max <= 0) max = 1;
  const n = points.length;
  const xOf = (i) => pad + (i / (n - 1)) * (w - pad * 2);
  const yOf = (v) => h - pad - (v / max) * (h - pad * 2);

  ctx.beginPath();
  ctx.moveTo(xOf(0), yOf(vals[0]));
  for (let i = 1; i < n; i++) ctx.lineTo(xOf(i), yOf(vals[i]));
  ctx.lineTo(xOf(n - 1), h - pad);
  ctx.lineTo(xOf(0), h - pad);
  ctx.closePath();
  const grad = ctx.createLinearGradient(0, 0, 0, h);
  grad.addColorStop(0, hexToRgba(color, 0.35));
  grad.addColorStop(1, hexToRgba(color, 0.02));
  ctx.fillStyle = grad;
  ctx.fill();

  ctx.beginPath();
  ctx.moveTo(xOf(0), yOf(vals[0]));
  for (let i = 1; i < n; i++) ctx.lineTo(xOf(i), yOf(vals[i]));
  ctx.strokeStyle = color;
  ctx.lineWidth = 1.8;
  ctx.stroke();
}

function hexToRgba(hex, a) {
  const m = hex.replace("#", "");
  return `rgba(${parseInt(m.substring(0, 2), 16)},${parseInt(m.substring(2, 4), 16)},${parseInt(m.substring(4, 6), 16)},${a})`;
}

function renderHistory(hist) {
  if (!hist) return;
  drawChart("chart-cpu", hist.cpuUsagePercent, { color: "#38bdf8", max: 100 });
  drawChart("chart-mem", hist.memoryUsedPercent, { color: "#34d399", max: 100 });
  const rx = hist.networkRxBytesPerSec || [];
  const tx = hist.networkTxBytesPerSec || [];
  const allMax = Math.max(1, ...rx.map((p) => p.v), ...tx.map((p) => p.v));
  drawChart("chart-net", rx, { color: "#a78bfa", max: allMax });

  const last = (arr) => (arr && arr.length ? arr[arr.length - 1].v : null);
  const cpuNow = last(hist.cpuUsagePercent), memNow = last(hist.memoryUsedPercent);
  if ($("cpu-now")) $("cpu-now").textContent = cpuNow == null ? "—" : `${cpuNow.toFixed(0)}%`;
  if ($("mem-now")) $("mem-now").textContent = memNow == null ? "—" : `${memNow.toFixed(0)}%`;
  const rxNow = last(rx), txNow = last(tx);
  if ($("net-now")) $("net-now").textContent =
    `↓ ${rxNow == null ? "—" : fmtRate(rxNow)} · ↑ ${txNow == null ? "—" : fmtRate(txNow)}`;
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
  if (!grid) return;
  if (!apps || !apps.length) {
    grid.innerHTML = `<div class="card muted">No applications configured.</div>`;
    return;
  }
  // Group by category, preserving a sensible category order.
  const ORDER = ["Media", "Observability", "Smart Home", "Apps & Tools", "Databases & Infra", "Other"];
  const groups = {};
  apps.forEach((a) => {
    const cat = a.category || "Other";
    (groups[cat] = groups[cat] || []).push(a);
  });
  const cats = Object.keys(groups).sort((a, b) => {
    const ia = ORDER.indexOf(a), ib = ORDER.indexOf(b);
    return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib);
  });

  grid.innerHTML = cats.map((cat) => {
    const meta = CATEGORY_META[cat] || CATEGORY_META.Other;
    const list = groups[cat];
    const okCount = list.filter((a) => a.health === "OK").length;
    const tiles = list.map(appTile).join("");
    return `<section class="app-cat cat-${meta.slug}" style="--cat:${meta.color}">
        <header class="app-cat-head">
          <span class="app-cat-icon">${meta.icon}</span>
          <h3 class="app-cat-title">${cat}</h3>
          <span class="app-cat-count">${okCount}/${list.length} up</span>
        </header>
        <div class="grid apps-subgrid">${tiles}</div>
      </section>`;
  }).join("");
}

// Per-category color + icon for visual distinction.
const CATEGORY_META = {
  "Media":             { slug: "media",   color: "#f472b6", icon: "🎬" },
  "Observability":     { slug: "obs",     color: "#38bdf8", icon: "📊" },
  "Smart Home":        { slug: "smart",   color: "#34d399", icon: "🏠" },
  "Apps & Tools":      { slug: "apps",    color: "#a78bfa", icon: "🧰" },
  "Databases & Infra": { slug: "db",      color: "#f5b942", icon: "🗄️" },
  "Other":             { slug: "other",   color: "#93a1b3", icon: "📦" },
};

function appTile(a) {
  const [cls, label] = HEALTH_PILL[a.health] || HEALTH_PILL.NOT_CONFIGURED;
  const stats = a.stats && Object.keys(a.stats).length
    ? `<div class="app-stats">` +
      Object.entries(a.stats).map(([k, v]) => `<div><span>${k}</span><span>${v}</span></div>`).join("") +
      `</div>`
    : "";
  const inner = `
      <div class="app-head">
        <span class="app-name">${a.name}</span>
        <span class="pill ${cls}">${label}</span>
      </div>
      ${stats}`;
  if (a.nodePort) {
    const url = `http://${location.hostname}:${a.nodePort}`;
    return `<a class="card app-card app-link" href="${url}" target="_blank" rel="noopener">${inner}<span class="app-open">Open ↗</span></a>`;
  }
  return `<div class="card app-card">${inner}</div>`;
}

function appsError() {
  if ($("apps-grid")) $("apps-grid").innerHTML = `<div class="card muted">Cannot reach dashboard API.</div>`;
}

/* ------------------------------ polling ------------------------------ */

async function pollMetrics() {
  try { renderMetrics(await getJson(API.metrics)); } catch (e) { clusterStatusError(); }
}
async function pollApps() {
  try { renderApps(await getJson(API.apps)); } catch (e) { appsError(); }
}
async function pollHistory() {
  try { renderHistory(await getJson(API.history)); } catch (e) { /* keep last */ }
}

async function init() {
  wireNav();
  try {
    renderSystem(await getJson(API.system));
  } catch (e) {
    if ($("sysinfo")) $("sysinfo").innerHTML = `<div class="card muted">System info unavailable.</div>`;
  }
  await Promise.all([pollMetrics(), pollApps()]);
  stampUpdated();
  setInterval(async () => { await Promise.all([pollMetrics(), pollApps()]); stampUpdated(); }, POLL_MS);

  await pollHistory();
  setInterval(pollHistory, 30000);

  let rt;
  window.addEventListener("resize", () => { clearTimeout(rt); rt = setTimeout(pollHistory, 300); });
}

document.addEventListener("DOMContentLoaded", init);
