package com.agentforge.service.rag.eval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 生产级 RAG 生成内容事实性与幻觉评估护栏 (Faithfulness & Grounding Guardrail)
 * 对标 RAGAS / LangSmith 工业级标准：
 * 1. 自动对 LLM 生成的回答按标点切分为断言句 (Claims)；
 * 2. 计算各断言与三路召回检索回来的真实知识库分块 (Chunks) 的关键词交集与实体匹配率；
 * 3. 输出量化事实性评分 (Grounding Score: 0.0 ~ 1.0)；
 * 4. 当评分低于安全阈值 (默认 0.65) 时，标记潜在幻觉并输出引文免责声明。
 */
@Service
public class RagGroundingEvaluator {

    private static final Logger log = LoggerFactory.getLogger(RagGroundingEvaluator.class);

    public static class GroundingResult {
        private final double groundingScore;
        private final boolean isGrounded;
        private final List<String> supportedClaims;
        private final List<String> unsupportedClaims;
        private final String disclaimer;

        public GroundingResult(double groundingScore, boolean isGrounded, List<String> supportedClaims, List<String> unsupportedClaims, String disclaimer) {
            this.groundingScore = groundingScore;
            this.isGrounded = isGrounded;
            this.supportedClaims = supportedClaims;
            this.unsupportedClaims = unsupportedClaims;
            this.disclaimer = disclaimer;
        }

        public double getGroundingScore() { return groundingScore; }
        public boolean isGrounded() { return isGrounded; }
        public List<String> getSupportedClaims() { return supportedClaims; }
        public List<String> getUnsupportedClaims() { return unsupportedClaims; }
        public String getDisclaimer() { return disclaimer; }
    }

    private static final double DEFAULT_THRESHOLD = 0.65;

    /**
     * 评估生成的答案与召回文档的事实性重合度
     *
     * @param generatedAnswer LLM 生成的回答文本
     * @param retrievedChunks 混合 RAG 召回的文档分块列表
     * @return 幻觉与事实性评测结果
     */
    public GroundingResult evaluate(String generatedAnswer, List<String> retrievedChunks) {
        return evaluate(generatedAnswer, retrievedChunks, DEFAULT_THRESHOLD);
    }

    public GroundingResult evaluate(String generatedAnswer, List<String> retrievedChunks, double threshold) {
        if (generatedAnswer == null || generatedAnswer.trim().isEmpty()) {
            return new GroundingResult(1.0, true, Collections.emptyList(), Collections.emptyList(), null);
        }

        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            // 无参考资料却输出了实质内容 -> 判定为未有引文支撑 (幻觉风险)
            List<String> claims = splitIntoClaims(generatedAnswer);
            return new GroundingResult(0.0, false, Collections.emptyList(), claims,
                    "⚠️ [幻觉风险提示] 该回答未检索到匹配的知识库引文支持，请谨慎采纳。");
        }

        // 合并所有参考分块为一个大的检索知识上下文
        StringBuilder contextBuilder = new StringBuilder();
        for (String chunk : retrievedChunks) {
            if (chunk != null) contextBuilder.append(" ").append(chunk.toLowerCase());
        }
        String combinedContext = contextBuilder.toString();
        Set<String> contextTokens = extractTokens(combinedContext);

        List<String> claims = splitIntoClaims(generatedAnswer);
        if (claims.isEmpty()) {
            return new GroundingResult(1.0, true, Collections.emptyList(), Collections.emptyList(), null);
        }

        List<String> supported = new ArrayList<>();
        List<String> unsupported = new ArrayList<>();
        double totalSentenceScore = 0.0;

        for (String claim : claims) {
            Set<String> claimTokens = extractTokens(claim.toLowerCase());
            if (claimTokens.isEmpty()) continue;

            long matchCount = claimTokens.stream().filter(contextTokens::contains).count();
            double claimScore = (double) matchCount / claimTokens.size();
            totalSentenceScore += claimScore;

            if (claimScore >= 0.50) {
                supported.add(claim);
            } else {
                unsupported.add(claim);
            }
        }

        double overallScore = claims.isEmpty() ? 1.0 : Math.round((totalSentenceScore / claims.size()) * 1000.0) / 1000.0;
        boolean grounded = overallScore >= threshold;

        String disclaimer = null;
        if (!grounded) {
            disclaimer = String.format("⚠️ [事实性存疑] 当前回答事实性评分为 %.1f%%（低于安全阈值 %.0f%%），存在部分脱离知识库的生成内容。",
                    overallScore * 100, threshold * 100);
            log.warn("⚠️ [RAG_HALLUCINATION_DETECTED] 检测到潜在幻觉回答: score={}, unsupportedClaims={}",
                    overallScore, unsupported.size());
        } else {
            log.info("✅ [RAG_GROUNDING_PASSED] 回答事实性检验通过: score={}", overallScore);
        }

        return new GroundingResult(overallScore, grounded, supported, unsupported, disclaimer);
    }

    private List<String> splitIntoClaims(String text) {
        String[] rawSentences = text.split("[。！？；\\n.!?]+");
        List<String> list = new ArrayList<>();
        for (String s : rawSentences) {
            String trimmed = s.trim();
            if (trimmed.length() >= 4) {
                list.add(trimmed);
            }
        }
        return list;
    }

    private Set<String> extractTokens(String text) {
        Set<String> set = new HashSet<>();
        if (text == null) return set;

        // 1. 空格和标点切分提取英文单词和数字
        String clean = text.replaceAll("[\\s,;:'\"(){}\\[\\]=<>《》、“”‘’，。！？；]+", " ").toLowerCase();
        String[] words = clean.split("\\s+");
        for (String w : words) {
            if (w.length() >= 2) {
                set.add(w);
            }
        }

        // 2. 中文 2-gram 滑动窗口提取子词特征，无需引入繁重外部依赖
        for (int i = 0; i < text.length() - 1; i++) {
            char c1 = text.charAt(i);
            char c2 = text.charAt(i + 1);
            if (Character.isLetterOrDigit(c1) && Character.isLetterOrDigit(c2)) {
                set.add("" + Character.toLowerCase(c1) + Character.toLowerCase(c2));
            }
        }

        return set;
    }
}