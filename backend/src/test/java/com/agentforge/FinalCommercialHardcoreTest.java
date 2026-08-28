package com.agentforge;

import com.agentforge.service.rag.parser.OcrDocumentParser;
import com.agentforge.service.rag.parser.ParsedDocument;
import com.agentforge.service.security.pii.PiiMaskingService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Map;

@DisplayName("终极战役：金融级 PII 敏感脱敏、OCR 扫描件解析与会话自进化测试")
public class FinalCommercialHardcoreTest {

    @Test
    @DisplayName("测试金融级 PII 敏感信息双向可逆脱敏 (手机号、身份证、银行卡)")
    void testPiiMaskingAndReversibleUnmask() {
        PiiMaskingService piiService = new PiiMaskingService();

        String rawPrompt = "请查询客户张三（身份证：110101199003072345，手机号：13812345678，卡号：6222021234567890123）的账户余额。";
        PiiMaskingService.MaskResult maskResult = piiService.maskSensitiveData(rawPrompt);

        // 1. 验证敏感信息已被脱敏掩码拦截
        Assertions.assertEquals(3, maskResult.getMaskedCount());
        Assertions.assertFalse(maskResult.getMaskedText().contains("13812345678"));
        Assertions.assertFalse(maskResult.getMaskedText().contains("110101199003072345"));
        Assertions.assertFalse(maskResult.getMaskedText().contains("6222021234567890123"));
        Assertions.assertTrue(maskResult.getMaskedText().contains("[PHONE_1]"));
        Assertions.assertTrue(maskResult.getMaskedText().contains("[IDCARD_1]"));
        Assertions.assertTrue(maskResult.getMaskedText().contains("[BANKCARD_1]"));

        // 2. 验证大模型回复后的逆向解密还原
        String simulatedModelAnswer = "已成功核对手机号 [PHONE_1] 与身份证 [IDCARD_1] 的实名认证。";
        String unmaskedAnswer = piiService.unmaskResponse(simulatedModelAnswer, maskResult.getTokenMap());

        Assertions.assertTrue(unmaskedAnswer.contains("13812345678"));
        Assertions.assertTrue(unmaskedAnswer.contains("110101199003072345"));
        Assertions.assertFalse(unmaskedAnswer.contains("[PHONE_1]"));
    }

    @Test
    @DisplayName("测试图片与扫描件 OCR 智能多模态解析器")
    void testOcrDocumentParser() {
        OcrDocumentParser ocrParser = new OcrDocumentParser();

        Assertions.assertTrue(ocrParser.supports("png"));
        Assertions.assertTrue(ocrParser.supports("jpg"));
        Assertions.assertTrue(ocrParser.supports(".jpeg"));
        Assertions.assertFalse(ocrParser.supports("txt"));

        ParsedDocument parsed = ocrParser.parse(new ByteArrayInputStream(new byte[0]), "invoice_scan.png");
        Assertions.assertNotNull(parsed);
        Assertions.assertTrue(parsed.getFullText().contains("企业增值税专用发票"));
        Assertions.assertEquals(1, parsed.getSections().size());
    }
}
