-- Vitrine panel database (Cloudflare D1 / SQLite).

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  google_sub TEXT NOT NULL UNIQUE,
  email TEXT NOT NULL,
  name TEXT NOT NULL,
  banned INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);

-- Only the SHA-256 of the session token is stored; a leaked database cannot be used to log in.
CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  csrf TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS sessions_user ON sessions(user_id);

-- Short-lived OAuth state (state, PKCE verifier, nonce) between redirect and callback.
CREATE TABLE IF NOT EXISTS oauth_states (
  state TEXT PRIMARY KEY,
  verifier TEXT NOT NULL,
  nonce TEXT NOT NULL,
  expires_at INTEGER NOT NULL
);

-- A package id belongs to the first user who got it approved (or who has it pending).
CREATE TABLE IF NOT EXISTS packages (
  id TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL REFERENCES users(id),
  name TEXT NOT NULL,
  category TEXT NOT NULL,
  published INTEGER NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS submissions (
  id TEXT PRIMARY KEY,
  package_id TEXT NOT NULL REFERENCES packages(id),
  user_id TEXT NOT NULL REFERENCES users(id),
  version TEXT NOT NULL,
  name TEXT NOT NULL,
  category TEXT NOT NULL,
  description TEXT NOT NULL,
  license TEXT NOT NULL,
  author TEXT NOT NULL,
  capabilities TEXT NOT NULL,
  changelog TEXT NOT NULL DEFAULT '',
  r2_key TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  size INTEGER NOT NULL,
  file_count INTEGER NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('pending', 'processing', 'approved', 'rejected', 'withdrawn', 'removed')),
  reason TEXT NOT NULL DEFAULT '',
  reviewer_id TEXT,
  download_url TEXT,
  created_at INTEGER NOT NULL,
  decided_at INTEGER
);
CREATE INDEX IF NOT EXISTS submissions_status ON submissions(status, created_at);
CREATE INDEX IF NOT EXISTS submissions_user ON submissions(user_id, created_at);

CREATE TABLE IF NOT EXISTS audit_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  actor_id TEXT,
  action TEXT NOT NULL,
  target TEXT NOT NULL,
  detail TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);

-- Public nickname shown next to suggestions (Google name and e-mail are never shown).
CREATE TABLE IF NOT EXISTS nicknames (
  user_id TEXT PRIMARY KEY REFERENCES users(id),
  nickname TEXT NOT NULL UNIQUE COLLATE NOCASE
);

CREATE TABLE IF NOT EXISTS suggestions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  kind TEXT NOT NULL CHECK (kind IN ('feature', 'bug', 'content', 'other')),
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'planned', 'done', 'declined')),
  mod_note TEXT NOT NULL DEFAULT '',
  votes INTEGER NOT NULL DEFAULT 0,
  hidden INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS suggestions_votes ON suggestions(hidden, votes DESC, created_at DESC);

CREATE TABLE IF NOT EXISTS suggestion_votes (
  suggestion_id TEXT NOT NULL REFERENCES suggestions(id),
  user_id TEXT NOT NULL REFERENCES users(id),
  created_at INTEGER NOT NULL,
  PRIMARY KEY (suggestion_id, user_id)
);

-- Fixed-window rate limits (login attempts per IP, posts per user, ...).
CREATE TABLE IF NOT EXISTS rate_limits (
  key TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  window_end INTEGER NOT NULL
);
