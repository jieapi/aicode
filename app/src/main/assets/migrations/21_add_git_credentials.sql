CREATE TABLE IF NOT EXISTS git_credentials (
    id TEXT NOT NULL PRIMARY KEY,
    host TEXT NOT NULL,
    username TEXT NOT NULL,
    token TEXT NOT NULL,
    label TEXT NOT NULL DEFAULT '',
    isDefault INTEGER NOT NULL DEFAULT 0,
    createdAt INTEGER NOT NULL,
    updatedAt INTEGER NOT NULL
);
