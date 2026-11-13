package org.finos.ai.demo.afc;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** HTTP surface for the AML alert-triage use case. */
@RestController
@RequestMapping("/api/afc")
public class AfcDemoController {

  private final AfcDemoService demoService;
  private final AfcEvidencePdfRenderer pdfRenderer;

  public AfcDemoController(AfcDemoService demoService, AfcEvidencePdfRenderer pdfRenderer) {
    this.demoService = demoService;
    this.pdfRenderer = pdfRenderer;
  }

  @PostMapping("/alerts")
  public Map<String, Object> start(@RequestBody AfcAlert alert) throws Exception {
    String id = demoService.start(alert);
    return Map.of("processInstanceId", id, "processDefinitionKey", AfcDemoService.PROCESS_KEY);
  }

  @GetMapping("/instances/{processInstanceId}/status")
  public Map<String, Object> status(@PathVariable String processInstanceId) {
    return demoService.status(processInstanceId);
  }

  @GetMapping("/tasks")
  public Map<String, Object> tasks() {
    return Map.of("tasks", demoService.openTasks());
  }

  @PostMapping("/tasks/{taskId}/complete")
  public Map<String, Object> complete(
      @PathVariable String taskId,
      @RequestParam(name = "decision", required = false) String decision,
      @RequestParam(name = "reviewer", required = false) String reviewer,
      @RequestParam(name = "rationale", required = false) String rationale) {
    demoService.completeTask(taskId, decision, reviewer, rationale);
    return Map.of("taskId", taskId, "status", "completed");
  }

  @GetMapping("/instances/{processInstanceId}/evidence.pdf")
  public ResponseEntity<byte[]> evidence(@PathVariable String processInstanceId) {
    Map<String, Object> status = demoService.status(processInstanceId);
    @SuppressWarnings("unchecked")
    Map<String, Object> variables = (Map<String, Object>) status.get("variables");
    ByteArrayOutputStream bytes =
        pdfRenderer.render(processInstanceId, String.valueOf(status.get("state")), variables);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_PDF);
    headers.setContentDisposition(
        ContentDisposition.attachment().filename("afc-evidence-" + processInstanceId + ".pdf").build());
    return ResponseEntity.ok().headers(headers).body(bytes.toByteArray());
  }
}
