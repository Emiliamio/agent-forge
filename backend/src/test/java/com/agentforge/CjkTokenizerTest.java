package com.agentforge;

import com.agentforge.service.rag.tokenizer.CjkTokenizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CJK 二元分词器与自然语言提问检索转换测试")
public class CjkTokenizerTest {

    @Test
    @DisplayName("测试中英文混合切分产生 1-gram 与 2-gram")
    void testCjkTokenize() {
        String text = "员工前往北京出差，住宿报销上限800元。";
        String tokens = CjkTokenizer.tokenize(text);

        assertNotNull(tokens);
        // 包含核心 2-gram
        assertTrue(tokens.contains("员工"), "必须包含 2-gram '员工'");
        assertTrue(tokens.contains("北京"), "必须包含 2-gram '北京'");
        assertTrue(tokens.contains("出差"), "必须包含 2-gram '出差'");
        assertTrue(tokens.contains("住宿"), "必须包含 2-gram '住宿'");
        assertTrue(tokens.contains("报销"), "必须包含 2-gram '报销'");
        assertTrue(tokens.contains("800"), "必须完整保留数字 800");
    }

    @Test
    @DisplayName("测试自然语言疑问句提取关键词并生成 websearch OR 查询串")
    void testNaturalLanguageQuestionToWebSearchQuery() {
        String query = "北京出差住宿报销标准是多少";
        String searchQuery = CjkTokenizer.toWebSearchQuery(query);

        assertNotNull(searchQuery);
        // 必须过滤疑问词 "是多少"
        assertFalse(searchQuery.contains("是多少"), "必须过滤中文疑问停用词 '是多少'");
        // 必须保留核心业务关键词
        assertTrue(searchQuery.contains("北京"), "必须包含关键词 '北京'");
        assertTrue(searchQuery.contains("出差"), "必须包含关键词 '出差'");
        assertTrue(searchQuery.contains("住宿"), "必须包含关键词 '住宿'");
        assertTrue(searchQuery.contains("报销"), "必须包含关键词 '报销'");
        assertTrue(searchQuery.contains("标准"), "必须包含关键词 '标准'");
        assertTrue(searchQuery.contains(" or "), "关键词之间必须使用 'or' 连接");
    }

    @Test
    @DisplayName("测试中英混合技术问句分词转换")
    void testMixedTechQuery() {
        String query = "微服务架构中的 RESTful API 鉴权方案有哪些？";
        String searchQuery = CjkTokenizer.toWebSearchQuery(query);

        assertNotNull(searchQuery);
        assertTrue(searchQuery.contains("RESTful"), "必须完整保留英文专有名词 RESTful");
        assertTrue(searchQuery.contains("API"), "必须完整保留英文专有名词 API");
        assertTrue(searchQuery.contains("架构"), "必须包含中文关键词 '架构'");
        assertTrue(searchQuery.contains("鉴权"), "必须包含中文关键词 '鉴权'");
        assertFalse(searchQuery.contains("有哪些"), "必须过滤疑问词 '有哪些'");
    }
}
