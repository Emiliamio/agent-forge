package com.agentforge.service.security.moderation;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 政企等保红线：DFA (确定性有穷自动机) 毫秒级内容安全审计与违规过滤中继
 * 针对用户输入 Prompt 与大模型输出文本执行毫秒级 (<0.2ms) 敏感词合规审查
 */
@Slf4j
@Service
public class DfaContentModerationService {

    // DFA 根节点树
    private final Map<Character, Object> dfaRoot = new HashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModerationResult implements Serializable {
        private static final long serialVersionUID = 1L;

        private boolean isPassed;
        private String filteredText;
        private Set<String> hitKeywords;
        private int hitCount;
    }

    public DfaContentModerationService() {
        // 初始化预置等保红线测试关键词库
        initKeywordTree(Set.of(
                "非法集资", "涉黄暴恐", "反动颠覆", "洗钱逃税", "内部绝密窃取", "高利贷诈骗"
        ));
    }

    /**
     * 动态热重载敏感词库
     */
    public synchronized void initKeywordTree(Collection<String> keywords) {
        dfaRoot.clear();
        for (String word : keywords) {
            if (StrUtil.isBlank(word)) continue;
            Map<Character, Object> current = dfaRoot;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                Object sub = current.get(c);
                if (sub == null) {
                    Map<Character, Object> newSub = new HashMap<>();
                    current.put(c, newSub);
                    current = newSub;
                } else {
                    current = (Map<Character, Object>) sub;
                }
                if (i == word.length() - 1) {
                    current.put('\0', Boolean.TRUE); // 结束标记
                }
            }
        }
        log.info("🛡️ DFA 内容安全敏感词树初始化完成: 词条数量={}", keywords.size());
    }

    /**
     * 快速审计文本内容
     */
    public ModerationResult inspect(String text) {
        if (StrUtil.isBlank(text)) {
            return ModerationResult.builder().isPassed(true).filteredText(text).hitKeywords(Set.of()).hitCount(0).build();
        }

        Set<String> hitKeywords = new HashSet<>();
        char[] chars = text.toCharArray();
        StringBuilder filteredSb = new StringBuilder(text);

        for (int i = 0; i < chars.length; i++) {
            Map<Character, Object> current = dfaRoot;
            int matchLen = 0;

            for (int j = i; j < chars.length; j++) {
                char c = chars[j];
                Object next = current.get(c);
                if (next == null) {
                    break;
                }
                matchLen++;
                current = (Map<Character, Object>) next;

                if (current.containsKey('\0')) {
                    // 命中敏感词
                    String hitWord = text.substring(i, i + matchLen);
                    hitKeywords.add(hitWord);

                    // 替换为等长星号
                    for (int k = i; k < i + matchLen; k++) {
                        filteredSb.setCharAt(k, '*');
                    }
                    break;
                }
            }
        }

        boolean passed = hitKeywords.isEmpty();
        if (!passed) {
            log.warn("🚨 DFA 拦截到违规敏感词: 命中词条={}, 原文预览={}", hitKeywords, StrUtil.maxLength(text, 50));
        }

        return ModerationResult.builder()
                .isPassed(passed)
                .filteredText(filteredSb.toString())
                .hitKeywords(hitKeywords)
                .hitCount(hitKeywords.size())
                .build();
    }
}
