-- AI 知识库补充 keywords 列（RAG 检索辅助）
-- 若列已存在会报错，可忽略
ALTER TABLE ai_knowledge
  ADD COLUMN keywords VARCHAR(512) DEFAULT NULL COMMENT '检索关键词，逗号分隔' AFTER answer;
