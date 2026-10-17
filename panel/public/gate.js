"use strict";
// Called by the Turnstile widget once the visitor passed the check.
window.vitrinePassed = async function (token) {
	const msg = document.getElementById("gate-msg");
	msg.textContent = "Checking…";
	try {
		const res = await fetch("/gate/verify", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify({ token }),
			credentials: "same-origin",
		});
		if (!res.ok) throw new Error();
		const next = new URLSearchParams(location.search).get("next") || "/";
		location.replace(next.startsWith("/") && !next.startsWith("//") ? next : "/");
	} catch {
		msg.textContent = "That did not work. Please reload the page and try again.";
	}
};
