package com.agentforge.tools;

import java.util.Map;

/**
 * 智能体工具统一接口 (支持 Function Calling 协议与 OpenAPI 3.0 工具动态挂载)
 */
public interface BaseTool {

    /**
     * 工具唯一标识名称 (如 calculator, web_search, current_time)
     */
    String getName();

    /**
     * 工具中文功能描述 (供大模型阅读并决定何时调用)
     */
    String getDescription();

    /**
     * 工具参数 JSON Schema 规范
     */
    Map<String, Object> getParametersSchema();

    /**
     * 执行工具具体业务逻辑
     *
     * @param params 模型解析出的入参字典
     * @return 工具执行输出的纯文本/JSON结果
     */
    String execute(Map<String, Object> params);
}
