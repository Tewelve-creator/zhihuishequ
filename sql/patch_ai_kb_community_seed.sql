-- 小区知识库样例：公告 / 规章 / 收费 / 制度（租户 1）
USE smart_community;
SET NAMES utf8mb4;

INSERT INTO ai_knowledge (tenant_id, category, question, answer, keywords, hit_count, status, create_time)
SELECT 1, 'notice', '小区最新公告',
'【阳光花园社区公告】本周物业值班电话：400-800-2026；电梯维保定于每周三上午；快递柜位于1号门东侧。重大通知请关注业主群与公告栏。',
'公告,通知,值班,电梯,快递柜', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='小区最新公告');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, keywords, hit_count, status, create_time)
SELECT 1, 'notice', '垃圾分类怎么放',
'本小区实行垃圾分类：可回收物投蓝色桶，厨余投绿色桶，有害垃圾投红色桶，其他垃圾投灰色桶。投放时间为每天 7:00–21:00，请勿在楼道堆放杂物。',
'垃圾分类,垃圾桶,投放', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='垃圾分类怎么放');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, keywords, hit_count, status, create_time)
SELECT 1, 'policy', '物业规章制度',
'业主应遵守《阳光花园物业管理规约》：禁止占用消防通道与公共绿地；装修需提前到物业登记并缴纳装修押金；宠物出门须牵绳并清理粪便；夜间 22:00 后请控制噪音。',
'规章,制度,规约,装修,宠物,噪音', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='物业规章制度');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, keywords, hit_count, status, create_time)
SELECT 1, 'policy', '停车管理规定',
'固定车位业主凭备案车牌出入；访客临时停车请在 App 登记访客并填写车牌，临停免费时长 2 小时，超时按 5 元/小时计费（以岗亭公示为准）。地下车库限速 5km/h。',
'停车,车牌,临停,车位,车库', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='停车管理规定');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, keywords, hit_count, status, create_time)
SELECT 1, 'fee', '物业费收费标准',
'住宅物业费：2.80 元/㎡·月（含公共区域保洁、绿化、秩序维护、共用设施日常维护）。车库管理费另计 50 元/月·位。账单按自然月出具，可通过 App「缴费管理」在线缴纳。',
'物业费,收费标准,单价,缴费', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='物业费收费标准');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, keywords, hit_count, status, create_time)
SELECT 1, 'fee', '水电费怎么交',
'公区公摊水电费随物业费一并出账；户内水电由供水供电公司直收。欠费可在 App 查账单，逾期超过 30 天物业将短信提醒，长期欠费可能影响部分便民服务办理。',
'水电费,公摊,欠费,账单', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='水电费怎么交');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, keywords, hit_count, status, create_time)
SELECT 1, 'policy', '装修管理规定',
'装修须提前 3 个工作日到物业办理登记，提交装修方案与施工人员名单；施工时间工作日 8:00–12:00、14:00–18:00，法定节假日及周日禁止噪音施工；竣工后物业验收后退还装修押金。',
'装修,押金,施工,登记', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='装修管理规定');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, keywords, hit_count, status, create_time)
SELECT 1, 'policy', '消防安全规定',
'楼道、配电井、消防通道严禁堆放杂物与电动车；电动车禁止入户充电，请使用指定充电车棚；发现消防隐患可拨打物业值班电话或在 App 报事报修。',
'消防,电动车,充电,通道', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='消防安全规定');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, keywords, hit_count, status, create_time)
SELECT 1, 'community', '社区活动怎么报名',
'社区文体活动在 App「社区活动」查看与报名，名额满即止；线下也可到物业服务中心登记。活动期间请服从现场秩序，儿童需监护人陪同。',
'活动,报名,社区活动', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='社区活动怎么报名');

INSERT INTO ai_knowledge (tenant_id, category, question, answer, keywords, hit_count, status, create_time)
SELECT 1, 'community', '失物招领在哪里',
'失物招领信息可在 App「失物招领」发布与查看；实物暂存在物业服务中心失物柜，认领时请出示身份证明并登记。贵重物品请尽快联系物业。',
'失物,招领,捡到,丢失', 0, 1, NOW()
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM ai_knowledge WHERE tenant_id=1 AND question='失物招领在哪里');

-- 兼容旧问法：更新物业费条目话术
UPDATE ai_knowledge
SET answer = '本小区住宅物业费标准为 2.80 元/㎡·月，可通过 App「缴费管理」查询与缴纳；详情也可问我「物业费收费标准」。',
    keywords = COALESCE(keywords, '物业费,交费')
WHERE tenant_id = 1 AND question = '物业费';
