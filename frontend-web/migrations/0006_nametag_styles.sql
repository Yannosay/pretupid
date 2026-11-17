CREATE TABLE cosmetics_next (
  id TEXT PRIMARY KEY NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('cape', 'trail', 'emote', 'hat', 'back', 'pet', 'nametag')),
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

INSERT INTO cosmetics_next (id, type, name, description, asset_key, asset_sha256, data, enabled, sort, created_at, updated_at)
SELECT id, type, name, description, asset_key, asset_sha256, data, enabled, sort, created_at, updated_at FROM cosmetics;

DROP TABLE cosmetics;

ALTER TABLE cosmetics_next RENAME TO cosmetics;

CREATE INDEX IF NOT EXISTS cosmetics_type ON cosmetics (type, enabled, sort);

INSERT OR IGNORE INTO cosmetics (id, type, name, description, data, enabled, sort, created_at, updated_at) VALUES
('sky', 'nametag', 'Sky', 'A calm light blue name.', '{"animation":"STATIC","effect":"NONE","primary":"#8FC4FF","secondary":"#8FC4FF","speed":1,"bold":false}', 1, 10, 1795392000, 1795392000),
('sunset', 'nametag', 'Sunset', 'Orange fading into pink.', '{"animation":"GRADIENT","effect":"NONE","primary":"#FDBA74","secondary":"#F472B6","speed":1,"bold":false}', 1, 20, 1795392000, 1795392000),
('ocean', 'nametag', 'Ocean', 'Blue waves roll through your name.', '{"animation":"WAVE","effect":"WAVE","primary":"#38BDF8","secondary":"#A5F3FC","speed":1,"bold":false}', 1, 30, 1795392000, 1795392000),
('neon', 'nametag', 'Neon', 'Pulses between green and cyan.', '{"animation":"PULSE","effect":"NONE","primary":"#4ADE80","secondary":"#22D3EE","speed":1.2,"bold":true}', 1, 40, 1795392000, 1795392000),
('gold', 'nametag', 'Gold', 'A shine sweeps across bold gold letters.', '{"animation":"SHIMMER","effect":"NONE","primary":"#FACC15","secondary":"#F59E0B","speed":1,"bold":true}', 1, 50, 1795392000, 1795392000),
('rainbow', 'nametag', 'Rainbow', 'Every color, bouncing along.', '{"animation":"RAINBOW","effect":"HOP","primary":"#FFFFFF","secondary":"#FFFFFF","speed":1,"bold":false}', 1, 60, 1795392000, 1795392000),
('flame', 'nametag', 'Flame', 'Flickering fire colors.', '{"animation":"FLAME","effect":"FLOAT","primary":"#FFFFFF","secondary":"#FFFFFF","speed":1,"bold":true}', 1, 70, 1795392000, 1795392000),
('northern_lights', 'nametag', 'Aurora', 'Northern lights drifting through green and teal.', '{"animation":"AURORA","effect":"NONE","primary":"#FFFFFF","secondary":"#FFFFFF","speed":1,"bold":false}', 1, 80, 1795392000, 1795392000);
