CREATE TABLE ai_providers_new (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    apiKey TEXT NOT NULL,
    baseUrl TEXT NOT NULL,
    defaultModel TEXT NOT NULL,
    isActive INTEGER NOT NULL,
    models TEXT NOT NULL,
    selectedModel TEXT NOT NULL,
    isEnabled INTEGER NOT NULL,
    useFullUrl INTEGER NOT NULL DEFAULT 0,
    useResponseApi INTEGER NOT NULL
);

INSERT INTO ai_providers_new (id, name, type, apiKey, baseUrl, defaultModel, isActive, models, selectedModel, isEnabled, useFullUrl, useResponseApi)
SELECT id, name, type, apiKey, baseUrl, defaultModel, isActive, models, selectedModel, isEnabled, useFullUrl, useResponseApi
FROM ai_providers;

DROP TABLE ai_providers;
ALTER TABLE ai_providers_new RENAME TO ai_providers;
