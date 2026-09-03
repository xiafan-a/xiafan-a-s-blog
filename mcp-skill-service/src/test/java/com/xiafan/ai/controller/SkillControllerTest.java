package com.xiafan.ai.controller;

import com.xiafan.ai.persistence.RecordRepository;
import com.xiafan.ai.skill.SkillApplyResponse;
import com.xiafan.ai.skill.SkillDefinition;
import com.xiafan.ai.skill.SkillRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SkillControllerTest {

    private static final SkillDefinition SKILL =
            new SkillDefinition("sample-helper", "Sample skill", "Keep answers short.", "test", true);

    @Mock
    private SkillRegistry registry;

    @Mock
    private RecordRepository records;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SkillController controller = new SkillController(registry, records);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void listsSkills() throws Exception {
        when(registry.list(false)).thenReturn(List.of(SKILL));

        mockMvc.perform(get("/api/v1/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills", hasSize(1)))
                .andExpect(jsonPath("$.skills[0].name").value("sample-helper"));
    }

    @Test
    void returnsNotFoundForUnknownSkill() throws Exception {
        when(registry.getRequired("missing"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "missing"));

        mockMvc.perform(get("/api/v1/skills/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void appliesSkill() throws Exception {
        when(registry.apply(eq("sample-helper"), eq("Write a summary"), any()))
                .thenReturn(new SkillApplyResponse(
                        SKILL,
                        "Use the sample-helper skill.",
                        List.of(Map.of("role", "user", "content", "Write a summary"))));

        mockMvc.perform(post("/api/v1/skills/sample-helper/apply")
                        .contentType("application/json")
                        .content("{\"userMessage\":\"Write a summary\",\"conversationHistory\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system_prompt").value("Use the sample-helper skill."));
    }
}
