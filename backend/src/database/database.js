const Database = require("better-sqlite3");
const path = require("path");
const fs = require("fs");

const databasePath = path.resolve(
    process.cwd(),
    process.env.DATABASE_PATH || "./data/resqmesh.db"
);

fs.mkdirSync(path.dirname(databasePath), { recursive: true });

const db = new Database(databasePath);

db.pragma("journal_mode = WAL");
db.pragma("foreign_keys = ON");

db.exec(`
    CREATE TABLE IF NOT EXISTS incidents (
        id TEXT PRIMARY KEY,
        sender_id TEXT,
        sender_name TEXT,
        title TEXT NOT NULL,
        content TEXT NOT NULL,
        priority TEXT NOT NULL,
        latitude REAL,
        longitude REAL,
        accuracy REAL,
        occurred_at TEXT,
        received_at TEXT NOT NULL,
        status TEXT NOT NULL DEFAULT 'RECEIVED',
        payload_json TEXT
    );

    CREATE INDEX IF NOT EXISTS idx_incidents_received_at
    ON incidents(received_at);

    CREATE INDEX IF NOT EXISTS idx_incidents_priority
    ON incidents(priority);
`);

console.log("ResQMesh SQLite database initialized.");

module.exports = db;
