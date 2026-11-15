package org.finos.omicron;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Map;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.TaskService;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FluxnovaProcessTest {
    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Test
    void highPriorityAlertWaitsForHumanDecision() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("amlAlertTriage", Map.of(
                "caseId", "SYNTHETIC-HIGH-001",
                "depositCount24h", 4,
                "depositTotal24hUsd", new BigDecimal("20000"),
                "screeningCandidateMatch", false,
                "inputSha256", "e41b2fc8d99a"));

        assertEquals("HIGH", runtimeService.getVariable(instance.getId(), "riskTier"));
        Task task = taskService.createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertNotNull(task);
        assertEquals("investigatorReview", task.getTaskDefinitionKey());
        assertNull(runtimeService.getVariable(instance.getId(), "disposition"));

        taskService.complete(task.getId(), Map.of(
                "reviewerId", "test-investigator",
                "disposition", "escalate"));
        assertTrue(runtimeService.createProcessInstanceQuery()
                .processInstanceId(instance.getId()).singleResult() == null);
    }

    @Test
    void lowerPriorityAlertStillWaitsForHumanDecision() {
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("amlAlertTriage", Map.of(
                "caseId", "SYNTHETIC-LOW-001",
                "depositCount24h", 1,
                "depositTotal24hUsd", new BigDecimal("2000"),
                "screeningCandidateMatch", false,
                "inputSha256", "72af40d22b5f"));

        assertEquals("LOW", runtimeService.getVariable(instance.getId(), "riskTier"));
        Task task = taskService.createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertNotNull(task);
        assertEquals("investigatorReview", task.getTaskDefinitionKey());
        assertNull(runtimeService.getVariable(instance.getId(), "disposition"));
    }
}
