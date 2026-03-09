-- Repair the known mojibake value created by an older local import path.
-- The update is deliberately scoped to the seeded provider and the exact
-- corrupted value so operator-renamed providers are never overwritten.
UPDATE `ai_client_api`
SET `provider_name` = '默认 OpenAI 兼容 Provider',
    `update_time` = CURRENT_TIMESTAMP
WHERE `api_id` = '1001'
  AND `provider_name` = 'é»˜è®¤ OpenAI å…¼å®¹ Provider';
