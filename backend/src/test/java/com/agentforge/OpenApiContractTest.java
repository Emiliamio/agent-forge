package com.agentforge;

import com.agentforge.support.LiveTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * OpenAPI 契约快照与防漂移回归测试 (R5 架构不变量机器验证)
 * 锁定对外暴露的 REST 接口签名、URL 路由、请求参数与响应模型，防止未授权接口变更破坏客户端兼容性
 */
@EnabledIf("com.agentforge.support.LiveTestSupport#usable")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DisplayName("R5: OpenAPI 契约快照与防漂移回归测试")
public class OpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("验证 /v3/api-docs 输出与 docs/openapi.json 规范化快照严格一致 (R5 门禁)")
    void testOpenApiContractSnapshot() throws Exception {
        String rawJson = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

        Object jsonTree = mapper.readValue(rawJson, Object.class);
        String normalizedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonTree);
        normalizedJson = normalizedJson.replace("\r\n", "\n").stripTrailing() + "\n";

        Path snapshotPath = resolveSnapshotPath();

        String updateEnv = System.getenv("UPDATE_OPENAPI");
        boolean updateMode = "1".equals(updateEnv) || "true".equalsIgnoreCase(updateEnv);

        if (updateMode || !Files.exists(snapshotPath)) {
            if (snapshotPath.getParent() != null) {
                Files.createDirectories(snapshotPath.getParent());
            }
            Files.writeString(snapshotPath, normalizedJson, StandardCharsets.UTF_8);
            System.out.println(">>> [OpenAPI Contract] Snapshot written to " + snapshotPath.toAbsolutePath());
            return;
        }

        String expectedJson = Files.readString(snapshotPath, StandardCharsets.UTF_8).replace("\r\n", "\n").stripTrailing() + "\n";

        if (!expectedJson.equals(normalizedJson)) {
            List<String> expectedLines = Arrays.asList(expectedJson.split("\n"));
            List<String> actualLines = Arrays.asList(normalizedJson.split("\n"));
            List<String> diffLines = new ArrayList<>();

            int maxLines = Math.max(expectedLines.size(), actualLines.size());
            for (int i = 0; i < maxLines && diffLines.size() < 40; i++) {
                String exp = i < expectedLines.size() ? expectedLines.get(i) : "<EOF>";
                String act = i < actualLines.size() ? actualLines.get(i) : "<EOF>";
                if (!exp.equals(act)) {
                    diffLines.add(String.format("L%d: [-] %s", i + 1, exp));
                    diffLines.add(String.format("L%d: [+] %s", i + 1, act));
                }
            }

            String diffOutput = String.join("\n", diffLines);
            System.err.println("=== OpenAPI Contract Drift Detected (First " + diffLines.size() + " lines diff) ===\n" + diffOutput);
            fail("OpenAPI contract does not match " + snapshotPath.toAbsolutePath() + " snapshot.\n"
                    + "If API signature changes are intentional, run with UPDATE_OPENAPI=1 to update baseline.\n"
                    + "Diff:\n" + diffOutput);
        }
    }

    private Path resolveSnapshotPath() {
        Path p = Paths.get("docs", "openapi.json");
        if (Files.exists(p) || Files.isDirectory(Paths.get("docs"))) {
            return p;
        }
        Path parentDocs = Paths.get("..", "docs", "openapi.json");
        if (Files.exists(parentDocs) || Files.isDirectory(Paths.get("..", "docs"))) {
            return parentDocs;
        }
        return p;
    }
}
