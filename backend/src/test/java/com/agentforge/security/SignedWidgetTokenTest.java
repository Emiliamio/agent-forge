package com.agentforge.security;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("挂件签名令牌 (SignedWidgetToken) 与防重放 (ReplayGuard) 安全测试")
public class SignedWidgetTokenTest {

    private static final String SECRET_KEY = "test-secret-key-123456";
    private static final long DEFAULT_AGENT_ID = 1L;
    private static final long DEFAULT_TENANT_ID = 1L;

    @Mock
    private ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ReplayGuard replayGuard;

    @BeforeEach
    void setUp() {
        // 默认无 Redis，走内存 LRU 降级
        replayGuard = new ReplayGuard(null);
    }

    @Test
    @DisplayName("1. 正常签名令牌校验通过")
    void testNormalSignedTokenPasses() {
        long exp = (System.currentTimeMillis() / 1000L) + 3600L;
        String nonce = "a1b2c3d4e5f6";
        String token = SignedWidgetToken.sign(SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, exp, nonce);

        assertTrue(SignedWidgetToken.isSignedToken(token));
        assertDoesNotThrow(() -> SignedWidgetToken.verify(token, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, replayGuard));
    }

    @Test
    @DisplayName("2. 过期令牌坚决拒绝 (超 30s 宽限期)")
    void testExpiredTokenRejected() {
        long exp = (System.currentTimeMillis() / 1000L) - 35L;
        String nonce = "expired123456";
        String token = SignedWidgetToken.sign(SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, exp, nonce);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                SignedWidgetToken.verify(token, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, replayGuard));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("过期"));
    }

    @Test
    @DisplayName("3. 允许 30 秒时钟漂移宽限期")
    void testClockSkewGracePeriod() {
        long exp = (System.currentTimeMillis() / 1000L) - 15L;
        String nonce = "grace12345678";
        String token = SignedWidgetToken.sign(SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, exp, nonce);

        assertDoesNotThrow(() -> SignedWidgetToken.verify(token, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, replayGuard));
    }

    @Test
    @DisplayName("4. 篡改 Signature 签名坚决拒绝")
    void testTamperedSignatureRejected() {
        long exp = (System.currentTimeMillis() / 1000L) + 3600L;
        String nonce = "tamper123456";
        String validToken = SignedWidgetToken.sign(SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, exp, nonce);

        String tamperedToken = validToken + "X";
        BusinessException ex = assertThrows(BusinessException.class, () ->
                SignedWidgetToken.verify(tamperedToken, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, replayGuard));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("签名校验失败"));
    }

    @Test
    @DisplayName("5. 篡改 Payload (exp / nonce) 导致签名不匹配")
    void testTamperedPayloadRejected() {
        long exp = (System.currentTimeMillis() / 1000L) + 3600L;
        String nonce = "payload123456";
        String validToken = SignedWidgetToken.sign(SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, exp, nonce);

        String[] parts = validToken.split("\\.");
        String tamperedToken = parts[0] + "." + (exp + 1000L) + "." + parts[2] + "." + parts[3];

        BusinessException ex = assertThrows(BusinessException.class, () ->
                SignedWidgetToken.verify(tamperedToken, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, replayGuard));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("签名校验失败"));
    }

    @Test
    @DisplayName("6. 内存 LRU 防重放：同一令牌第二次调用坚决拒绝")
    void testReplayAttackRejectedInMemory() {
        long exp = (System.currentTimeMillis() / 1000L) + 3600L;
        String nonce = "replay-nonce-001";
        String token = SignedWidgetToken.sign(SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, exp, nonce);

        // 第一次调用通过
        assertDoesNotThrow(() -> SignedWidgetToken.verify(token, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, replayGuard));

        // 第二次调用触发重放拦截
        BusinessException ex = assertThrows(BusinessException.class, () ->
                SignedWidgetToken.verify(token, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, replayGuard));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("重放拦截"));
    }

    @Test
    @DisplayName("7. Redis 分布式防重放：命中已存在 Nonce 坚决拒绝")
    void testReplayAttackWithRedis() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("widget:nonce:redis-nonce-001"), eq("1"), any(Duration.class)))
                .thenReturn(false);

        ReplayGuard redisReplayGuard = new ReplayGuard(redisTemplateProvider);
        long exp = (System.currentTimeMillis() / 1000L) + 3600L;
        String token = SignedWidgetToken.sign(SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, exp, "redis-nonce-001");

        BusinessException ex = assertThrows(BusinessException.class, () ->
                SignedWidgetToken.verify(token, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, redisReplayGuard));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("重放拦截"));
    }

    @Test
    @DisplayName("8. Redis 异常时平滑降级至本地内存 LRU 且不抛 500")
    void testRedisExceptionDegradesToMemoryLru() {
        when(redisTemplateProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection refused"));

        ReplayGuard fallbackGuard = new ReplayGuard(redisTemplateProvider);
        long exp = (System.currentTimeMillis() / 1000L) + 3600L;
        String token = SignedWidgetToken.sign(SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, exp, "redis-down-nonce");

        assertDoesNotThrow(() -> SignedWidgetToken.verify(token, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, fallbackGuard));
        BusinessException ex = assertThrows(BusinessException.class, () ->
                SignedWidgetToken.verify(token, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, fallbackGuard));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("重放拦截"));
    }

    @Test
    @DisplayName("9. 畸形 Token 格式边界拒绝")
    void testMalformedTokens() {
        assertFalse(SignedWidgetToken.isSignedToken("plaintext-secret-token"));
        assertFalse(SignedWidgetToken.isSignedToken(null));

        assertThrows(BusinessException.class, () -> SignedWidgetToken.verify("v1.abc", SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, replayGuard));
        assertThrows(BusinessException.class, () -> SignedWidgetToken.verify("v1.notanumber.nonce.sig", SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, replayGuard));
        assertThrows(BusinessException.class, () -> SignedWidgetToken.verify("v1.12345..sig", SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, replayGuard));
        assertThrows(BusinessException.class, () -> SignedWidgetToken.verify("not-v1", SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, replayGuard));
    }

    @Test
    @DisplayName("10. 伪造请求不得烧掉 nonce：篡改签名失败后，同一 nonce 的真实令牌仍必须通过")
    void forgeryMustNotBurnNonce() {
        String nonce = "0123456789abcdef";
        long exp = System.currentTimeMillis() / 1000 + 300;
        String valid = SignedWidgetToken.sign(SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, exp, nonce);
        String forged = valid.substring(0, valid.length() - 2) + "ZZ";

        ReplayGuard guard = new ReplayGuard(null);
        assertThrows(BusinessException.class,
                () -> SignedWidgetToken.verify(forged, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, guard));
        assertDoesNotThrow(() -> SignedWidgetToken.verify(valid, SECRET_KEY, DEFAULT_AGENT_ID, DEFAULT_TENANT_ID, guard));
    }

    @Test
    @DisplayName("11. 跨 Agent 伪造令牌坚决拒绝：Agent 1 签发的令牌校验 Agent 2 必须返回 401")
    void crossAgentTokenRejected() {
        long exp = (System.currentTimeMillis() / 1000L) + 3600L;
        String nonce = "cross-agent-nonce-1";
        String token = SignedWidgetToken.sign(SECRET_KEY, 1L, DEFAULT_TENANT_ID, exp, nonce);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                SignedWidgetToken.verify(token, SECRET_KEY, 2L, DEFAULT_TENANT_ID, replayGuard));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("签名校验失败"));
    }

    @Test
    @DisplayName("12. 跨 Tenant 伪造令牌坚决拒绝：Tenant 1 签发的令牌校验 Tenant 2 必须返回 401")
    void crossTenantTokenRejected() {
        long exp = (System.currentTimeMillis() / 1000L) + 3600L;
        String nonce = "cross-tenant-nonce-1";
        String token = SignedWidgetToken.sign(SECRET_KEY, DEFAULT_AGENT_ID, 1L, exp, nonce);

        BusinessException ex = assertThrows(BusinessException.class, () ->
                SignedWidgetToken.verify(token, SECRET_KEY, DEFAULT_AGENT_ID, 2L, replayGuard));
        assertEquals(ErrorCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("签名校验失败"));
    }
}
