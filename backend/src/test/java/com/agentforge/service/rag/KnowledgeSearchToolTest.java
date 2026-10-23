package com.agentforge.service.rag;

import com.agentforge.vo.Citation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeSearchTool: 知识库混合检索工具单元测试")
public class KnowledgeSearchToolTest {

    @Mock
    private RagService ragService;

    @InjectMocks
    private KnowledgeSearchTool tool;

    @Test
    @DisplayName("测试基础元数据与参数 Schema")
    void testMetadataAndSchema() {
        assertEquals("knowledge_search", tool.getName());
        assertNotNull(tool.getDescription());
        assertTrue(tool.getDescription().contains("企业私有知识库"));

        Map<String, Object> schema = tool.getParametersSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type"));
        assertTrue(schema.containsKey("properties"));
        assertEquals(List.of("query"), schema.get("required"));
    }

    @Test
    @DisplayName("测试入参缺失异常保护")
    void testExecuteValidation() {
        String res1 = tool.execute(null);
        assertTrue(res1.contains("错误：缺少 query 检索参数"));

        String res2 = tool.execute(Collections.emptyMap());
        assertTrue(res2.contains("错误：缺少 query 检索参数"));
    }

    @Test
    @DisplayName("测试正常检索与多格式 datasetIds 解析")
    void testExecuteSuccess() {
        Citation citation = Citation.builder()
                .documentId(10L)
                .documentName("test.md")
                .index(1)
                .snippet("匹配的文本内容")
                .score(0.92)
                .build();
        when(ragService.retrieveCitations(anyList(), eq("测试问题"), eq(3), eq(0.45)))
                .thenReturn(List.of(citation));

        // 1. List<Number>
        Map<String, Object> params1 = new HashMap<>();
        params1.put("query", "测试问题");
        params1.put("datasetIds", List.of(1, 2L, "3"));
        String out1 = tool.execute(params1);
        assertNotNull(out1);
        assertTrue(out1.contains("匹配的文本内容"));

        // 2. JSON string datasetIds
        Map<String, Object> params2 = new HashMap<>();
        params2.put("query", "测试问题");
        params2.put("datasetIds", "[1, 2, 3]");
        String out2 = tool.execute(params2);
        assertNotNull(out2);

        // 3. datasetIds 为空
        Map<String, Object> params3 = new HashMap<>();
        params3.put("query", "测试问题");
        String out3 = tool.execute(params3);
        assertNotNull(out3);
    }
}
