package pt.ulisboa.tecnico.rnl.dei.ems.correction.dto;

/**
 * Body for PATCH /correction-tasks/{id}/score — only the grade. No
 * justification field by product decision.
 */
public record SubmitGradeRequest(Double grade) {
}
