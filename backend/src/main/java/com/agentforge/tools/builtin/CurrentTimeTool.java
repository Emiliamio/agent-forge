package com.agentforge.tools.builtin;

import com.agentforge.tools.BaseTool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 内置当前时间与日期查询工具
 */
@Component
public class CurrentTimeTool implements BaseTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEEE");

    @Override
    public String getName() {
        return "current_time";
    }

    @Override
    public String getDescription() {
        return "获取当前系统的精确标准北京时间、年月日、时分秒以及星期信息。无需入参。";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.emptyMap());
        return schema;
    }

    @Override
    public String execute(Map<String, Object> params) {
        return "当前精确系统时间为: " + LocalDateTime.now().format(FORMATTER);
    }
}
