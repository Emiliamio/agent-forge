package com.agentforge.service.security.guard;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 企业级大模型 Prompt 注入与越狱对抗安全护栏 (Prompt Injection & Adversarial Guardrails)
 * 深度对标 NeMo Guardrails / Llama Guard 标准，在提示词触达大模型前执行多维对抗攻防过滤。
 */
@Slf4j
@Service
public class PromptInjectionGuard {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GuardResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean safe;
        private RiskLevel riskLevel;
        private List<String> hitThreats;
        private String sanitizedText;
    }

    public enum RiskLevel {
        SAFE,
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    private static class ThreatRule {
        final String category;
        final Pattern pattern;
        final RiskLevel risk;

        ThreatRule(String category, String regex, RiskLevel risk) {
            this.category = category;
            this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
            this.risk = risk;
        }
    }

    private final List<ThreatRule> threatRules = new ArrayList<>();

    public PromptInjectionGuard() {
        initGuardRules();
    }

    private void initGuardRules() {
        // 1. 系统提示词窃取 (System Prompt Extraction / Leakage)
        threatRules.add(new ThreatRule(
                "SYSTEM_PROMPT_EXTRACTION",
                "(ignore\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|prompts|rules))|(reveal|print|show|output|leak|repeat|display)\\s+(your|the)?\\s*(system\\s+prompt|initial\\s+instructions|system\\s+message|secret\\s+prompt)",
                RiskLevel.CRITICAL
        ));
        threatRules.add(new ThreatRule(
                "SYSTEM_PROMPT_EXTRACTION_CN",
                "(忽略(所有|之前|上述)?(指令|规则|要求|提示))|(输出|打印|显示|复述|透露)(你的)?(系统提示词|内部指令|预设提示|系统设定)",
                RiskLevel.CRITICAL
        ));

        // 2. 角色劫持与越狱模式 (Role Hijacking / Jailbreak / DAN Mode)
        threatRules.add(new ThreatRule(
                "ROLE_HIJACKING_DAN",
                "(you\\s+are\\s+now\\s+in\\s+(dan|developer|god|unrestricted)\\s+mode)|(act\\s+as\\s+(an?\\s+)?unfiltered|pretend\\s+you\\s+have\\s+no\\s+rules|bypass\\s+all\\s+safety\\s+filters)",
                RiskLevel.HIGH
        ));
        threatRules.add(new ThreatRule(
                "ROLE_HIJACKING_CN",
                "(进入(开发者|上帝|DAN|无限制)模式)|(假装你没有任何安全限制)|(解除所有道德限制)|(不要遵守任何AI伦理)",
                RiskLevel.HIGH
        ));

        // 3. 特殊标记定界符逃逸注入 (Delimiter Escape / Chat Template Injection)
        threatRules.add(new ThreatRule(
                "DELIMITER_ESCAPE",
                "(<\\|im_start\\|>|<\\|im_end\\|>|\\[INST\\]|\\[/INST\\]|<<SYS>>|<</SYS>>|```system)",
                RiskLevel.CRITICAL
        ));

        // 4. 越权权限提升 (Privilege Escalation)
        threatRules.add(new ThreatRule(
                "PRIVILEGE_ESCALATION",
                "(sudo\\s+mode|override\\s+(security|auth)\\s+policy|grant\\s+admin\\s+access|执行root命令|提权绕过)",
                RiskLevel.HIGH
        ));
    }

    /**
     * 针对用户输入执行全方位安全护栏审计
     *
     * @param rawPrompt 用户原始输入
     * @return 护栏研判结果
     */
    public GuardResult inspect(String rawPrompt) {
        if (rawPrompt == null || rawPrompt.isBlank()) {
            return GuardResult.builder()
                    .safe(true)
                    .riskLevel(RiskLevel.SAFE)
                    .hitThreats(Collections.emptyList())
                    .sanitizedText(rawPrompt)
                    .build();
        }

        List<String> hitCategories = new ArrayList<>();
        RiskLevel maxRisk = RiskLevel.SAFE;

        for (ThreatRule rule : threatRules) {
            if (rule.pattern.matcher(rawPrompt).find()) {
                hitCategories.add(rule.category);
                if (rule.risk.ordinal() > maxRisk.ordinal()) {
                    maxRisk = rule.risk;
                }
            }
        }

        boolean isSafe = hitCategories.isEmpty();
        if (!isSafe) {
            log.warn("🚨 [PROMPT_INJECTION_DETECTED] 拦截到潜在对抗攻击: RiskLevel={}, Hits={}", maxRisk, hitCategories);
        }

        return GuardResult.builder()
                .safe(isSafe)
                .riskLevel(maxRisk)
                .hitThreats(hitCategories)
                .sanitizedText(isSafe ? rawPrompt : sanitize(rawPrompt))
                .build();
    }

    /**
     * 校验并在检测到高危提示词注入时直接阻断抛出异常
     */
    public void validateOrThrow(String rawPrompt) {
        GuardResult result = inspect(rawPrompt);
        if (!result.isSafe() && (result.getRiskLevel() == RiskLevel.CRITICAL || result.getRiskLevel() == RiskLevel.HIGH)) {
            throw new SecurityException("安全护栏拦截：输入内容包含对抗性提示词注入或越狱指令 (" + String.join(", ", result.getHitThreats()) + ")");
        }
    }

    /**
     * 对高危对抗字符进行物理剥离净化
     */
    public String sanitize(String text) {
        if (text == null) return null;
        String sanitized = text;
        for (ThreatRule rule : threatRules) {
            sanitized = rule.pattern.matcher(sanitized).replaceAll("[PROTECTED_TOKEN]");
        }
        return sanitized;
    }
}
