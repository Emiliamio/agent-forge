package com.agentforge.security;

import com.agentforge.exception.BusinessException;
import com.agentforge.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * 挂件签名令牌工具类
 * 格式：v1.<exp>.<nonce>.<hmacBase64Url>
 */
@Slf4j
public final class SignedWidgetToken {

    public static final String PREFIX = "v1.";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long CLOCK_SKEW_TOLERANCE_SECONDS = 30L;

    private SignedWidgetToken() {
    }

    public static boolean isSignedToken(String token) {
        return token != null && token.startsWith(PREFIX);
    }

    public static String sign(String secretKey, long exp, String nonce) {
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new IllegalArgumentException("Secret key must not be empty");
        }
        if (nonce == null || nonce.trim().isEmpty()) {
            throw new IllegalArgumentException("Nonce must not be empty");
        }
        String payload = "v1." + exp + "." + nonce;
        byte[] hmacBytes = calculateHmac(secretKey, payload);
        String hmacBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        return payload + "." + hmacBase64Url;
    }

    public static void verify(String token, String secretKey, ReplayGuard replayGuard) {
        if (!isSignedToken(token)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证 (Token) 格式无效");
        }
        if (secretKey == null || secretKey.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "智能体服务端密钥未配置");
        }

        String[] parts = token.split("\\.");
        if (parts.length != 4) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证 (Token) 格式无效");
        }

        String expStr = parts[1];
        String nonce = parts[2];
        String providedSig = parts[3];

        long exp;
        try {
            exp = Long.parseLong(expStr);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证过期时间格式无效");
        }

        long nowSeconds = System.currentTimeMillis() / 1000L;
        if (nowSeconds > exp + CLOCK_SKEW_TOLERANCE_SECONDS) {
            log.warn("🛑 [WIDGET_TOKEN_EXPIRED] 令牌已过期：now={}, exp={}", nowSeconds, exp);
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证已过期");
        }

        if (nonce.isEmpty() || nonce.length() > 64) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证随机数无效");
        }

        if (replayGuard != null) {
            replayGuard.checkAndRecord(nonce, exp);
        }

        String payload = "v1." + expStr + "." + nonce;
        byte[] expectedHmac = calculateHmac(secretKey, payload);
        String expectedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedHmac);

        boolean signatureMatches = MessageDigest.isEqual(
                expectedSig.getBytes(StandardCharsets.UTF_8),
                providedSig.getBytes(StandardCharsets.UTF_8)
        );

        if (!signatureMatches) {
            log.warn("🛑 [WIDGET_TOKEN_SIGNATURE_INVALID] 签名校验失败");
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "挂件访问凭证签名校验失败");
        }
    }

    private static byte[] calculateHmac(String secretKey, String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256", e);
        }
    }
}
