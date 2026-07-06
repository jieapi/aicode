ALTER TABLE agent_messages ADD COLUMN isCompactionMarker INTEGER NOT NULL DEFAULT 0;

INSERT OR IGNORE INTO agent_messages (
  id,
  sessionId,
  role,
  content,
  timestamp,
  toolCallsJson,
  toolCallId,
  toolName,
  toolArgs,
  isError,
  reasoning,
  isCompacted,
  isContextSummary,
  isCompactionMarker
)
SELECT
  id || '_compaction_marker',
  sessionId,
  'USER',
  'What did we do so far?',
  timestamp - 1,
  NULL,
  NULL,
  NULL,
  NULL,
  0,
  NULL,
  0,
  0,
  1
FROM agent_messages
WHERE isContextSummary = 1
  AND isCompacted = 0;
