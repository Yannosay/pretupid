CREATE TABLE messages_next (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  from_uuid TEXT NOT NULL,
  to_uuid TEXT NOT NULL,
  kind TEXT NOT NULL CHECK (kind IN ('text', 'invite', 'screenie')),
  body TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  read_at INTEGER
);

INSERT INTO messages_next (id, from_uuid, to_uuid, kind, body, created_at, read_at)
SELECT id, from_uuid, to_uuid, kind, body, created_at, read_at FROM messages;

DROP TABLE messages;

ALTER TABLE messages_next RENAME TO messages;

CREATE INDEX IF NOT EXISTS messages_inbox ON messages (to_uuid, id);
CREATE INDEX IF NOT EXISTS messages_pair ON messages (from_uuid, to_uuid, id);
CREATE INDEX IF NOT EXISTS messages_age ON messages (created_at);

CREATE TABLE IF NOT EXISTS screenies (
  id TEXT PRIMARY KEY NOT NULL,
  owner TEXT NOT NULL,
  recipient TEXT NOT NULL,
  width INTEGER NOT NULL,
  height INTEGER NOT NULL,
  size INTEGER NOT NULL,
  created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS screenies_age ON screenies (created_at);
