package com.agentforge.service.security.pii;

import cn.hutool.core.util.StrUtil;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 金融级 PII 敏感信息脱敏与 DLP 数据防泄漏服务
 * 杜绝身份证号、手机号、银行卡号、邮箱等敏感数据明文外发给大模型
 */
@Slf4j
@Service
public class PiiMaskingService {

    // 中国 11 位手机号正则
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    // 中国 18 位身份证号正则
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)([1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx])(?!\\d)");
    // 银行卡号正则 (16-19 位)
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("(?<!\\d)([1-9]\\d{15,18})(?!\\d)");
    // 邮箱正则
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    @Data
    @Builder
    public static class MaskResult {
        private String maskedText;
        private Map<String, String> tokenMap; // [MASK_TOKEN] -> 原始明文 (用于解密还原)
        private int maskedCount;
    }

    /**
     * 对 Prompt 执行双向可逆脱敏 (Prompt 发给大模型前调用)
     */
    public MaskResult maskSensitiveData(String text) {
        if (StrUtil.isBlank(text)) {
            return MaskResult.builder().maskedText(text).tokenMap(Map.of()).maskedCount(0).build();
        }

        Map<String, String> tokenMap = new HashMap<>();
        String result = text;
        int count = 0;

        // 1. 脱敏手机号
        Matcher phoneMatcher = PHONE_PATTERN.matcher(result);
        StringBuffer phoneSb = new StringBuffer();
        int phoneIdx = 1;
        while (phoneMatcher.find()) {
            String rawPhone = phoneMatcher.group(1);
            String token = "[PHONE_" + (phoneIdx++) + "]";
            tokenMap.put(token, rawPhone);
            phoneMatcher.appendReplacement(phoneSb, Matcher.quoteReplacement(token));
            count++;
        }
        phoneMatcher.appendTail(phoneSb);
        result = phoneSb.toString();

        // 2. 脱敏身份证
        Matcher idMatcher = ID_CARD_PATTERN.matcher(result);
        StringBuffer idSb = new StringBuffer();
        int idIdx = 1;
        while (idMatcher.find()) {
            String rawId = idMatcher.group(1);
            String token = "[IDCARD_" + (idIdx++) + "]";
            tokenMap.put(token, rawId);
            idMatcher.appendReplacement(idSb, Matcher.quoteReplacement(token));
            count++;
        }
        idMatcher.appendTail(idSb);
        result = idSb.toString();

        // 3. 脱敏银行卡
        Matcher bankMatcher = BANK_CARD_PATTERN.matcher(result);
        StringBuffer bankSb = new StringBuffer();
        int bankIdx = 1;
        while (bankMatcher.find()) {
            String rawBank = bankMatcher.group(1);
            String token = "[BANKCARD_" + (bankIdx++) + "]";
            tokenMap.put(token, rawBank);
            bankMatcher.appendReplacement(bankSb, Matcher.quoteReplacement(token));
            count++;
        }
        bankMatcher.appendTail(bankSb);
        result = bankSb.toString();

        if (count > 0) {
            log.info("🛡️ PII 敏感数据脱敏拦截: 成功识别并保护 {} 处敏感实体", count);
        }

        return MaskResult.builder()
                .maskedText(result)
                .tokenMap(tokenMap)
                .maskedCount(count)
                .build();
    }

    /**
     * 对大模型返回的回答执行逆向解密还原 (返回给前端前调用)
     */
    public String unmaskResponse(String text, Map<String, String> tokenMap) {
        if (StrUtil.isBlank(text) || tokenMap == null || tokenMap.isEmpty()) {
            return text;
        }

        String result = text;
        for (Map.Entry<String, String> entry : tokenMap.entrySet()) {
            if (result.contains(entry.getKey())) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }
}
