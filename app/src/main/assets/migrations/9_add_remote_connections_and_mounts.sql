DROP TABLE IF EXISTS `remote_servers`;

CREATE TABLE IF NOT EXISTS `remote_connections` (
    `id` TEXT NOT NULL,
    `name` TEXT NOT NULL,
    `protocol` TEXT NOT NULL,
    `host` TEXT NOT NULL,
    `port` INTEGER NOT NULL,
    `username` TEXT NOT NULL,
    `authType` TEXT NOT NULL,
    `authData` TEXT NOT NULL,
    `passphrase` TEXT,
    PRIMARY KEY(`id`)
);

CREATE TABLE IF NOT EXISTS `remote_mounts` (
    `id` TEXT NOT NULL,
    `connectionId` TEXT NOT NULL,
    `remotePath` TEXT NOT NULL,
    `localMountPath` TEXT NOT NULL,
    `isActive` INTEGER NOT NULL,
    PRIMARY KEY(`id`),
    FOREIGN KEY(`connectionId`) REFERENCES `remote_connections`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS `index_remote_mounts_connectionId` ON `remote_mounts` (`connectionId`);
