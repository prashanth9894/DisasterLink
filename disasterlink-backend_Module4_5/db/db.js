const fs = require('fs');
const path = require('path');
const { DatabaseSync } = require('node:sqlite');

const DATA_DIR = path.join(__dirname, '..', 'data');
const DB_PATH = process.env.DISASTERLINK_DB_PATH || path.join(DATA_DIR, 'disasterlink.db');

fs.mkdirSync(path.dirname(DB_PATH), { recursive: true });

// Node 22+ ships a synchronous SQLite implementation. Using it here keeps the
// backend self-contained and avoids native-module build failures on Windows.
const db = new DatabaseSync(DB_PATH);
db.exec('PRAGMA foreign_keys = ON;');
db.exec('PRAGMA journal_mode = WAL;');
db.exec('PRAGMA busy_timeout = 5000;');

db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  phone TEXT UNIQUE NOT NULL,
  email TEXT UNIQUE NOT NULL,
  government_id TEXT UNIQUE NOT NULL,
  address TEXT NOT NULL,
  pincode TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK(role IN ('CITIZEN','RESCUE','AUTHORITY')),
  authority_type TEXT,
  district TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL DEFAULT (unixepoch('now','subsec') * 1000)
);

CREATE TABLE IF NOT EXISTS invite_codes (
  code TEXT PRIMARY KEY,
  authority_type TEXT NOT NULL,
  district TEXT,
  issued_by TEXT,
  used_by TEXT,
  expires_at INTEGER NOT NULL,
  used_at INTEGER,
  created_at INTEGER NOT NULL DEFAULT (unixepoch('now','subsec') * 1000)
);

CREATE TABLE IF NOT EXISTS emergencies (
  id TEXT PRIMARY KEY,
  client_id TEXT,
  citizen_id TEXT NOT NULL,
  type TEXT NOT NULL,
  message TEXT NOT NULL DEFAULT '',
  latitude REAL,
  longitude REAL,
  district TEXT NOT NULL DEFAULT '',
  reporter_name TEXT NOT NULL DEFAULT '',
  reporter_phone TEXT NOT NULL DEFAULT '',
  reporter_government_id TEXT NOT NULL DEFAULT '',
  status TEXT NOT NULL DEFAULT 'REPORTED',
  created_at INTEGER NOT NULL DEFAULT (unixepoch('now','subsec') * 1000)
);

CREATE TABLE IF NOT EXISTS rescue_teams (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  member_ids TEXT NOT NULL,
  phone TEXT NOT NULL,
  latitude REAL,
  longitude REAL,
  updated_at INTEGER NOT NULL DEFAULT (unixepoch('now','subsec') * 1000)
);

CREATE TABLE IF NOT EXISTS tasks (
  id TEXT PRIMARY KEY,
  emergency_id TEXT NOT NULL,
  title TEXT NOT NULL,
  location TEXT NOT NULL DEFAULT '',
  assigned_team_id TEXT,
  status TEXT NOT NULL DEFAULT 'PENDING',
  created_at INTEGER NOT NULL DEFAULT (unixepoch('now','subsec') * 1000),
  started_at INTEGER,
  completed_at INTEGER,
  image_uris TEXT,
  client_id TEXT
);

CREATE TABLE IF NOT EXISTS task_events (
  id TEXT PRIMARY KEY,
  task_id TEXT NOT NULL,
  action TEXT NOT NULL,
  actor_id TEXT NOT NULL DEFAULT '',
  actor_role TEXT NOT NULL DEFAULT '',
  details TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL DEFAULT (unixepoch('now','subsec') * 1000)
);

CREATE TABLE IF NOT EXISTS feedback (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  task_id TEXT NOT NULL,
  rescue_team_id TEXT NOT NULL,
  rating INTEGER NOT NULL,
  comment TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL DEFAULT (unixepoch('now','subsec') * 1000)
);

CREATE TABLE IF NOT EXISTS lora_devices (
  device_id TEXT PRIMARY KEY,
  label TEXT NOT NULL DEFAULT '',
  team_id TEXT,
  registered_by TEXT,
  last_seen_at INTEGER,
  created_at INTEGER NOT NULL DEFAULT (unixepoch('now','subsec') * 1000)
);

CREATE TABLE IF NOT EXISTS lora_packets (
  device_id TEXT NOT NULL,
  sequence INTEGER NOT NULL,
  type TEXT NOT NULL,
  received_at INTEGER NOT NULL,
  PRIMARY KEY (device_id, sequence)
);

CREATE TABLE IF NOT EXISTS otp_codes (
  phone TEXT PRIMARY KEY,
  otp_hash TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  last_sent_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS email_otp_codes (
  email TEXT NOT NULL,
  role TEXT NOT NULL CHECK(role IN ('CITIZEN','RESCUE','AUTHORITY')),
  otp_hash TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0,
  last_sent_at INTEGER NOT NULL,
  PRIMARY KEY (email, role)
);

CREATE TABLE IF NOT EXISTS refresh_sessions (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  token_hash TEXT UNIQUE NOT NULL,
  expires_at INTEGER NOT NULL,
  revoked_at INTEGER,
  created_at INTEGER NOT NULL DEFAULT (unixepoch('now','subsec') * 1000),
  last_used_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_emergencies_citizen_created ON emergencies(citizen_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_emergencies_status_created ON emergencies(status, created_at DESC);
CREATE UNIQUE INDEX IF NOT EXISTS idx_emergencies_client_id ON emergencies(client_id) WHERE client_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_tasks_emergency ON tasks(emergency_id);
CREATE INDEX IF NOT EXISTS idx_tasks_team_status ON tasks(assigned_team_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS idx_tasks_client_id ON tasks(client_id) WHERE client_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_task_events_task_created ON task_events(task_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_lora_devices_team ON lora_devices(team_id);
CREATE INDEX IF NOT EXISTS idx_lora_packets_received ON lora_packets(received_at DESC);
CREATE INDEX IF NOT EXISTS idx_feedback_task ON feedback(task_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_feedback_user_task ON feedback(user_id, task_id);
CREATE INDEX IF NOT EXISTS idx_refresh_sessions_user ON refresh_sessions(user_id);
`);

const AUTHORITY_RANKS = [
  'VAO',
  'TAHSILDAR',
  'DISTRICT_COLLECTOR',
  'STATE_DISASTER_MANAGEMENT_AUTHORITY',
  'CM'
];

const MIN_RANK_TO_MANAGE_TEAMS = 'DISTRICT_COLLECTOR';
const MIN_RANK_TO_ISSUE_INVITES = 'DISTRICT_COLLECTOR';

function rankIndex(authorityType) {
  return AUTHORITY_RANKS.indexOf(authorityType);
}

module.exports = {
  db,
  DB_PATH,
  AUTHORITY_RANKS,
  rankIndex,
  MIN_RANK_TO_MANAGE_TEAMS,
  MIN_RANK_TO_ISSUE_INVITES
};
