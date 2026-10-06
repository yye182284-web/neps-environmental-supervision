
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `aqi` (
  `aqi_id` tinyint unsigned NOT NULL AUTO_INCREMENT COMMENT '空气质量指数等级编号，共六级',
  `chinese_explain` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '空气质量指数级别汉字表述，如一级',
  `aqi_explain` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '空气质量类别说明，如优、良、轻度污染',
  `color` char(7) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '等级显示颜色的十六进制值',
  `health_impact` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对健康的影响情况',
  `take_steps` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '建议采取的防护措施',
  `so2_min` smallint unsigned NOT NULL COMMENT 'SO2平均浓度最小值，单位ug/m3',
  `so2_max` smallint unsigned DEFAULT NULL COMMENT 'SO2平均浓度最大值，NULL表示无上限',
  `co_min` smallint unsigned NOT NULL COMMENT 'CO平均浓度最小值，单位ug/m3',
  `co_max` smallint unsigned DEFAULT NULL COMMENT 'CO平均浓度最大值',
  `spm_min` smallint unsigned NOT NULL COMMENT 'PM2.5平均浓度最小值，单位ug/m3',
  `spm_max` smallint unsigned DEFAULT NULL COMMENT 'PM2.5平均浓度最大值',
  `remarks` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`aqi_id`),
  UNIQUE KEY `uk_aqi_chinese_explain` (`chinese_explain`),
  UNIQUE KEY `uk_aqi_explain` (`aqi_explain`),
  CONSTRAINT `ck_aqi_co_range` CHECK (((`co_max` is null) or (`co_min` <= `co_max`))),
  CONSTRAINT `ck_aqi_so2_range` CHECK (((`so2_max` is null) or (`so2_min` <= `so2_max`))),
  CONSTRAINT `ck_aqi_spm_range` CHECK (((`spm_max` is null) or (`spm_min` <= `spm_max`)))
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='空气质量指数等级及污染物浓度范围表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `aqi_feedback` (
  `af_id` int NOT NULL AUTO_INCREMENT COMMENT '空气质量公众监督反馈编号',
  `tel_id` varchar(20) NOT NULL COMMENT '公众监督员手机号码',
  `province_id` int NOT NULL COMMENT '省区域编号',
  `city_id` int NOT NULL COMMENT '市区域编号',
  `address` varchar(200) NOT NULL COMMENT '详细地址',
  `information` varchar(500) NOT NULL COMMENT '空气质量反馈描述',
  `estimated_grade` tinyint NOT NULL COMMENT '预估AQI等级：1~6',
  `af_date` varchar(10) NOT NULL COMMENT '反馈日期',
  `af_time` varchar(8) NOT NULL COMMENT '反馈时间',
  `gm_id` int DEFAULT NULL COMMENT '指派网格员编号',
  `assign_date` varchar(10) DEFAULT NULL COMMENT '指派日期',
  `assign_time` varchar(8) DEFAULT NULL COMMENT '指派时间',
  `state` tinyint NOT NULL DEFAULT '0' COMMENT '信息状态：0未指派，1已指派，2处理中，3已处理',
  `remarks` varchar(255) DEFAULT NULL COMMENT '备注',
  `accepted_at` datetime DEFAULT NULL COMMENT '网格员接单时间',
  `accept_deadline` datetime DEFAULT NULL COMMENT '接单截止时间',
  PRIMARY KEY (`af_id`),
  KEY `idx_feedback_tel` (`tel_id`),
  KEY `idx_feedback_region_state` (`province_id`,`city_id`,`state`),
  KEY `idx_feedback_date` (`af_date`),
  KEY `idx_feedback_region_date` (`province_id`,`city_id`,`af_date`),
  KEY `idx_feedback_member_state` (`gm_id`,`state`)
) ENGINE=InnoDB AUTO_INCREMENT=200013621 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='公众空气质量监督反馈表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `aqi_measurement` (
  `measurement_id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `task_id` bigint unsigned NOT NULL COMMENT '对应公众监督反馈任务编号',
  `grid_code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检测网格编码',
  `province_code` char(6) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '省级行政区划代码',
  `city_code` char(6) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '城市行政区划代码',
  `detail_address` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '实际检测地址',
  `inspector_id` bigint unsigned NOT NULL COMMENT 'AQI 检测网格员编号',
  `measured_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '检测时间',
  `so2_concentration` decimal(10,2) NOT NULL COMMENT 'SO2 平均浓度，ug/m3',
  `co_concentration` decimal(10,2) NOT NULL COMMENT 'CO 平均浓度，mg/m3',
  `pm25_concentration` decimal(10,2) NOT NULL COMMENT 'PM2.5 平均浓度，ug/m3',
  `so2_level_id` tinyint unsigned NOT NULL COMMENT 'SO2 对应等级',
  `co_level_id` tinyint unsigned NOT NULL COMMENT 'CO 对应等级',
  `pm25_level_id` tinyint unsigned NOT NULL COMMENT 'PM2.5 对应等级',
  `overall_level_id` tinyint unsigned GENERATED ALWAYS AS (greatest(`so2_level_id`,`co_level_id`,`pm25_level_id`)) STORED COMMENT '总体 AQI 等级，取三个污染物等级最大值',
  `is_exceeded` tinyint(1) GENERATED ALWAYS AS ((case when (greatest(`so2_level_id`,`co_level_id`,`pm25_level_id`) >= 3) then 1 else 0 end)) STORED COMMENT '0=优良，1=轻度污染及以上',
  `record_status` enum('DRAFT','SUBMITTED','CONFIRMED') COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUBMITTED' COMMENT '草稿/已提交/已确认',
  `submitted_at` datetime DEFAULT NULL,
  `confirmed_by` bigint unsigned DEFAULT NULL COMMENT '确认数据的系统管理员编号',
  `confirmed_at` datetime DEFAULT NULL,
  `remark` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`measurement_id`),
  UNIQUE KEY `uk_measurement_task` (`task_id`),
  KEY `idx_measurement_region_time` (`province_code`,`city_code`,`measured_at`),
  KEY `idx_measurement_level_time` (`overall_level_id`,`measured_at`),
  KEY `idx_measurement_status` (`record_status`),
  KEY `idx_measurement_inspector` (`inspector_id`,`measured_at`),
  KEY `fk_measurement_so2_level` (`so2_level_id`),
  KEY `fk_measurement_co_level` (`co_level_id`),
  KEY `fk_measurement_pm25_level` (`pm25_level_id`),
  KEY `idx_measurement_status_date` (`record_status`,`measured_at`),
  KEY `idx_measurement_region_date` (`province_code`,`city_code`,`measured_at`),
  CONSTRAINT `ck_measurement_concentrations` CHECK (((`so2_concentration` >= 0) and (`co_concentration` >= 0) and (`pm25_concentration` >= 0))),
  CONSTRAINT `ck_measurement_levels` CHECK (((`so2_level_id` between 1 and 6) and (`co_level_id` between 1 and 6) and (`pm25_level_id` between 1 and 6)))
) ENGINE=InnoDB AUTO_INCREMENT=800013614 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网格员提交的 AQI 实测及管理员确认记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `grid_city` (
  `city_id` int NOT NULL AUTO_INCREMENT COMMENT '系统网格覆盖市区域编号',
  `city_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系统网格覆盖市区域名称',
  `province_id` int NOT NULL COMMENT '所属省区域编号',
  `remarks` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`city_id`) USING BTREE,
  KEY `fk_city_province_id` (`province_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=8201 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `grid_member` (
  `gm_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '网格员编号',
  `gm_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '网格员姓名',
  `gm_code` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录编码',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '登录密码',
  `province_id` int DEFAULT NULL COMMENT '省编号',
  `city_id` int DEFAULT NULL COMMENT '市编号',
  `tel` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
  `state` tinyint NOT NULL DEFAULT '0' COMMENT '状态：0可工作，1临时抽调，2休假，3其他',
  `remarks` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`gm_id`),
  KEY `idx_grid_member_region` (`province_id`,`city_id`),
  KEY `idx_grid_member_state` (`state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网格员表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `grid_province` (
  `province_id` int NOT NULL AUTO_INCREMENT COMMENT '系统网格覆盖省区域编号',
  `province_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系统网格覆盖省区域名称',
  `province_abbr` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '系统网格覆盖省区域简称',
  `remarks` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`province_id`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=41 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE IF NOT EXISTS `supervisor` (
  `tel_id` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '公众监督员手机号',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录密码',
  `real_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '真实姓名',
  `age` tinyint unsigned DEFAULT NULL COMMENT '年龄（注册时填写）',
  `birthday` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '出生日期',
  `sex` tinyint DEFAULT NULL COMMENT '性别：1男，0女',
  `remarks` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `province_id` int DEFAULT NULL COMMENT '常驻省份编号',
  `city_id` int DEFAULT NULL COMMENT '常驻城市编号',
  PRIMARY KEY (`tel_id`),
  KEY `idx_supervisor_region` (`province_id`,`city_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公众监督员表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


