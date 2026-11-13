package org.finos.ai.demo.afc;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Renders the AFC governance evidence pack as a PDF. */
@Component
public class AfcEvidencePdfRenderer {

  private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
  private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
  private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, Color.BLACK);

  public ByteArrayOutputStream render(
      String processInstanceId, String state, Map<String, Object> variables) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document document = new Document(PageSize.A4, 42, 42, 42, 42);
    try {
      PdfWriter.getInstance(document, out);
      document.open();
      document.add(new Paragraph("AML Alert Triage - Governance Evidence Pack", TITLE));
      document.add(new Paragraph(
          "Omicron Track 2 (Governance-as-Code) - " + Instant.now(), BODY));
      document.add(new Paragraph(" "));
      document.add(new Paragraph("Process: " + processInstanceId + " (" + state + ")", BODY));

      document.add(new Paragraph("Decision trail", HEADING));
      PdfPTable table = new PdfPTable(2);
      table.setWidthPercentage(100);
      addRow(table, "Alert", str(variables, "afcAlertId"));
      addRow(table, "Rule score", str(variables, "afcRuleScore"));
      addRow(table, "Rule hits", str(variables, "afcRuleSummary"));
      addRow(table, "Agent recommendation (advisory)", str(variables, "afcAgentRecommendation"));
      addRow(table, "Agent rationale", str(variables, "afcAgentRationale"));
      addRow(table, "Policy path", str(variables, "afcPolicyPath"));
      addRow(table, "Policy citation", str(variables, "afcPolicyCitation"));
      addRow(table, "Gate rationale", str(variables, "afcGateRationale"));
      addRow(table, "Final decision", str(variables, "afcFinalDecision"));
      addRow(table, "Reviewer", str(variables, "afcReviewer"));
      addRow(table, "Review rationale", str(variables, "afcReviewRationale"));
      document.add(table);

      document.add(new Paragraph("Control coverage", HEADING));
      PdfPTable controls = new PdfPTable(2);
      controls.setWidthPercentage(100);
      addRow(controls, "mi-1 data leakage", "LLM egress redaction count: "
          + str(variables, "afcDlpRedactions"));
      addRow(controls, "mi-4 observability", "Token usage: "
          + str(variables, "afcPromptTokens") + " prompt / "
          + str(variables, "afcCompletionTokens") + " completion");
      addRow(controls, "mi-5 acceptance", "Deterministic gate invariants enforced by rule engine tests");
      addRow(controls, "mi-11 human feedback", "Human task UserTask_AfcComplianceReview");
      addRow(controls, "CALM architecture", "afc-alert-triage.calm.json");
      addRow(controls, "CCC crosswalk", "catalogs/ai-ml/gen-ai (FINOS Common Cloud Controls)");

      document.close();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to render AFC evidence PDF", e);
    }
    return out;
  }

  private static void addRow(PdfPTable table, String key, String value) {
    PdfPCell keyCell = new PdfPCell(new Paragraph(key, HEADING));
    keyCell.setBorderWidth(0.5f);
    PdfPCell valueCell = new PdfPCell(new Paragraph(value == null ? "" : value, BODY));
    valueCell.setBorderWidth(0.5f);
    valueCell.setHorizontalAlignment(Element.ALIGN_LEFT);
    table.addCell(keyCell);
    table.addCell(valueCell);
  }

  private static String str(Map<String, Object> variables, String key) {
    Object v = variables == null ? null : variables.get(key);
    return v == null ? "" : String.valueOf(v);
  }
}
