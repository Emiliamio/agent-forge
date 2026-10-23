package com.agentforge.service;

import com.agentforge.context.TenantContextHolder;
import com.agentforge.entity.*;
import com.agentforge.exception.BusinessException;
import com.agentforge.mapper.*;
import com.agentforge.service.agent.ReActAgentService;
import com.agentforge.service.agent.llm.LlmClient;
import com.agentforge.service.platform.api.SsoAuthService;
import com.agentforge.service.billing.api.SemanticCacheService;
import com.agentforge.service.billing.internal.BillingServiceImpl;
import com.agentforge.service.chat.impl.ChatSessionServiceImpl;
import com.agentforge.service.platform.internal.BotWebhookAdapter;
import com.agentforge.service.rag.chunk.ChunkManagementService;
import com.agentforge.service.rag.embedding.DefaultEmbeddingService;
import com.agentforge.service.rag.impl.RagServiceImpl;
import com.agentforge.service.rag.parser.*;
import com.agentforge.service.rag.parser.excel.ResilientExcelParser;
import com.agentforge.service.rag.parser.sidecar.VisionSidecarParser;
import com.agentforge.service.rag.search.HybridSearchService;
import com.agentforge.service.rag.version.DocumentVersionHotSwapService;
import com.agentforge.service.security.api.PiiMaskingService;
import com.agentforge.service.platform.api.SystemSelfCheckService;
import com.agentforge.service.workflow.model.DagModel;
import com.agentforge.service.workflow.model.WorkflowContext;
import com.agentforge.service.workflow.node.*;
import com.agentforge.vo.ChunkSearchResult;
import com.agentforge.vo.Citation;
import com.agentforge.vo.LoginVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.agentforge.service.rag.citation.CitationHighlightEngine;
import com.agentforge.service.rag.tokenizer.CjkTokenizer;
import com.agentforge.service.rag.acl.AclPermissionContext;
import com.agentforge.service.market.TemplateMarketService;
import com.agentforge.service.platform.api.SystemSelfCheckService.CheckItem;
import com.agentforge.service.platform.api.SystemSelfCheckService.DiagnosticReport;
import com.agentforge.service.platform.api.SsoAuthService.OrgDeptSyncItem;
import com.agentforge.service.billing.api.SemanticCacheService.CachedEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 核心服务层全面覆盖单测套件 (覆盖 IM, Version, Chunk, System, Auth, Billing, Chat, Parser, Workflow)
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ComprehensiveServiceCoverageTest: 核心服务层深度质量与边界单测覆盖")
public class ComprehensiveServiceCoverageTest {

    @Mock private DocumentMapper documentMapper;
    @Mock private DocumentChunkMapper chunkMapper;
    @Mock private SysTenantMapper tenantMapper;
    @Mock private TokenUsageLogMapper tokenUsageLogMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ChatSessionMapper chatSessionMapper;
    @Mock private ChatMessageMapper chatMessageMapper;
    @Mock private ChatFeedbackMapper chatFeedbackMapper;
    @Mock private ReActAgentService reActAgentService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;
    @Mock private RedisConnectionFactory redisConnectionFactory;
    @Mock private RedisConnection redisConnection;
    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private HybridSearchService hybridSearchService;
    @Mock private DefaultEmbeddingService embeddingService;
    @Mock private PiiMaskingService piiMaskingService;
    @Mock private LlmClient llmClient;

    @BeforeEach
    void setUp() {
        TenantContextHolder.setTenantId(1L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getConnectionFactory()).thenReturn(redisConnectionFactory);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(embeddingService.embed(anyString())).thenReturn(new float[1536]);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // =========================================================================
    // 1. IM: BotWebhookAdapter
    // =========================================================================
    @Test
    @DisplayName("IM: 飞书 Webhook 挑战验证与用户提问分发")
    void testBotWebhookAdapter() {
        BotWebhookAdapter adapter = new BotWebhookAdapter(reActAgentService);

        // 1. URL 挑战模式
        Map<String, Object> challengeResp = adapter.handleFeishuEvent("{\"challenge\":\"test_challenge_code_123\"}");
        assertEquals("test_challenge_code_123", challengeResp.get("challenge"));

        // 2. 消息事件分发
        when(reActAgentService.runAgent(anyLong(), anyString(), anyInt()))
                .thenReturn(com.agentforge.vo.AgentExecutionResult.builder()
                        .finalAnswer("已为您查询到差旅报销政策。")
                        .build());

        String eventBody = "{\"event\":{\"message\":{\"content\":\"差旅报销标准\"}}}";
        Map<String, Object> eventResp = adapter.handleFeishuEvent(eventBody);
        assertEquals(0, eventResp.get("code"));
        assertEquals("success", eventResp.get("msg"));
    }

    // =========================================================================
    // 2. RAG Version: DocumentVersionHotSwapService
    // =========================================================================
    @Test
    @DisplayName("RAG Version: 文档版本原子替换与旧切片清理")
    void testDocumentVersionHotSwapService() {
        DocumentVersionHotSwapService service = new DocumentVersionHotSwapService(documentMapper, chunkMapper, redisTemplate);

        Document doc = new Document();
        doc.setId(10L);
        doc.setTenantId(1L);
        when(documentMapper.selectById(10L)).thenReturn(doc);
        when(chunkMapper.delete(any())).thenReturn(5);
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("agentforge:cache:1:k1"));

        int deleted = service.purgeOldVersionAndPrepare(10L);
        assertEquals(5, deleted);
        assertEquals("PARSING", doc.getParseStatus());
        assertEquals(0, doc.getChunkCount());

        // 跨租户拦截
        Document otherTenantDoc = new Document();
        otherTenantDoc.setId(11L);
        otherTenantDoc.setTenantId(2L);
        when(documentMapper.selectById(11L)).thenReturn(otherTenantDoc);

        assertThrows(IllegalArgumentException.class, () -> service.purgeOldVersionAndPrepare(11L));
    }

    // =========================================================================
    // 3. RAG Chunk: ChunkManagementService
    // =========================================================================
    @Test
    @DisplayName("RAG Chunk: 切片在线热编辑与状态切换")
    void testChunkManagementService() {
        ChunkManagementService service = new ChunkManagementService(chunkMapper, embeddingService, redisTemplate);

        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(100L);
        chunk.setTenantId(1L);
        chunk.setDocumentId(10L);
        chunk.setContent("旧内容");
        when(chunkMapper.selectById(100L)).thenReturn(chunk);

        // 1. 更新内容
        DocumentChunk updated = service.updateChunkContent(100L, "新政策内容");
        assertEquals("新政策内容", updated.getContent());
        verify(chunkMapper).updateById(chunk);

        // 2. 切换状态
        service.toggleChunkStatus(100L, false);
        verify(chunkMapper, times(2)).updateById(chunk);

        // 3. 分页查询
        Page<DocumentChunk> page = new Page<>(1, 10);
        page.setRecords(List.of(chunk));
        when(chunkMapper.selectPage(any(), any())).thenReturn(page);
        Page<DocumentChunk> listRes = service.listChunks(10L, 1, 10);
        assertNotNull(listRes);
        assertEquals(1, listRes.getRecords().size());
        assertNotNull(service.updateChunkContentVo(100L, "新政策内容"));
        assertNotNull(service.listChunkVos(10L, 1, 10));
    }

    // =========================================================================
    // 4. System: SystemSelfCheckService
    // =========================================================================
    @Test
    @DisplayName("System: 基础设施一键诊断")
    void testSystemSelfCheckService() {
        SystemSelfCheckService service = new SystemSelfCheckService(jdbcTemplate, redisTemplate);

        when(jdbcTemplate.queryForObject(anyString(), eq(String.class))).thenReturn("vector");
        when(redisConnection.ping()).thenReturn("PONG");

        SystemSelfCheckService.DiagnosticReport report = service.runDiagnostics();
        assertNotNull(report);
        assertTrue(report.getTotalChecks() >= 4);
    }

    // =========================================================================
    // 5. Auth: SsoAuthService
    // =========================================================================
    @Test
    @DisplayName("Auth: SSO 组织架构与员工多端批量同步")
    void testSsoAuthService() {
        SsoAuthService service = new SsoAuthService(userMapper);

        SsoAuthService.OrgUserSyncItem uItem1 = SsoAuthService.OrgUserSyncItem.builder()
                .externalUserId("ext_01")
                .username("new_ext_user")
                .realName("外部员工")
                .email("ext@agentforge.ai")
                .role("VIEWER")
                .build();

        SsoAuthService.OrgUserSyncItem uItem2 = SsoAuthService.OrgUserSyncItem.builder()
                .externalUserId("ext_02")
                .username("existing_user")
                .realName("已存在员工")
                .email("exist@agentforge.ai")
                .role("EDITOR")
                .build();

        SysUser existing = new SysUser();
        existing.setId(20L);
        existing.setTenantId(1L);
        existing.setUsername("existing_user");

        when(userMapper.selectOne(any()))
                .thenReturn(null)
                .thenReturn(existing);

        int syncCount = service.syncOrganizationUsers(1L, List.of(uItem1, uItem2));
        assertEquals(2, syncCount);
        verify(userMapper).insert(any(SysUser.class));
        verify(userMapper).updateById(existing);
    }

    // =========================================================================
    // 6. Billing: SemanticCacheService & BillingServiceImpl
    // =========================================================================
    @Test
    @DisplayName("Billing: 语义缓存存取与计费扣费审计")
    void testBillingServices() {
        // 1. SemanticCacheService
        SemanticCacheService cacheService = new SemanticCacheService(redisTemplate, embeddingService);
        when(redisTemplate.keys(anyString())).thenReturn(Set.of("agentforge:semantic_cache:1:entry1"));

        SemanticCacheService.CachedEntry entry = SemanticCacheService.CachedEntry.builder()
                .query("差旅标准")
                .response("北京住宿 800")
                .vectorStr(com.agentforge.service.rag.VectorUtils.toString(new float[1536]))
                .createTime(System.currentTimeMillis())
                .build();
        when(valueOperations.get("agentforge:semantic_cache:1:entry1")).thenReturn(entry);

        cacheService.getCachedResponse("差旅标准");
        cacheService.putCache("差旅标准", "北京住宿 800");

        // 2. BillingServiceImpl
        BillingServiceImpl billingService = new BillingServiceImpl(tenantMapper, tokenUsageLogMapper);
        SysTenant tenant = new SysTenant();
        tenant.setId(1L);
        tenant.setWalletBalance(new BigDecimal("100.00"));
        when(tenantMapper.selectById(1L)).thenReturn(tenant);

        // 非缓存调用扣费
        billingService.recordAndDeduct(1L, 1L, 1L, "deepseek-chat", 500, 500, false, 120);
        verify(tenantMapper).updateById(tenant);
        verify(tokenUsageLogMapper).insert(any(TokenUsageLog.class));

        // 缓存调用 0 成本
        billingService.recordAndDeduct(1L, 1L, 1L, "gpt-4o", 500, 500, true, 20);
    }

    // =========================================================================
    // 7. Chat: ChatSessionServiceImpl
    // =========================================================================
    @Test
    @DisplayName("Chat: 会话创建、发送问答与用户反馈")
    void testChatSessionService() {
        ChatSessionServiceImpl service = new ChatSessionServiceImpl(
                chatSessionMapper, chatMessageMapper, chatFeedbackMapper, reActAgentService, piiMaskingService
        );

        // 1. 创建会话
        ChatSession session = service.createOrGetSession(1L, 1L, "测试会话");
        assertNotNull(session);
        verify(chatSessionMapper).insert(any(ChatSession.class));

        // 2. 问答流程
        when(chatSessionMapper.selectById(10L)).thenReturn(ChatSession.builder()
                .id(10L)
                .tenantId(1L)
                .appId(1L)
                .messageCount(0)
                .build());
        when(piiMaskingService.maskSensitiveData(anyString()))
                .thenReturn(PiiMaskingService.MaskResult.builder()
                        .maskedText("北京住宿标准是多少？")
                        .tokenMap(Map.of())
                        .build());
        when(reActAgentService.runAgent(anyLong(), anyString(), anyInt()))
                .thenReturn(com.agentforge.vo.AgentExecutionResult.builder()
                        .finalAnswer("北京住宿上限 800 元。")
                        .totalTokens(50)
                        .citations(List.of())
                        .steps(List.of())
                        .build());
        when(piiMaskingService.unmaskResponse(anyString(), anyMap()))
                .thenReturn("北京住宿上限 800 元。");

        ChatMessage reply = service.sendQuery(10L, 1L, "北京住宿标准是多少？");
        assertNotNull(reply);
        assertEquals("assistant", reply.getRole());
        assertEquals("北京住宿上限 800 元。", reply.getContent());

        // 3. 提交评价
        service.submitFeedback(100L, 1L, 1, "LIKE", "回答准确");
        verify(chatFeedbackMapper).insert(any(ChatFeedback.class));
    }

    // =========================================================================
    // 8. Parsers: Txt, Factory, Excel, VisionSidecar, Docx, Pdf
    // =========================================================================
    @Test
    @DisplayName("Parsers: 文本、工厂、Excel 与多模态解析器")
    void testDocumentParsers() {
        // 1. TxtDocumentParser
        TxtDocumentParser txtParser = new TxtDocumentParser();
        assertTrue(txtParser.supports("TXT"));
        assertTrue(txtParser.supports("JSON"));
        InputStream txtStream = new ByteArrayInputStream("第一行制度\n第二行条款".getBytes(StandardCharsets.UTF_8));
        ParsedDocument parsedTxt = txtParser.parse(txtStream, "policy.txt");
        assertNotNull(parsedTxt);
        assertTrue(parsedTxt.getFullText().contains("第一行制度"));

        // 2. DocumentParserFactory
        DocumentParserFactory factory = new DocumentParserFactory(List.of(txtParser));
        DocumentParser resolved = factory.getParser("TXT");
        assertSame(txtParser, resolved);
        assertThrows(BusinessException.class, () -> factory.getParser("UNKNOWN"));

        // 3. VisionSidecarParser
        PdfDocumentParser mockPdfParser = mock(PdfDocumentParser.class);
        when(mockPdfParser.parse(any(), anyString())).thenReturn(ParsedDocument.builder()
                .fullText("视觉多模态降级文本")
                .sections(List.of())
                .metadata(Map.of())
                .build());
        VisionSidecarParser visionParser = new VisionSidecarParser(mockPdfParser);
        assertTrue(visionParser.supports("PNG"));
        assertTrue(visionParser.supports("JPG"));
        ParsedDocument visionDoc = visionParser.parse(new ByteArrayInputStream(new byte[]{1, 2, 3}), "chart.png");
        assertNotNull(visionDoc);
        assertTrue(visionDoc.getFullText().contains("视觉多模态"));

        // 4. ResilientExcelParser
        ResilientExcelParser excelParser = new ResilientExcelParser();
        assertTrue(excelParser.supports("XLSX"));
        assertTrue(excelParser.supports("CSV"));
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb.createSheet("Sheet1");
            org.apache.poi.ss.usermodel.Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("姓名");
            row.createCell(1).setCellValue("报销金额");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            wb.write(out);
            ParsedDocument excelDoc = excelParser.parse(new ByteArrayInputStream(out.toByteArray()), "data.xlsx");
            assertNotNull(excelDoc);
            assertTrue(excelDoc.getFullText().contains("报销金额"));
        } catch (Exception e) {
            fail("Excel 解析异常: " + e.getMessage());
        }
    }

    // =========================================================================
    // 9. Workflow Nodes: Condition, Start, End, Code
    // =========================================================================
    @Test
    @DisplayName("Workflow: 各类型节点执行器边界与运算")
    void testWorkflowNodes() {
        WorkflowContext ctx = WorkflowContext.builder()
                .variables(new HashMap<>(Map.of("score", 85, "role", "admin")))
                .build();

        // 1. ConditionNodeExecutor
        ConditionNodeExecutor conditionNode = new ConditionNodeExecutor();
        DagModel.DagNode condDef = DagModel.DagNode.builder()
                .id("cond_1")
                .type("CONDITION")
                .data(Map.of(
                        "variable", "score",
                        "operator", ">=",
                        "targetValue", 60
                ))
                .build();
        NodeExecutionResult condRes = conditionNode.execute(ctx, condDef).block();
        assertNotNull(condRes);

        // 2. StartNode & EndNode
        StartNodeExecutor startNode = new StartNodeExecutor();
        DagModel.DagNode startDef = DagModel.DagNode.builder().id("start").type("START").build();
        NodeExecutionResult startRes = startNode.execute(ctx, startDef).block();
        assertNotNull(startRes);

        EndNodeExecutor endNode = new EndNodeExecutor();
        DagModel.DagNode endDef = DagModel.DagNode.builder().id("end").type("END").build();
        NodeExecutionResult endRes = endNode.execute(ctx, endDef).block();
        assertNotNull(endRes);

        // 3. CodeNodeExecutor
        CodeNodeExecutor codeNode = new CodeNodeExecutor();
        DagModel.DagNode codeDef = DagModel.DagNode.builder()
                .id("code_1")
                .type("CODE")
                .data(Map.of("code", "output.put('result', 100);"))
                .build();
        NodeExecutionResult codeRes = codeNode.execute(ctx, codeDef).block();
        assertNotNull(codeRes);
    }

    // =========================================================================
    // 10. RAG Impl: RagServiceImpl
    // =========================================================================
    @Test
    @DisplayName("RAG: 数据集创建与三路检索组装")
    void testRagServices() {
        RagServiceImpl ragService = new RagServiceImpl(hybridSearchService, documentMapper);

        ChunkSearchResult searchResult = ChunkSearchResult.builder()
                .id(101L)
                .documentId(1L)
                .content("北京出差住宿标准上限 800 元/天")
                .score(0.85)
                .metadata("{\"pageNumber\":2,\"title\":\"差旅报销制度\"}")
                .build();

        when(hybridSearchService.hybridSearch(anyList(), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(searchResult));

        Document doc = new Document();
        doc.setId(1L);
        doc.setName("企业财务制度.pdf");
        when(documentMapper.selectById(1L)).thenReturn(doc);

        List<Citation> citations = ragService.retrieveCitations(List.of(1L), "北京住宿", 5, 0.4);
        assertNotNull(citations);
        assertEquals(1, citations.size());
        Citation c = citations.get(0);
        assertEquals("企业财务制度.pdf", c.getDocumentName());
        assertEquals("差旅报销制度", c.getSectionTitle());
        assertTrue(c.getPageNumber() >= 1);
    }

    // =========================================================================
    // 11. Remaining Service Entities and Utilities Coverage
    // =========================================================================
    @Test
    @DisplayName("Service Entities and Utilities Coverage")
    void testRemainingServiceEntitiesAndUtils() {
        // 1. CitationHighlightEngine
        CitationHighlightEngine engine = new CitationHighlightEngine();
        Citation c1 = Citation.builder().documentName("制度.pdf").pageNumber(1).snippet("北京出差住宿标准500元").build();
        List<CitationHighlightEngine.HighlightSpan> spans = engine.computeHighlights(
                "北京出差住宿标准500元。请妥善保管发票。", "北京出差住宿标准500元。请妥善保管发票。", "制度.pdf", 1);
        assertNotNull(spans);
        assertFalse(spans.isEmpty());

        CitationHighlightEngine.HighlightSpan span = CitationHighlightEngine.HighlightSpan.builder()
                .citationIndex(1)
                .documentName("制度.pdf")
                .pageNumber(1)
                .startOffset(0)
                .endOffset(10)
                .matchedSnippet("北京住宿")
                .build();
        assertEquals(1, span.getCitationIndex());
        assertEquals("制度.pdf", span.getDocumentName());

        // 2. CjkTokenizer
        assertTrue(CjkTokenizer.isCjk('中'));
        assertFalse(CjkTokenizer.isCjk('a'));
        assertFalse(CjkTokenizer.isCjk(' '));
        assertNotNull(CjkTokenizer.toWebSearchQuery("北京 住宿 2026"));
        assertEquals("", CjkTokenizer.toWebSearchQuery(null));
        assertEquals("", CjkTokenizer.toWebSearchQuery("   "));
        assertFalse(CjkTokenizer.extractKeywords("北京出差住宿报销标准").isEmpty());
        assertTrue(CjkTokenizer.extractKeywords(null).isEmpty());
        assertTrue(CjkTokenizer.extractKeywords("").isEmpty());
        assertEquals("''", CjkTokenizer.toTsQuery(null));
        assertEquals("''", CjkTokenizer.toTsQuery("   "));
        assertNotNull(CjkTokenizer.toTsQuery("北京 出差 住宿"));

        // 3. CachedEntry
        CachedEntry entry = CachedEntry.builder()
                .query("q")
                .response("缓存答案")
                .vectorStr("[]")
                .createTime(System.currentTimeMillis())
                .build();
        assertEquals("缓存答案", entry.getResponse());

        // 4. System Diagnostic
        CheckItem checkItem = CheckItem.builder()
                .component("Redis")
                .status("HEALTHY")
                .message("RTT 1ms")
                .latencyMs(1L)
                .build();
        assertEquals("Redis", checkItem.getComponent());
        DiagnosticReport report = DiagnosticReport.builder()
                .allPassed(true)
                .totalChecks(1)
                .passedChecks(1)
                .checkItems(List.of(checkItem))
                .checkedAt(java.time.LocalDateTime.now())
                .build();
        assertTrue(report.isAllPassed());

        // 异常诊断分支覆盖
        JdbcTemplate badJdbc = mock(JdbcTemplate.class);
        when(badJdbc.queryForObject(anyString(), eq(Integer.class))).thenThrow(new RuntimeException("DB down"));
        RedisTemplate<String, Object> badRedis = mock(RedisTemplate.class);
        when(badRedis.opsForValue()).thenThrow(new RuntimeException("Redis down"));

        SystemSelfCheckService failedCheck = new SystemSelfCheckService(badJdbc, badRedis);
        DiagnosticReport failedReport = failedCheck.runDiagnostics();
        assertNotNull(failedReport);
        assertFalse(failedReport.isAllPassed());

        // 5. TemplateMarketService.IndustryTemplate
        TemplateMarketService.IndustryTemplate template = TemplateMarketService.IndustryTemplate.builder()
                .templateId("legal_assistant")
                .name("法务合规助手")
                .category("LEGAL")
                .description("合同审查与风险提示")
                .build();
        assertEquals("legal_assistant", template.getTemplateId());

        // 6. AclPermissionContext
        AclPermissionContext acl = AclPermissionContext.builder()
                .userId(1001L)
                .deptId(10L)
                .roleIds(List.of(1L))
                .permissions(List.of("READ"))
                .isSuperAdmin(false)
                .build();
        assertEquals(1001L, acl.getUserId());
        assertFalse(acl.isSuperAdmin());

        // 7. DagModel and WorkflowContext Models
        DagModel.DagEdge edge = DagModel.DagEdge.builder()
                .id("e1")
                .source("start")
                .target("llm")
                .build();
        assertEquals("e1", edge.getId());

        WorkflowContext.NodeStepLog stepLog = WorkflowContext.NodeStepLog.builder()
                .nodeId("llm_1")
                .nodeName("LLM 生成")
                .status("SUCCESS")
                .durationMs(45L)
                .build();
        assertEquals("llm_1", stepLog.getNodeId());

        // 8. OrgDeptSyncItem
        OrgDeptSyncItem dept = OrgDeptSyncItem.builder()
                .externalDeptId(10L)
                .name("财务部")
                .parentId(0L)
                .build();
        assertEquals(10L, dept.getExternalDeptId());
        // 9. ACL & Market & SSO & System Coverage Boost
        AclPermissionContext acl2 = new AclPermissionContext();
        acl2.setUserId(2L);
        acl2.setDeptId(20L);
        acl2.setRoleIds(List.of(2L));
        acl2.setPermissions(List.of("WRITE"));
        acl2.setSuperAdmin(true);
        assertTrue(acl2.isSuperAdmin());
        assertEquals(20L, acl2.getDeptId());
        assertEquals(List.of(2L), acl2.getRoleIds());
        assertEquals(List.of("WRITE"), acl2.getPermissions());
        assertNotNull(acl2.toString());
        assertNotNull(acl.toString());
        assertNotEquals(acl, acl2);

        // TemplateMarketService
        TemplateMarketService marketService = new TemplateMarketService(mock(WorkflowDefinitionMapper.class), mock(AgentAppMapper.class));
        assertFalse(marketService.listTemplates().isEmpty());
        assertFalse(marketService.listTemplateVos().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> marketService.cloneTemplateToTenant("non_exist"));

        TemplateMarketService.IndustryTemplate tFull = TemplateMarketService.IndustryTemplate.builder()
                .templateId("t")
                .name("n")
                .category("c")
                .icon("i")
                .description("d")
                .valueProposition("v")
                .estimatedPrice("p")
                .dagJson("{}")
                .defaultPrompt("p")
                .build();
        assertNotNull(tFull.toString());
        assertEquals("i", tFull.getIcon());
        assertEquals("v", tFull.getValueProposition());
        assertEquals("p", tFull.getEstimatedPrice());
        assertEquals("{}", tFull.getDagJson());
        assertEquals("p", tFull.getDefaultPrompt());

        // SsoAuthService entities
        SsoAuthService.OrgUserSyncItem uItem = SsoAuthService.OrgUserSyncItem.builder()
                .externalUserId("ext_1")
                .username("test_user")
                .realName("测试")
                .email("test@agentforge.com")
                .phone("13800000000")
                .deptId(1L)
                .role("VIEWER")
                .build();
        assertEquals("ext_1", uItem.getExternalUserId());
        assertNotNull(uItem.toString());

        // Models
        DagModel dagModel = new DagModel();
        dagModel.setNodes(List.of());
        dagModel.setEdges(List.of());
        assertNotNull(dagModel.getNodes());
        assertNotNull(dagModel.getEdges());
        assertNotNull(dagModel.toString());

        DagModel.DagNode dNode = new DagModel.DagNode();
        dNode.setId("n");
        dNode.setName("name");
        dNode.setType("T");
        dNode.setData(Map.of());
        assertNotNull(dNode.toString());

        DagModel.DagEdge dEdge = new DagModel.DagEdge();
        dEdge.setId("e");
        dEdge.setSource("s");
        dEdge.setTarget("t");
        assertNotNull(dEdge.toString());

        WorkflowContext.NodeStepLog sLog = new WorkflowContext.NodeStepLog();
        sLog.setNodeId("n");
        sLog.setNodeName("nn");
        sLog.setStatus("OK");
        sLog.setDurationMs(1L);
        sLog.setErrorMsg("err");
        assertEquals("err", sLog.getErrorMsg());
        assertNotNull(sLog.toString());
    }
}