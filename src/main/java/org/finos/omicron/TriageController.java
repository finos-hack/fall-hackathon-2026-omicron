package org.finos.omicron;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.TaskService;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class TriageController {
    private static final Set<String> ALLOWED_DISPOSITIONS = Set.of(
            "close_as_false_positive", "escalate", "request_information");
    private static final Pattern REVIEWER_ID = Pattern.compile("[A-Za-z0-9._@-]{1,80}");

    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public TriageController(RuntimeService runtimeService, TaskService taskService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ready", "workflow", "Fluxnova BPMN and DMN");
    }

    @PostMapping("/triage")
    public TriageStarted startTriage(@RequestBody AlertRequest request) {
        if (request.caseId() == null || request.caseId().isBlank() || request.caseId().length() > 80) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "caseId is required");
        }
        if (request.depositCount24h() < 0 || request.depositTotal24hUsd() == null
                || request.depositTotal24hUsd().signum() < 0 || request.inputSha256() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid synthetic alert fields");
        }

        Map<String, Object> variables = Map.of(
                "caseId", request.caseId(),
                "depositCount24h", request.depositCount24h(),
                "depositTotal24hUsd", request.depositTotal24hUsd(),
                "screeningCandidateMatch", request.screeningCandidateMatch(),
                "inputSha256", request.inputSha256());
        ProcessInstance instance = runtimeService.startProcessInstanceByKey("amlAlertTriage", variables);
        String priority = String.valueOf(runtimeService.getVariable(instance.getId(), "riskTier"));
        Task task = taskService.createTaskQuery().processInstanceId(instance.getId()).singleResult();
        if (task == null || !"investigatorReview".equals(task.getTaskDefinitionKey())) {
            throw new IllegalStateException("The process must stop at the investigator task");
        }
        return new TriageStarted(
                request.caseId(), instance.getId(), priority, task.getId(), true,
                "AWAITING_HUMAN_REVIEW");
    }

    @PostMapping("/triage/tasks/{taskId}/review")
    public ReviewRecorded recordReview(
            @PathVariable("taskId") String taskId, @RequestBody ReviewRequest request) {
        if (request == null || request.reviewerId() == null
                || !REVIEWER_ID.matcher(request.reviewerId()).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reviewerId is required");
        }
        if (request.disposition() == null || !ALLOWED_DISPOSITIONS.contains(request.disposition())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only a human review disposition is supported; report filing is outside this demo");
        }
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null || !"investigatorReview".equals(task.getTaskDefinitionKey())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Investigator task not found");
        }
        taskService.setAssignee(taskId, request.reviewerId());
        taskService.complete(taskId, Map.of(
                "reviewerId", request.reviewerId(),
                "disposition", request.disposition()));
        return new ReviewRecorded(taskId, request.reviewerId(), request.disposition(), "HUMAN_REVIEW_RECORDED");
    }

    public record AlertRequest(
            String caseId,
            int depositCount24h,
            BigDecimal depositTotal24hUsd,
            boolean screeningCandidateMatch,
            String inputSha256) {}

    public record TriageStarted(
            String caseId,
            String processInstanceId,
            String priority,
            String investigatorTaskId,
            boolean humanReviewRequired,
            String status) {}

    public record ReviewRequest(String reviewerId, String disposition) {}

    public record ReviewRecorded(String taskId, String reviewerId, String disposition, String status) {}
}
