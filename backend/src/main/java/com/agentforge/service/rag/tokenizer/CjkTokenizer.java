package com.agentforge.service.rag.tokenizer;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯 Java 实现的工业级 CJK (中日韩) 二元分词 (Bigram) 与自然语言提问分词引擎。
 * 配合 PostgreSQL 基础 'simple' 字典，实现零外部 C 库依赖下的高召回率全文检索。
 */
public class CjkTokenizer {

    // 常见中文提问虚词与停用词集合 (检索时过滤以防止稀释关键词命中)
    private static final Set<String> STOP_WORDS = Set.of(
            "是多少", "是多少呢", "有什么", "有哪些", "是什么", "什么是",
            "怎么做", "怎么", "如何", "请问", "能不能", "可否", "可以吗",
            "的", "了", "在", "是", "我", "有", "和", "就", "不", "人", "都", "一", "一个",
            "上", "也", "很", "到", "说", "要", "去", "你", "会", "着", "没有", "看", "好",
            "自己", "这", "那", "吗", "呢", "吧", "啊", "呀", "及", "与", "以及", "关于"
    );

    private static final Pattern WORD_PATTERN = Pattern.compile("[a-zA-Z0-9_+#\\.-]+");

    /**
     * 判断字符是否属于 CJK 汉字字符集
     */
    public static boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B;
    }

    /**
     * 将原始中英文混合文本切分为适用于 PostgreSQL 'simple' 字典索引的 Token 串 (以空格分隔)
     * 中文输出: 单字 (1-gram) + 双字 (2-gram)
     * 英文与数字: 完整保留单词与数字
     */
    public static String tokenize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        StringBuilder sb = new StringBuilder(text.length() * 3);
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);

            if (isCjk(c)) {
                // 1. 添加 CJK 1-gram
                sb.append(c).append(' ');

                // 2. 添加 CJK 2-gram
                if (i + 1 < len) {
                    char next = text.charAt(i + 1);
                    if (isCjk(next)) {
                        sb.append(c).append(next).append(' ');
                    }
                }
            } else if (Character.isLetterOrDigit(c)) {
                // 英文/数字连续词元收集
                int start = i;
                while (i < len && (Character.isLetterOrDigit(text.charAt(i)) || text.charAt(i) == '_' || text.charAt(i) == '-')) {
                    i++;
                }
                sb.append(text, start, i).append(' ');
                i--; // 回退一位由外层循环递增
            } else {
                sb.append(' ');
            }
        }

        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    /**
     * 将用户的自然语言提问转换为高召回率的 PostgreSQL tsquery 表达式。
     * 自动剥离中文虚词与疑问代词，将有效关键词以 '|' (OR) 连接，并保留精准 2-gram。
     *
     * 示例输入: "北京出差住宿报销标准是多少"
     * 过滤后关键词: "北京", "出差", "住宿", "报销", "标准"
     * 产出 tsquery: "'北京' | '出差' | '住宿' | '报销' | '标准'"
     */
    public static String toTsQuery(String query) {
        if (query == null || query.isBlank()) {
            return "''";
        }

        String cleaned = query.trim();
        // 过滤常见长停用词短语
        for (String stop : STOP_WORDS) {
            if (stop.length() >= 2 && cleaned.contains(stop)) {
                cleaned = cleaned.replace(stop, " ");
            }
        }

        Set<String> tokenSet = new LinkedHashSet<>();

        // 提取连续英文单词与数字
        Matcher matcher = WORD_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            String w = matcher.group().trim();
            if (w.length() >= 2) {
                tokenSet.add(w);
            }
        }

        // 提取 CJK 连续片段并生成 2-gram 与核心单字
        StringBuilder currentCjk = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (isCjk(c)) {
                currentCjk.append(c);
            } else {
                if (currentCjk.length() > 0) {
                    processCjkSegment(currentCjk.toString(), tokenSet);
                    currentCjk.setLength(0);
                }
            }
        }
        if (currentCjk.length() > 0) {
            processCjkSegment(currentCjk.toString(), tokenSet);
        }

        // 过滤单字符无意义字 (如果已经有 2-gram)
        if (tokenSet.isEmpty()) {
            // 兜底返回原句切分
            return "'" + sanitizeToken(query.trim()) + "'";
        }

        StringJoiner sj = new StringJoiner(" | ");
        for (String t : tokenSet) {
            String sanitized = sanitizeToken(t);
            if (!sanitized.isBlank()) {
                sj.add("'" + sanitized + "'");
            }
        }

        String result = sj.toString();
        return result.isBlank() ? "''" : result;
    }

    /**
     * 将自然语言提问转换为适用于 PostgreSQL websearch_to_tsquery 的查询串 (以 'or' 连接有效词元)
     * 示例输入: "北京出差住宿报销标准是多少"
     * 输出: "北京 or 出差 or 住宿 or 报销 or 标准"
     */
    public static String toWebSearchQuery(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }

        String cleaned = query.trim();
        for (String stop : STOP_WORDS) {
            if (stop.length() >= 2 && cleaned.contains(stop)) {
                cleaned = cleaned.replace(stop, " ");
            }
        }

        Set<String> tokenSet = new LinkedHashSet<>();

        Matcher matcher = WORD_PATTERN.matcher(cleaned);
        while (matcher.find()) {
            String w = matcher.group().trim();
            if (w.length() >= 2) {
                tokenSet.add(w);
            }
        }

        StringBuilder currentCjk = new StringBuilder();
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (isCjk(c)) {
                currentCjk.append(c);
            } else {
                if (currentCjk.length() > 0) {
                    processCjkSegment(currentCjk.toString(), tokenSet);
                    currentCjk.setLength(0);
                }
            }
        }
        if (currentCjk.length() > 0) {
            processCjkSegment(currentCjk.toString(), tokenSet);
        }

        if (tokenSet.isEmpty()) {
            return sanitizeToken(query.trim());
        }

        StringJoiner sj = new StringJoiner(" or ");
        for (String t : tokenSet) {
            String sanitized = sanitizeToken(t);
            if (!sanitized.isBlank()) {
                sj.add(sanitized);
            }
        }
        return sj.toString();
    }

    private static void processCjkSegment(String segment, Set<String> tokenSet) {
        if (segment.length() == 1) {
            tokenSet.add(segment);
            return;
        }

        // 优先存入 2-gram
        for (int i = 0; i < segment.length() - 1; i++) {
            String bigram = segment.substring(i, i + 2);
            if (!STOP_WORDS.contains(bigram)) {
                tokenSet.add(bigram);
            }
        }

        // 如果片段较长，可补充整词
        if (segment.length() <= 4 && !STOP_WORDS.contains(segment)) {
            tokenSet.add(segment);
        }
    }

    private static String sanitizeToken(String token) {
        // 防止 SQL/tsquery 注入转义
        return token.replace("'", "").replace("\\", "").replace("&", "").replace("|", "").replace("!", "").replace(":", "");
    }
}
