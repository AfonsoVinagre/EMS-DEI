package pt.ulisboa.tecnico.rnl.dei.ems.audit.domain;

/**
 * The critical actions recorded in the immutable audit log (Registo de Auditoria
 * Inviolável). Each maps to a hook at the point the action happens, so the log
 * captures who did what, when, and (for grade changes) the before/after values.
 */
public enum AuditAction {
	EXAM_UPLOAD,
	EXAM_DISTRIBUTE,
	EXAM_RELEASE,
	GRADE_SUBMIT,
	REVISION_REQUEST,
	REVISION_PROCESS,
	CONSULTATION_REQUEST,
	CONSULTATION_APPROVE,
	CONSULTATION_REJECT,
	CONSULTATION_AUTO_APPROVE,
	PDF_ACCESS
}