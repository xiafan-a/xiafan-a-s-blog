package com.xiafan.ai;

import com.xiafan.ai.persistence.RecordRepository;
import com.xiafan.ai.skill.SkillRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AgentCapabilityApplicationTests {

    @Autowired
    private SkillRegistry registry;

    @Autowired
    private RecordRepository records;

    @Test
    void contextLoadsWithMcpAndSkills() {
        assertThat(registry.list(false)).isNotEmpty();
        assertThat(registry.getRequired("chinese-blog-writer").instructions()).contains("中文");
    }

    @Test
    void writesAndQueriesAuditRecords() {
        records.recordSkillUsage("sample-helper", "TEST", "APPLY", "hello", true, null, 1);
        records.recordMcpCall("blog-agent-capabilities", "list_skills", Map.of("x", "y"), "ok", true, null, 1);

        Map<String, Object> skillPage = records.querySkillUsage("sample-helper", "TEST", true, 1, 20);
        assertThat(skillPage.get("total")).isEqualTo(1);

        Map<String, Object> mcpPage = records.queryMcpCalls("list_skills", "blog-agent-capabilities", true, 1, 20);
        assertThat(mcpPage.get("total")).isEqualTo(1);
    }
}
