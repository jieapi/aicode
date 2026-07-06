ALTER TABLE agent_messages ADD COLUMN isContextSummary INTEGER NOT NULL DEFAULT 0;

UPDATE agent_messages
SET isContextSummary = 1
WHERE role = 'ASSISTANT'
  AND content LIKE '【系统提示：早期的对话已被压缩%';
