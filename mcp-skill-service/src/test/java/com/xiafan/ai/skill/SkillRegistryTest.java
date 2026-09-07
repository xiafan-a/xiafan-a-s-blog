package com.xiafan.ai.skill;

import com.xiafan.ai.config.SkillProperties;
import com.xiafan.ai.persistence.RecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SkillRegistryTest {

    @Mock
    private RecordRepository records;

    private SkillRegistry registry;

    @BeforeEach
    void setUp() {
        SkillProperties props = new SkillProperties();
        String root = Path.of(System.getProperty("basedir", "."), "src/test/resources/skills")
                .toAbsolutePath().toString();
        props.setRoot(root);
        registry = new SkillRegistry(props, records);
        when(records.findSkillEnabled(anyString())).thenReturn(Optional.empty());
        registry.initialize();
    }

    @Test
    void loadsAndAppliesSkillFromMarkdownDirectory() {
        assertThat(registry.list(false)).hasSize(1);

        SkillDefinition skill = registry.getRequired("sample-helper");
        assertThat(skill.description()).contains("Sample skill");
        assertThat(skill.instructions()).contains("bullets");

        SkillApplyResponse applied = registry.apply(
                "sample-helper",
                "Write a summary",
                List.of(Map.of("role", "user", "content", "previous message")));
        assertThat(applied.systemPrompt()).contains("sample-helper");
        assertThat(applied.preparedMessages()).hasSize(2);
    }
}
