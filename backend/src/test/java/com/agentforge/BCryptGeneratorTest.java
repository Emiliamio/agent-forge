package com.agentforge;

import cn.dev33.satoken.secure.BCrypt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("系统默认管理员 BCrypt 密码校验测试")
public class BCryptGeneratorTest {

    @Test
    @DisplayName("验证 seed-data.sql 中的管理员密码哈希与 admin123456 100% 匹配")
    void testAdminPasswordHashMatch() {
        String password = "admin123456";
        String seedHash = "$2a$10$VnTSObTDaYlHVoAUrU72Tu/ohEH3hdJxAw6VbNHGs1zavk..1wfi6";
        assertTrue(BCrypt.checkpw(password, seedHash), "seed-data.sql 中的管理员密码必须与 admin123456 匹配");
    }
}
