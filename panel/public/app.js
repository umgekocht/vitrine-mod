// Panel UI. All user-provided text is inserted with textContent, never as HTML.
"use strict";

const state = { me: null, csrf: "", moderator: false, view: "browse", queueStatus: "pending", catalog: [], browse: [], suggestSort: "top", suggestStatus: "" };
const $ = (sel) => document.querySelector(sel);

function el(tag, attrs = {}, ...children) {
	const node = document.createElement(tag);
	for (const [k, v] of Object.entries(attrs)) {
		if (k === "class") node.className = v;
		else if (k.startsWith("on")) node.addEventListener(k.slice(2), v);
		else if (v !== false && v != null) node.setAttribute(k, v === true ? "" : v);
	}
	for (const c of children.flat()) if (c != null) node.append(c instanceof Node ? c : document.createTextNode(String(c)));
	return node;
}

async function api(path, { method = "GET", body, form } = {}) {
	const headers = {};
	if (method !== "GET") headers["X-CSRF-Token"] = state.csrf;
	if (body) headers["Content-Type"] = "application/json";
	const res = await fetch(path, { method, headers, credentials: "same-origin", body: form || (body ? JSON.stringify(body) : undefined) });
	const data = await res.json().catch(() => ({}));
	if (!res.ok) throw new Error(data.error || `Request failed (${res.status})`);
	return data;
}

const fmtDate = (ms) => (ms ? new Date(ms).toLocaleString() : "");
const fmtSize = (b) => (b > 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`);

function badge(status) {
	return el("span", { class: `badge ${status}` }, status);
}

function askReason(title, confirmLabel) {
	const dlg = $("#reason-dialog");
	$("#reason-title").textContent = title;
	$("#reason-ok").textContent = confirmLabel;
	$("#reason-text").value = "";
	dlg.showModal();
	return new Promise((resolve) => {
		dlg.addEventListener("close", () => {
			const text = $("#reason-text").value.trim();
			resolve(dlg.returnValue === "ok" && text.length >= 3 ? text : null);
		}, { once: true });
	});
}

function show(view) {
	state.view = view;
	for (const s of document.querySelectorAll(".view")) s.hidden = s.id !== `view-${view}`;
	for (const t of document.querySelectorAll(".tab")) t.classList.toggle("active", t.dataset.view === view);
	if (location.hash.slice(1) !== view) history.replaceState(null, "", `#${view}`);
	({ browse: loadBrowse, suggestions: loadSuggestions, mine: loadMine, queue: loadQueue, catalog: loadCatalog, audit: loadAudit })[view]?.();
}

async function init() {
	const params = new URLSearchParams(location.search);
	if (params.has("error")) history.replaceState(null, "", "/");
	const me = await api("/api/me").catch(() => ({ user: null }));
	const views = ["browse", "suggestions", "mine", "queue", "catalog", "audit"];
	let start = location.hash.slice(1);
	if (me.user) {
		Object.assign(state, { me: me.user, csrf: me.csrf, moderator: me.moderator });
		for (const t of document.querySelectorAll(".mod-only")) t.hidden = !state.moderator;
		const initial = (me.user.nickname || me.user.name || me.user.email).trim().charAt(0).toUpperCase();
		$("#account").append(
			el("span", { class: "avatar", "aria-hidden": "true" }, initial),
			el("span", { class: "email" }, me.user.email),
			el("button", { class: "btn", onclick: logout }, "Sign out"),
		);
	} else {
		$("#account").append(el("a", { class: "btn primary", href: "/auth/login" }, "Sign in"));
		if (params.get("error") === "banned") alert("This account has been blocked.");
	}
	$("#upload-signin").hidden = !!me.user;
	$("#upload-area").hidden = !me.user;
	if (!views.includes(start) || (["queue", "catalog", "audit"].includes(start) && !state.moderator)) start = "browse";
	show(start);
}

async function logout() {
	await api("/auth/logout", { method: "POST" }).catch(() => {});
	location.href = "/";
}

// ---- my uploads -------------------------------------------------------------

async function loadMine() {
	if (!state.me) return;
	const list = $("#mine-list");
	list.replaceChildren(el("div", { class: "empty" }, "Loading…"));
	try {
		const { submissions } = await api("/api/my/submissions");
		list.replaceChildren(...(submissions.length ? submissions.map(mineItem) : [el("div", { class: "empty" }, "Nothing uploaded yet.")]));
	} catch (e) {
		list.replaceChildren(el("div", { class: "empty" }, e.message));
	}
}

function mineItem(s) {
	const actions = el("div", { class: "actions" });
	if (s.status === "pending") {
		actions.append(el("button", {
			class: "btn", onclick: async () => {
				if (!confirm("Withdraw this upload?")) return;
				await api(`/api/submissions/${s.id}/withdraw`, { method: "POST" }).catch((e) => alert(e.message));
				loadMine();
			},
		}, "Withdraw"));
	}
	return el("div", { class: "item" },
		el("div", {},
			el("div", { class: "title" }, `${s.name} `, el("span", { class: "meta" }, `${s.package_id} · ${s.version}`)),
			el("div", { class: "meta" }, badge(s.status), ` uploaded ${fmtDate(s.created_at)}`),
		),
		actions,
		s.reason ? el("div", { class: "desc" }, `Moderator: ${s.reason}`) : null,
	);
}

function setupUpload() {
	const form = $("#upload-form");
	const drop = $("#drop");
	const input = form.elements.file;
	input.addEventListener("change", () => {
		$("#drop-text").textContent = input.files[0] ? `${input.files[0].name} (${fmtSize(input.files[0].size)})` : "Choose a .vpkg file or drop it here";
	});
	for (const ev of ["dragenter", "dragover"]) drop.addEventListener(ev, (e) => { e.preventDefault(); drop.classList.add("over"); });
	for (const ev of ["dragleave", "drop"]) drop.addEventListener(ev, () => drop.classList.remove("over"));
	drop.addEventListener("drop", (e) => {
		e.preventDefault();
		if (e.dataTransfer.files.length) {
			input.files = e.dataTransfer.files;
			input.dispatchEvent(new Event("change"));
		}
	});
	form.addEventListener("submit", async (e) => {
		e.preventDefault();
		const msg = $("#upload-msg");
		const button = form.querySelector("button[type=submit]");
		const file = input.files[0];
		if (!file) return;
		if (file.size > 10 * 1024 * 1024) {
			msg.className = "msg err";
			msg.textContent = "The file is larger than 10 MB.";
			return;
		}
		const data = new FormData();
		data.append("file", file);
		data.append("license", form.elements.license.value);
		data.append("changelog", form.elements.changelog.value);
		button.disabled = true;
		msg.className = "msg";
		msg.textContent = "Checking and uploading…";
		try {
			const r = await api("/api/uploads", { method: "POST", form: data });
			msg.className = "msg ok";
			msg.textContent = `${r.package} ${r.version} is waiting for review.`;
			form.reset();
			$("#drop-text").textContent = "Choose a .vpkg file or drop it here";
			loadMine();
		} catch (err) {
			msg.className = "msg err";
			msg.textContent = err.message;
		} finally {
			button.disabled = false;
		}
	});
}

// ---- moderation -------------------------------------------------------------

async function loadQueue() {
	const list = $("#queue-list");
	list.replaceChildren(el("div", { class: "empty" }, "Loading…"));
	try {
		const { submissions } = await api(`/api/mod/queue?status=${state.queueStatus}`);
		list.replaceChildren(...(submissions.length ? submissions.map(queueItem) : [el("div", { class: "empty" }, "Nothing here.")]));
	} catch (e) {
		list.replaceChildren(el("div", { class: "empty" }, e.message));
	}
}

function queueItem(s) {
	const caps = JSON.parse(s.capabilities || "[]");
	const actions = el("div", { class: "actions" },
		el("a", { class: "btn", href: `/api/mod/submissions/${s.id}/file` }, "Download"),
	);
	if (s.status === "pending") {
		const approveBtn = el("button", {
			class: "btn ok", onclick: async () => {
				if (!confirm(`Publish ${s.package_id} ${s.version} to every Vitrine player?`)) return;
				approveBtn.disabled = true;
				approveBtn.textContent = "Publishing…";
				try {
					await api(`/api/mod/submissions/${s.id}/approve`, { method: "POST" });
				} catch (e) {
					alert(e.message);
				}
				loadQueue();
			},
		}, "Approve");
		actions.append(
			approveBtn,
			el("button", {
				class: "btn danger", onclick: async () => {
					const reason = await askReason(`Reject ${s.package_id} ${s.version}`, "Reject");
					if (!reason) return;
					await api(`/api/mod/submissions/${s.id}/reject`, { method: "POST", body: { reason } }).catch((e) => alert(e.message));
					loadQueue();
				},
			}, "Reject"),
		);
	}
	actions.append(el("button", {
		class: "btn", onclick: async () => {
			const ban = !s.uploader_banned;
			if (!confirm(`${ban ? "Block" : "Unblock"} ${s.uploader_email}?`)) return;
			await api(`/api/mod/users/${s.uploader_id}/${ban ? "ban" : "unban"}`, { method: "POST" }).catch((e) => alert(e.message));
			loadQueue();
		},
	}, s.uploader_banned ? "Unblock user" : "Block user"));

	return el("div", { class: "item" },
		el("div", {},
			el("div", { class: "title" }, `${s.name} `, el("span", { class: "meta" }, `${s.package_id} · ${s.version}`)),
			el("div", { class: "meta" }, badge(s.status),
				` ${s.category} · ${s.license} · ${fmtSize(s.size)} · ${s.file_count} files · by ${s.author} (${s.uploader_email}) · ${fmtDate(s.created_at)}`),
			el("div", { class: "meta" }, `Permissions: ${caps.length ? caps.join(", ") : "none"}${caps.includes("skript") ? "  ⚠ runs Lua/JavaScript – read the scripts before approving" : ""}`),
			el("div", { class: "meta" }, `SHA-256 ${s.sha256}`),
		),
		actions,
		s.description ? el("div", { class: "desc" }, s.description) : null,
		s.changelog ? el("div", { class: "desc" }, `What's new: ${s.changelog}`) : null,
		s.reason ? el("div", { class: "desc" }, `Reason: ${s.reason}`) : null,
	);
}

async function loadCatalog() {
	const list = $("#catalog-list");
	list.replaceChildren(el("div", { class: "empty" }, "Loading…"));
	try {
		state.catalog = (await api("/api/mod/catalog")).packages;
		renderCatalog();
	} catch (e) {
		list.replaceChildren(el("div", { class: "empty" }, e.message));
	}
}

function renderCatalog() {
	const q = $("#catalog-search").value.trim().toLowerCase();
	const rows = state.catalog.filter((p) => !q || `${p.id} ${p.name} ${p.author}`.toLowerCase().includes(q));
	$("#catalog-list").replaceChildren(...(rows.length ? rows.map((p) => el("div", { class: "item" },
		el("div", {},
			el("div", { class: "title" }, `${p.name} `, el("span", { class: "meta" }, p.id)),
			el("div", { class: "meta" }, `${p.category} · ${p.latestVersion} · by ${p.author}`),
		),
		el("div", { class: "actions" }, el("button", {
			class: "btn danger", onclick: async () => {
				const reason = await askReason(`Remove ${p.id} from the catalog`, "Remove");
				if (!reason) return;
				await api(`/api/mod/catalog/${p.id}/delete`, { method: "POST", body: { reason } }).catch((e) => alert(e.message));
				loadCatalog();
			},
		}, "Remove")),
	)) : [el("div", { class: "empty" }, "No packages.")]));
}

async function loadAudit() {
	const list = $("#audit-list");
	try {
		const { entries } = await api("/api/mod/audit");
		list.replaceChildren(...entries.map((a) => el("div", { class: "item" },
			el("div", {}, el("strong", {}, a.action), ` ${a.target}`, a.detail ? el("span", { class: "meta" }, ` – ${a.detail}`) : null),
			el("div", { class: "meta" }, `${a.actor || "?"} · ${fmtDate(a.created_at)}`),
		)));
	} catch (e) {
		list.replaceChildren(el("div", { class: "empty" }, e.message));
	}
}

// ---- browse -------------------------------------------------------------------

const CATEGORY_NAMES = { texturen: "Texture pack", theme: "Theme", hud: "HUD", shader: "Shader", sound: "Sounds",
	sprache: "Language", addon: "Add-on", cosmetics: "Cosmetics", bundle: "Bundle" };

async function loadBrowse() {
	const grid = $("#browse-grid");
	if (!state.browse.length) grid.replaceChildren(el("div", { class: "empty" }, "Loading…"));
	try {
		state.browse = (await api("/api/public/catalog")).packages;
		renderBrowse();
	} catch (e) {
		grid.replaceChildren(el("div", { class: "empty" }, e.message));
	}
}

function renderBrowse() {
	const q = $("#browse-search").value.trim().toLowerCase();
	const cat = $("#browse-category").value;
	const rows = state.browse.filter((p) => (!cat || p.category === cat)
		&& (!q || `${p.name} ${p.author} ${p.description} ${p.tags.join(" ")}`.toLowerCase().includes(q)));
	$("#browse-grid").replaceChildren(...(rows.length ? rows.map((p) => el("button", { class: "tile", onclick: () => openPackage(p.id) },
		p.icon ? el("img", { src: p.icon, alt: "", loading: "lazy", width: "56", height: "56" }) : el("div", { class: "tile-icon" }, p.name.charAt(0)),
		el("div", { class: "tile-text" },
			el("div", { class: "title" }, p.name),
			el("div", { class: "meta" }, `${CATEGORY_NAMES[p.category] || p.category} · by ${p.author}`),
			el("div", { class: "desc clamp" }, p.description),
		),
	)) : [el("div", { class: "empty" }, "No packages match.")]));
}

async function openPackage(id) {
	const body = $("#package-body");
	body.replaceChildren(el("div", { class: "empty" }, "Loading…"));
	$("#package-dialog").showModal();
	try {
		const p = await api(`/api/public/packages/${encodeURIComponent(id)}`);
		body.replaceChildren(
			el("h2", {}, p.name),
			el("div", { class: "meta" }, `${CATEGORY_NAMES[p.category] || p.category} · by ${p.author} · ${p.license}`),
			el("p", { class: "desc" }, p.description || "No description."),
			el("p", { class: "meta" }, `Permissions: ${p.capabilities.length ? p.capabilities.join(", ") : "none"}`),
			el("p", {}, "Install it in game: press ", el("strong", {}, "M"), ", search for ", el("strong", {}, p.name), " and click Install."),
			el("h3", {}, "Versions"),
			el("div", { class: "list compact" }, ...p.versions.map((v) => el("div", { class: "item" },
				el("div", {}, el("strong", {}, v.version),
					el("span", { class: "meta" }, ` ${v.publishedAt ? new Date(v.publishedAt).toLocaleDateString() : ""} · ${fmtSize(v.size)}`)),
				v.download ? el("a", { class: "btn", href: v.download, rel: "noopener noreferrer" }, "Download .vpkg") : null,
			))),
			p.repository ? el("p", {}, el("a", { href: p.repository, rel: "noopener noreferrer", target: "_blank" }, "Source on GitHub")) : null,
		);
	} catch (e) {
		body.replaceChildren(el("div", { class: "empty" }, e.message));
	}
}

// ---- suggestions -----------------------------------------------------------

async function loadSuggestions() {
	const logged = !!state.me;
	$("#suggest-signin").hidden = logged;
	$("#nick-form").hidden = !logged || !!state.me?.nickname;
	$("#suggest-form").hidden = !logged || !state.me?.nickname;
	const list = $("#suggest-list");
	const qs = new URLSearchParams({ sort: state.suggestSort });
	if (state.suggestStatus) qs.set("status", state.suggestStatus);
	try {
		const { suggestions } = await api(`/api/suggestions?${qs}`);
		list.replaceChildren(...(suggestions.length ? suggestions.map(suggestionItem) : [el("div", { class: "empty" }, "No suggestions yet. Be the first!")]));
	} catch (e) {
		list.replaceChildren(el("div", { class: "empty" }, e.message));
	}
}

const KIND_NAMES = { feature: "Idea", bug: "Bug", content: "Content", other: "Other" };

function suggestionItem(s) {
	const vote = el("button", {
		class: `vote${s.voted ? " on" : ""}`, title: state.me ? "Vote" : "Sign in to vote", "aria-pressed": String(!!s.voted),
		onclick: async () => {
			if (!state.me) {
				location.href = "/auth/login";
				return;
			}
			try {
				await api(`/api/suggestions/${s.id}/vote`, { method: "POST" });
				loadSuggestions();
			} catch (e) {
				alert(e.message);
			}
		},
	}, "▲", el("span", {}, String(s.votes)));
	const modTools = state.moderator ? el("div", { class: "actions start" },
		el("select", {
			"aria-label": "Status",
			onchange: async (ev) => {
				const note = prompt("Note for players (optional):", s.mod_note || "") ?? s.mod_note;
				await api(`/api/mod/suggestions/${s.id}`, { method: "POST", body: { status: ev.target.value, note } }).catch((e) => alert(e.message));
				loadSuggestions();
			},
		}, ...["open", "planned", "done", "declined"].map((st) => el("option", { value: st, selected: st === s.status }, st))),
		el("button", {
			class: "btn danger", onclick: async () => {
				if (!confirm("Hide this suggestion?")) return;
				await api(`/api/mod/suggestions/${s.id}`, { method: "POST", body: { hidden: true } }).catch((e) => alert(e.message));
				loadSuggestions();
			},
		}, "Hide"),
	) : null;
	return el("div", { class: "suggestion" }, vote,
		el("div", { class: "s-body" },
			el("div", { class: "title" }, s.title),
			el("div", { class: "meta" }, badge(s.status), ` ${KIND_NAMES[s.kind] || s.kind} · by ${s.author || "player"} · ${fmtDate(s.created_at)}`),
			el("div", { class: "desc" }, s.body),
			s.mod_note ? el("div", { class: "note" }, `Team: ${s.mod_note}`) : null,
			modTools,
		),
	);
}

function setupSuggestions() {
	$("#nick-form").addEventListener("submit", async (e) => {
		e.preventDefault();
		const msg = $("#nick-msg");
		try {
			const r = await api("/api/me/nickname", { method: "POST", body: { nickname: e.target.elements.nickname.value } });
			state.me.nickname = r.nickname;
			loadSuggestions();
		} catch (err) {
			msg.className = "msg err";
			msg.textContent = err.message;
		}
	});
	$("#suggest-form").addEventListener("submit", async (e) => {
		e.preventDefault();
		const f = e.target;
		const msg = $("#suggest-msg");
		try {
			await api("/api/suggestions", { method: "POST", body: { kind: f.elements.kind.value, title: f.elements.title.value, body: f.elements.body.value } });
			f.reset();
			msg.className = "msg ok";
			msg.textContent = "Thanks! Your suggestion is online.";
			loadSuggestions();
		} catch (err) {
			msg.className = "msg err";
			msg.textContent = err.message;
		}
	});
	for (const p of document.querySelectorAll("#suggest-filter .pill")) {
		p.addEventListener("click", () => {
			state.suggestSort = p.dataset.sort || "top";
			state.suggestStatus = p.dataset.status || "";
			for (const q of document.querySelectorAll("#suggest-filter .pill")) q.classList.toggle("active", q === p);
			loadSuggestions();
		});
	}
}

document.addEventListener("DOMContentLoaded", () => {
	$("#browse-search").addEventListener("input", renderBrowse);
	$("#browse-category").addEventListener("change", renderBrowse);
	setupSuggestions();
	for (const t of document.querySelectorAll(".tab")) t.addEventListener("click", () => show(t.dataset.view));
	for (const p of document.querySelectorAll("#queue-filter .pill")) {
		p.addEventListener("click", () => {
			state.queueStatus = p.dataset.status;
			for (const q of document.querySelectorAll("#queue-filter .pill")) q.classList.toggle("active", q === p);
			loadQueue();
		});
	}
	$("#catalog-search").addEventListener("input", renderCatalog);
	setupUpload();
	init();
});
