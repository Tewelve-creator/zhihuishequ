-- 更新访客制度话术（免审核）+ 补充制度问答样例
USE smart_community;
SET NAMES utf8mb4;

UPDATE ai_knowledge
SET answer = '临时访客可在 App 智能管家或「访客预约」提交，填写姓名后直接生效（状态已通过），无需物业审核；物业仅做线下核销。驾车来访需填写车牌。'
WHERE tenant_id = 1 AND question = '访客预约';

INSERT INTO ai_knowledge (tenant_id, category, question, answer, hit_count, status, create_time)
SELECT 1, 'visit', '怎么登记访客', '直接对我说「帮我登记访客」，按提示补充姓名、是否驾车、到访时间即可；信息确认后才会创建预约。', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='怎么登记访客');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, hit_count, status, create_time)
SELECT 1, 'repair', '怎么报修', '可以说「我要报修」并描述问题，确认后会为您创建报修工单；也可在服务里拍照报修。', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='怎么报修');
