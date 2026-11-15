CREATE TABLE IF NOT EXISTS players (
  uuid TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  name_lower TEXT NOT NULL,
  first_seen INTEGER NOT NULL,
  last_seen INTEGER NOT NULL,
  online INTEGER NOT NULL DEFAULT 0,
  client_version TEXT NOT NULL DEFAULT '',
  style TEXT NOT NULL DEFAULT '{}',
  equipped TEXT NOT NULL DEFAULT '{}',
  emote_id TEXT,
  emote_at INTEGER,
  server_address TEXT,
  share_server INTEGER NOT NULL DEFAULT 0,
  allow_requests INTEGER NOT NULL DEFAULT 1,
  upload_banned INTEGER NOT NULL DEFAULT 0,
  banned INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS players_name ON players (name_lower);
CREATE INDEX IF NOT EXISTS players_seen ON players (last_seen);

CREATE TABLE IF NOT EXISTS capes (
  id TEXT PRIMARY KEY NOT NULL,
  uuid TEXT NOT NULL,
  sha256 TEXT NOT NULL,
  width INTEGER NOT NULL,
  height INTEGER NOT NULL,
  size INTEGER NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('active', 'replaced', 'removed')),
  reports INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  removed_at INTEGER,
  removed_reason TEXT
);

CREATE INDEX IF NOT EXISTS capes_owner ON capes (uuid, status);
CREATE INDEX IF NOT EXISTS capes_hash ON capes (sha256, status);
CREATE INDEX IF NOT EXISTS capes_queue ON capes (status, reports DESC, created_at DESC);

CREATE TABLE IF NOT EXISTS cape_reports (
  cape_id TEXT NOT NULL,
  reporter TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (cape_id, reporter)
);

CREATE TABLE IF NOT EXISTS cosmetics (
  id TEXT PRIMARY KEY NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('cape', 'trail', 'emote')),
  name TEXT NOT NULL,
  description TEXT NOT NULL DEFAULT '',
  asset_key TEXT,
  asset_sha256 TEXT,
  data TEXT NOT NULL DEFAULT '{}',
  enabled INTEGER NOT NULL DEFAULT 1,
  sort INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS cosmetics_type ON cosmetics (type, enabled, sort);

CREATE TABLE IF NOT EXISTS friendships (
  uuid TEXT NOT NULL,
  friend TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (uuid, friend)
);

CREATE TABLE IF NOT EXISTS friend_requests (
  from_uuid TEXT NOT NULL,
  to_uuid TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (from_uuid, to_uuid)
);

CREATE INDEX IF NOT EXISTS friend_requests_to ON friend_requests (to_uuid);

CREATE TABLE IF NOT EXISTS blocks (
  uuid TEXT NOT NULL,
  blocked TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (uuid, blocked)
);

CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  from_uuid TEXT NOT NULL,
  to_uuid TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('text', 'invite')),
  body TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  read_at INTEGER
);

CREATE INDEX IF NOT EXISTS messages_inbox ON messages (to_uuid, id);
CREATE INDEX IF NOT EXISTS messages_pair ON messages (from_uuid, to_uuid, id);
CREATE INDEX IF NOT EXISTS messages_age ON messages (created_at);

CREATE TABLE IF NOT EXISTS news (
  id TEXT PRIMARY KEY NOT NULL,
  title TEXT NOT NULL,
  summary TEXT NOT NULL DEFAULT '',
  body TEXT NOT NULL,
  category TEXT NOT NULL CHECK (category IN ('news', 'update', 'announcement', 'event', 'maintenance')),
  pinned INTEGER NOT NULL DEFAULT 0,
  published INTEGER NOT NULL DEFAULT 0,
  published_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS news_feed ON news (published, pinned DESC, published_at DESC);

CREATE TABLE IF NOT EXISTS audit_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  action TEXT NOT NULL,
  target TEXT NOT NULL DEFAULT '',
  detail TEXT NOT NULL DEFAULT '',
  ip TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS rate_limits (
  key TEXT PRIMARY KEY NOT NULL,
  window_start INTEGER NOT NULL,
  count INTEGER NOT NULL
);
