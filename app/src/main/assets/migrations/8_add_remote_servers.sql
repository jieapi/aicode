CREATE TABLE IF NOT EXISTS `remote_servers` (
    `id` TEXT NOT NULL,
    `name` TEXT NOT NULL,
    `protocol` TEXT NOT NULL,
    `host` TEXT NOT NULL,
    `port` INTEGER NOT NULL,
    `username` TEXT NOT NULL,
    `authType` TEXT NOT NULL,
    `authData` TEXT NOT NULL,
    `passphrase` TEXT,
    `remotePath` TEXT NOT NULL,
    `localMountPath` TEXT NOT NULL,
    PRIMARY KEY(`id`)
);
