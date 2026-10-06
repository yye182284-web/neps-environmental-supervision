-- NEPS 四类角色及 AQI 任务处理流程所需的最小扩展。
-- 本脚本只新增账号表和演示账号，不删除已有业务数据。

CREATE TABLE IF NOT EXISTS system_account (
    account_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '系统账号编号',
    username VARCHAR(40) NOT NULL COMMENT '登录名',
    password VARCHAR(100) NOT NULL COMMENT '登录密码',
    real_name VARCHAR(50) NOT NULL COMMENT '姓名',
    role VARCHAR(20) NOT NULL COMMENT '角色：ADMIN、DECISION',
    state TINYINT NOT NULL DEFAULT 0 COMMENT '0正常，1停用',
    remarks VARCHAR(200) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (account_id),
    UNIQUE KEY uk_system_account_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='系统管理员和决策者账号表';

CREATE TABLE IF NOT EXISTS login_audit (
    audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '登录日志编号',
    account_type VARCHAR(20) NOT NULL COMMENT 'PUBLIC、GRID_MEMBER、ADMIN、DECISION',
    account_name VARCHAR(40) NOT NULL COMMENT '登录账号',
    real_name VARCHAR(50) DEFAULT NULL COMMENT '姓名',
    success TINYINT(1) NOT NULL COMMENT '1成功，0失败',
    client_ip VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
    login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    message VARCHAR(100) DEFAULT NULL COMMENT '登录结果',
    PRIMARY KEY (audit_id),
    KEY idx_login_audit_time (login_time),
    KEY idx_login_audit_account (account_type, account_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='系统登录审计日志';

INSERT INTO system_account (username, password, real_name, role, state, remarks)
VALUES
    ('admin', '123456', '系统管理员', 'ADMIN', 0, '课程演示账号'),
    ('decision', '123456', '环境决策者', 'DECISION', 0, '课程演示账号')
ON DUPLICATE KEY UPDATE
    real_name = VALUES(real_name), role = VALUES(role), state = VALUES(state);

-- Selenium 端到端测试使用的公众监督员账号。
INSERT INTO supervisor (tel_id, password, real_name, age, birthday, sex, remarks)
VALUES ('13800000000', '123456', '自动化测试员', 20, '2006-01-01', 1, 'Selenium 课程测试账号')
ON DUPLICATE KEY UPDATE
    real_name = VALUES(real_name), remarks = VALUES(remarks);

INSERT INTO grid_member
    (gm_id, gm_name, gm_code, password, province_id, city_id, tel, state, remarks)
VALUES
    ('1001', '张网格', 'GRID001', '123456', 1, 1, '13800001001', 0, '河北省石家庄市演示账号'),
    ('1002', '李保定', 'GRID002', '123456', 1, 2, '13800001002', 0, '河北省保定市演示账号'),
    ('1003', '魏沧州', 'GRID003', '123456', 1, 3, '13800001003', 0, '河北省沧州市演示账号'),
    ('1004', '王北京', 'GRID004', '123456', 2, 35, '13800001004', 0, '北京市演示账号'),
    ('1005', '赵天津', 'GRID005', '123456', 3, 36, '13800001005', 0, '天津市演示账号'),
    ('1006', '陈南京', 'GRID006', '123456', 17, 3201, '13800001006', 0, '江苏省南京市演示账号'),
    ('1007', '周杭州', 'GRID007', '123456', 18, 3301, '13800001007', 0, '浙江省杭州市演示账号'),
    ('1008', '刘青岛', 'GRID008', '123456', 22, 3702, '13800001008', 0, '山东省青岛市演示账号'),
    ('1009', '林深圳', 'GRID009', '123456', 26, 4403, '13800001009', 0, '广东省深圳市演示账号'),
    ('1010', '孙上海', 'GRID010', '123456', 11, 3101, '13800001010', 0, '上海市演示账号')
ON DUPLICATE KEY UPDATE
    gm_name = VALUES(gm_name), gm_code = VALUES(gm_code), province_id = VALUES(province_id),
    city_id = VALUES(city_id), tel = VALUES(tel), state = VALUES(state), remarks = VALUES(remarks);

-- 修复旧版本按 Windows 默认编码初始化后，审计日志中已经保存的乱码姓名。
UPDATE login_audit a
JOIN system_account s ON a.account_type IN ('ADMIN','DECISION') AND a.account_name = s.username
SET a.real_name = s.real_name
WHERE a.real_name IS NOT NULL;

UPDATE login_audit a
JOIN grid_member g ON a.account_type = 'GRID_MEMBER'
    AND a.account_name IN (g.gm_id, g.gm_code, g.tel)
SET a.real_name = g.gm_name
WHERE a.real_name IS NOT NULL;

UPDATE login_audit a
JOIN supervisor s ON a.account_type = 'PUBLIC' AND a.account_name = s.tel_id
SET a.real_name = s.real_name
WHERE a.real_name IS NOT NULL;

-- 新流程中网格员提交即为最终结果：兼容升级旧版本遗留的“待管理员确认”记录。
UPDATE aqi_measurement
SET record_status = 'CONFIRMED', confirmed_at = COALESCE(confirmed_at, submitted_at, measured_at)
WHERE record_status = 'SUBMITTED';

UPDATE aqi_feedback f
JOIN aqi_measurement m ON m.task_id = f.af_id AND m.record_status = 'CONFIRMED'
SET f.state = 3
WHERE f.state <> 3;

-- 多区域演示数据：覆盖不同省市、月份和 AQI 等级，保证统计看板具备可解释的非零数据。
INSERT INTO aqi_feedback
    (af_id, tel_id, province_id, city_id, address, information, estimated_grade,
     af_date, af_time, gm_id, assign_date, assign_time, state, remarks)
VALUES
    (910001,'13800000000',1,1,'石家庄市长安区建设北大街','道路施工产生扬尘，能见度轻微下降',3,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 1 MONTH),'%Y-%m-%d'),'09:20:00',1001,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 1 MONTH),'%Y-%m-%d'),'10:00:00',3,'多区域统计演示'),
    (910002,'13800000000',1,2,'保定市竞秀区朝阳大街','早高峰空气有轻微异味',2,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 2 MONTH),'%Y-%m-%d'),'08:30:00',1002,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 2 MONTH),'%Y-%m-%d'),'09:00:00',3,'多区域统计演示'),
    (910003,'13800000000',1,3,'沧州市运河区解放西路','园区附近可见烟尘并伴随异味',4,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 3 MONTH),'%Y-%m-%d'),'14:10:00',1003,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 3 MONTH),'%Y-%m-%d'),'14:40:00',3,'多区域统计演示'),
    (910004,'13800000000',2,35,'北京市朝阳区东三环','交通流量较大，空气质量一般',2,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 4 MONTH),'%Y-%m-%d'),'17:30:00',1004,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 4 MONTH),'%Y-%m-%d'),'18:00:00',3,'多区域统计演示'),
    (910005,'13800000000',3,36,'天津市滨海新区新港路','港区附近出现明显灰霾',4,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 5 MONTH),'%Y-%m-%d'),'11:20:00',1005,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 5 MONTH),'%Y-%m-%d'),'11:50:00',3,'多区域统计演示'),
    (910006,'13800000000',17,3201,'南京市建邺区江东中路','空气清新，无明显污染现象',1,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 6 MONTH),'%Y-%m-%d'),'10:15:00',1006,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 6 MONTH),'%Y-%m-%d'),'10:40:00',3,'多区域统计演示'),
    (910007,'13800000000',18,3301,'杭州市西湖区文三路','局部道路有少量扬尘',2,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 7 MONTH),'%Y-%m-%d'),'13:25:00',1007,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 7 MONTH),'%Y-%m-%d'),'14:00:00',3,'多区域统计演示'),
    (910008,'13800000000',22,3702,'青岛市市南区香港中路','海风条件良好，空气质量优良',1,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 8 MONTH),'%Y-%m-%d'),'15:40:00',1008,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 8 MONTH),'%Y-%m-%d'),'16:10:00',3,'多区域统计演示'),
    (910009,'13800000000',26,4403,'深圳市南山区科技园','午后有轻度灰霾现象',3,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 9 MONTH),'%Y-%m-%d'),'16:20:00',1009,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 9 MONTH),'%Y-%m-%d'),'16:45:00',3,'多区域统计演示'),
    (910010,'13800000000',11,3101,'上海市浦东新区张江路','夜间出现刺鼻气味和浓烟',5,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 10 MONTH),'%Y-%m-%d'),'20:10:00',1010,DATE_FORMAT(DATE_SUB(CURDATE(),INTERVAL 10 MONTH),'%Y-%m-%d'),'20:35:00',3,'多区域统计演示')
ON DUPLICATE KEY UPDATE address=VALUES(address), information=VALUES(information),
    estimated_grade=VALUES(estimated_grade), gm_id=VALUES(gm_id), state=3, remarks=VALUES(remarks);

INSERT INTO aqi_measurement
    (measurement_id, task_id, grid_code, province_code, city_code, detail_address, inspector_id,
     measured_at, so2_concentration, co_concentration, pm25_concentration,
     so2_level_id, co_level_id, pm25_level_id, record_status, submitted_at, confirmed_at, remark)
VALUES
    (920001,910001,'GRID001','000001','000001','石家庄市长安区建设北大街',1001,DATE_SUB(NOW(),INTERVAL 1 MONTH),180,6.5,118,3,3,3,'CONFIRMED',DATE_SUB(NOW(),INTERVAL 1 MONTH),DATE_SUB(NOW(),INTERVAL 1 MONTH),'施工扬尘点位复测'),
    (920002,910002,'GRID002','000001','000002','保定市竞秀区朝阳大街',1002,DATE_SUB(NOW(),INTERVAL 2 MONTH),82,3.8,62,2,2,2,'CONFIRMED',DATE_SUB(NOW(),INTERVAL 2 MONTH),DATE_SUB(NOW(),INTERVAL 2 MONTH),'交通点位常规检测'),
    (920003,910003,'GRID003','000001','000003','沧州市运河区解放西路',1003,DATE_SUB(NOW(),INTERVAL 3 MONTH),420,17,190,4,4,4,'CONFIRMED',DATE_SUB(NOW(),INTERVAL 3 MONTH),DATE_SUB(NOW(),INTERVAL 3 MONTH),'园区下风向检测'),
    (920004,910004,'GRID004','000002','000035','北京市朝阳区东三环',1004,DATE_SUB(NOW(),INTERVAL 4 MONTH),70,3.2,58,2,2,2,'CONFIRMED',DATE_SUB(NOW(),INTERVAL 4 MONTH),DATE_SUB(NOW(),INTERVAL 4 MONTH),'交通高峰检测'),
    (920005,910005,'GRID005','000003','000036','天津市滨海新区新港路',1005,DATE_SUB(NOW(),INTERVAL 5 MONTH),390,16,175,4,4,4,'CONFIRMED',DATE_SUB(NOW(),INTERVAL 5 MONTH),DATE_SUB(NOW(),INTERVAL 5 MONTH),'港区灰霾监测'),
    (920006,910006,'GRID006','000017','003201','南京市建邺区江东中路',1006,DATE_SUB(NOW(),INTERVAL 6 MONTH),32,1.8,28,1,1,1,'CONFIRMED',DATE_SUB(NOW(),INTERVAL 6 MONTH),DATE_SUB(NOW(),INTERVAL 6 MONTH),'背景点位检测'),
    (920007,910007,'GRID007','000018','003301','杭州市西湖区文三路',1007,DATE_SUB(NOW(),INTERVAL 7 MONTH),76,3.5,64,2,2,2,'CONFIRMED',DATE_SUB(NOW(),INTERVAL 7 MONTH),DATE_SUB(NOW(),INTERVAL 7 MONTH),'道路扬尘检测'),
    (920008,910008,'GRID008','000022','003702','青岛市市南区香港中路',1008,DATE_SUB(NOW(),INTERVAL 8 MONTH),28,1.5,22,1,1,1,'CONFIRMED',DATE_SUB(NOW(),INTERVAL 8 MONTH),DATE_SUB(NOW(),INTERVAL 8 MONTH),'沿海背景检测'),
    (920009,910009,'GRID009','000026','004403','深圳市南山区科技园',1009,DATE_SUB(NOW(),INTERVAL 9 MONTH),165,7.2,105,3,3,3,'CONFIRMED',DATE_SUB(NOW(),INTERVAL 9 MONTH),DATE_SUB(NOW(),INTERVAL 9 MONTH),'灰霾点位检测'),
    (920010,910010,'GRID010','000011','003101','上海市浦东新区张江路',1010,DATE_SUB(NOW(),INTERVAL 10 MONTH),760,27,275,5,5,5,'CONFIRMED',DATE_SUB(NOW(),INTERVAL 10 MONTH),DATE_SUB(NOW(),INTERVAL 10 MONTH),'异常排放点复测')
ON DUPLICATE KEY UPDATE detail_address=VALUES(detail_address), measured_at=VALUES(measured_at),
    so2_concentration=VALUES(so2_concentration), co_concentration=VALUES(co_concentration),
    pm25_concentration=VALUES(pm25_concentration), so2_level_id=VALUES(so2_level_id),
    co_level_id=VALUES(co_level_id), pm25_level_id=VALUES(pm25_level_id),
    record_status='CONFIRMED', confirmed_at=VALUES(confirmed_at), remark=VALUES(remark);
