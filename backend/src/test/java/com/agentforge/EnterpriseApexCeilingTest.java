package com.agentforge;

import com.agentforge.service.agent.llm.arena.ModelArenaTrafficSplitter;
import com.agentforge.service.agent.sandbox.SecureCodeSandboxEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

@DisplayName("AgentForge 终极工业天花板测试：受限安全代码沙箱 + 多模型灰度金丝雀分流竞技场")
public class EnterpriseApexCeilingTest {

    @Test
    @DisplayName("测试受限安全代码沙箱执行器与超时看门狗 (SecureCodeSandboxEngine)")
    void testSecureCodeSandboxEngine() {
        SecureCodeSandboxEngine sandbox = new SecureCodeSandboxEngine();

        // 1. 测试高危命令拦截 (System.exit & ProcessBuilder)
        SecureCodeSandboxEngine.SandboxResult res1 = sandbox.execute("System.exit(0);", 1000);
        Assertions.assertFalse(res1.isSuccess());
        Assertions.assertTrue(res1.isSecurityBlocked());
        Assertions.assertTrue(res1.getViolationReason().contains("Restricted keyword"));

        SecureCodeSandboxEngine.SandboxResult res2 = sandbox.execute("new ProcessBuilder('calc.exe').start();", 1000);
        Assertions.assertFalse(res2.isSuccess());
        Assertions.assertTrue(res2.isSecurityBlocked());

        // 2. 测试合法安全算法运行
        SecureCodeSandboxEngine.SandboxResult resSafe = sandbox.execute("SUM_1_TO_N", 1000);
        Assertions.assertTrue(resSafe.isSuccess());
        Assertions.assertFalse(resSafe.isSecurityBlocked());
        Assertions.assertTrue(resSafe.getOutput().contains("Result=500500"));

        // 3. 测试超时看门狗强行中断
        SecureCodeSandboxEngine.SandboxResult resTimeout = sandbox.execute("INFINITE_LOOP_TEST", 200);
        Assertions.assertFalse(resTimeout.isSuccess());
        Assertions.assertTrue(resTimeout.getViolationReason().contains("timeout exceeded"));
    }

    @Test
    @DisplayName("测试多模型金丝雀分流与竞技场指标收集 (ModelArenaTrafficSplitter)")
    void testModelArenaTrafficSplitter() {
        ModelArenaTrafficSplitter splitter = new ModelArenaTrafficSplitter();

        // 配置候选：DeepSeek-V3 80% 权重，DeepSeek-R1 20% 权重
        splitter.setCandidates(Arrays.asList(
                new ModelArenaTrafficSplitter.ModelCandidate("deepseek-v3", 80),
                new ModelArenaTrafficSplitter.ModelCandidate("deepseek-r1", 20)
        ));

        // 1. 验证相同用户 ID 路由的确定性一致性 (Deterministic Hashing)
        String r1 = splitter.routeConsistent("tenant_alpha_user_99");
        String r2 = splitter.routeConsistent("tenant_alpha_user_99");
        Assertions.assertEquals(r1, r2);

        // 2. 模拟打点指标
        splitter.recordMetric("deepseek-v3", 120);
        splitter.recordMetric("deepseek-v3", 180);
        splitter.recordUserFeedback("deepseek-v3", true);

        ModelArenaTrafficSplitter.ModelMetrics m = splitter.getMetrics("deepseek-v3");
        Assertions.assertNotNull(m);
        Assertions.assertEquals(2, m.getTotalRequests());
        Assertions.assertEquals(150.0, m.getAvgLatencyMs());
        Assertions.assertEquals(1, m.getThumbsUp());
    }
}