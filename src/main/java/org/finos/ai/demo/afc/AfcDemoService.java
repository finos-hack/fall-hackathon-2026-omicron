package org.finos.ai.demo.afc;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.finos.fluxnova.bpm.engine.HistoryService;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.TaskService;
import org.finos.fluxnova.bpm.engine.history.HistoricProcessInstance;
import org.finos.fluxnova.bpm.engine.history.HistoricVariableInstance;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.springframework.stereotype.Service;

/** Starts and queries AML alert-triage process instances. */
@Service
public class AfcDemoService {

  public static final String PROCESS_KEY = "AfcInvestigation_AI";

  private final RuntimeService runtimeService;
  private final TaskService taskService;
  private final HistoryService historyService;
  private final ObjectMapper objectMapper;

  public AfcDemoService(
      RuntimeService runtimeService,
      TaskService taskService,
      HistoryService historyService,
      ObjectMapper objectMapper) {
    this.runtimeService = runtimeService;
    this.taskService = taskService;
    this.historyService = historyService;
    this.objectMapper = objectMapper;
  }

  public String start(AfcAlert alert) throws Exception {
    String businessKey = "afc-" + alert.alertId() + "-" + Instant.now().toEpochMilli();
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("afcAlertJson", objectMapper.writeValueAsString(alert));
    variables.put("afcAlertId", alert.alertId());
    variables.put("afcCustomerId", alert.customerId());
    ProcessInstance instance =
        runtimeService.startProcessInstanceByKey(PROCESS_KEY, businessKey, variables);
    return instance.getProcessInstanceId();
  }

  public Map<String, Object> status(String processInstanceId) {
    ProcessInstance running = runtimeService.createProcessInstanceQuery()
        .processInstanceId(processInstanceId)
        .singleResult();
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("processInstanceId", processInstanceId);
    if (running != null) {
      out.put("state", "ACTIVE");
      out.put("currentActivityIds", runtimeService.getActiveActivityIds(processInstanceId));
      out.put("variables", variablesOf(processInstanceId, true));
    } else {
      HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
          .processInstanceId(processInstanceId)
          .singleResult();
      if (historic == null) {
        throw new IllegalArgumentException("Unknown process instance: " + processInstanceId);
      }
      out.put("state", historic.getEndTime() == null ? "ACTIVE" : "COMPLETED");
      out.put("startTime", historic.getStartTime());
      out.put("endTime", historic.getEndTime());
      out.put("variables", variablesOf(processInstanceId, false));
    }
    return out;
  }

  public List<Map<String, Object>> openTasks() {
    return taskService.createTaskQuery()
        .processDefinitionKey(PROCESS_KEY)
        .orderByTaskCreateTime()
        .asc()
        .list()
        .stream()
        .map(this::taskSummary)
        .toList();
  }

  public void completeTask(String taskId, String decision, String reviewer, String rationale) {
    Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
    if (task == null) {
      throw new IllegalArgumentException("Unknown task: " + taskId);
    }
    Map<String, Object> variables = new LinkedHashMap<>();
    variables.put("afcFinalDecision", decision == null ? "reviewed" : decision);
    variables.put("afcReviewer", reviewer == null ? "compliance-officer" : reviewer);
    variables.put("afcReviewRationale", rationale == null ? "" : rationale);
    taskService.complete(taskId, variables);
  }

  private Map<String, Object> taskSummary(Task task) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("taskId", task.getId());
    out.put("name", task.getName());
    out.put("processInstanceId", task.getProcessInstanceId());
    out.put("createTime", task.getCreateTime());
    out.put("candidateGroups", task.getTaskDefinitionKey());
    return out;
  }

  private Map<String, Object> variablesOf(String processInstanceId, boolean runtime) {
    Map<String, Object> variables = new LinkedHashMap<>();
    if (runtime) {
      variables.putAll(runtimeService.getVariables(processInstanceId));
    } else {
      for (HistoricVariableInstance v : historyService.createHistoricVariableInstanceQuery()
          .processInstanceId(processInstanceId)
          .list()) {
        variables.put(v.getVariableName(), v.getValue());
      }
    }
    variables.remove("afcAlertJson");
    return variables;
  }
}
