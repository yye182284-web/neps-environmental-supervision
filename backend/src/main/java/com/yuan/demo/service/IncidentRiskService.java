package com.yuan.demo.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Explainable, deterministic first-pass triage used before a grid member performs a real measurement. */
@Service
public class IncidentRiskService {

    public Map<String, Object> assess(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) throw new IllegalArgumentException("请先输入现场情况");
        String text = rawText.trim();
        if (text.length() > 1000) throw new IllegalArgumentException("现场描述不能超过 1000 个字符");

        int score = 8;
        List<String> triggers = new ArrayList<>();
        score += match(text, triggers, 36, "人员健康风险", "呼吸困难", "胸闷", "晕倒", "眼睛刺痛");
        score += match(text, triggers, 30, "持续或大量排放", "大量黑烟", "持续排放", "浓烟", "看不清");
        score += match(text, triggers, 22, "刺激性气味", "刺鼻", "恶臭", "明显异味", "臭味");
        score += match(text, triggers, 18, "颗粒物扩散", "扬尘", "烟尘", "粉尘", "灰霾");
        score += match(text, triggers, 15, "敏感场所", "学校", "幼儿园", "医院", "养老院", "居民区");
        score += match(text, triggers, 10, "可能存在异常时段排放", "夜间", "凌晨", "半夜", "偷排");
        score = Math.min(score, 100);

        String level = score >= 76 ? "紧急" : score >= 51 ? "高" : score >= 26 ? "中" : "常规";
        int estimatedGrade = score >= 76 ? 6 : score >= 56 ? 5 : score >= 38 ? 4 : score >= 22 ? 3 : score >= 13 ? 2 : 1;
        boolean detailedAddress = text.matches(".*(路|街|道|巷|村|镇|乡|区|县|号|园|厂|小区|大厦|广场).*?");
        int confidence = Math.min(96, 48 + triggers.size() * 8 + (detailedAddress ? 12 : 0));

        List<String> actions = new ArrayList<>();
        if (score >= 76) {
            actions.add("优先确保人员安全，远离污染源并联系属地应急或环保部门");
            actions.add("按紧急事件派发最近的属地网格员，并保留现场时间线");
        } else if (score >= 51) {
            actions.add("建议 30 分钟内完成属地派单并携带个人防护装备");
            actions.add("同步核验上风向、下风向和潜在排放口");
        } else {
            actions.add("按常规流程提交反馈并由属地网格员现场核实");
        }
        actions.add("记录准确地址、发生时间、持续时长、风向和可见现象");
        actions.add("现场结论必须以仪器实测 SO₂、CO、PM2.5 数据为准");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskScore", score);
        result.put("riskLevel", level);
        result.put("estimatedGrade", estimatedGrade);
        result.put("confidence", confidence);
        result.put("triggers", triggers.isEmpty() ? List.of("未发现明显高风险关键词") : triggers);
        result.put("actions", actions);
        result.put("evidenceChecklist", List.of("详细地址或地标", "发生时间与持续时长", "现场照片或视频", "风向与气味描述", "是否有人出现不适"));
        result.put("disclaimer", "该结果用于派单优先级辅助，不替代专业检测、执法认定或医疗建议。");
        return result;
    }

    private int match(String text, List<String> triggers, int points, String label, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                triggers.add(label + "（命中：“" + keyword + "”）");
                return points;
            }
        }
        return 0;
    }
}
