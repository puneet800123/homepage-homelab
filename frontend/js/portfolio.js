/* ============================================================
   Portfolio page — profile, certifications, homelab teaser.
   Depends on common.js.
   ============================================================ */
"use strict";

let MEM_TOTAL_SPEC = null;

function isSet(v) { return v && v !== "CHANGE_ME"; }

function renderProfile(p) {
  const nameLink = $("name-link");
  if (nameLink) {
    nameLink.textContent = p.name || "—";
    if (isSet((p.links || {}).linkedin)) nameLink.href = p.links.linkedin;
    else nameLink.removeAttribute("href");
  }
  if ($("title")) $("title").textContent = p.title || "";
  if ($("summary")) $("summary").textContent = p.summary || "";

  const li = p.links || {};
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
  if (!list) return;
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

async function pollTeaser() {
  try {
    renderClusterStatus(await getJson(API.metrics), MEM_TOTAL_SPEC);
  } catch (e) {
    clusterStatusError();
  }
}

async function init() {
  wireNav();
  try {
    renderProfile(await getJson(API.profile));
  } catch (e) {
    if ($("name-link")) $("name-link").textContent = "Puneet Singh";
    if ($("summary")) $("summary").textContent = "Profile unavailable.";
  }
  // Grab the memory hardware spec for the teaser's Memory line.
  try {
    const s = await getJson(API.system);
    MEM_TOTAL_SPEC = s && s.memory && s.memory.total ? s.memory.total : null;
  } catch (e) { /* fall back to live bytes */ }

  await pollTeaser();
  stampUpdated();
  setInterval(async () => { await pollTeaser(); stampUpdated(); }, POLL_MS);
}

document.addEventListener("DOMContentLoaded", init);
