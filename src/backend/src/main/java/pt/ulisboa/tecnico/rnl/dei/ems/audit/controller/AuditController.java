package pt.ulisboa.tecnico.rnl.dei.ems.audit.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import pt.ulisboa.tecnico.rnl.dei.ems.audit.dto.AuditLogDto;
import pt.ulisboa.tecnico.rnl.dei.ems.audit.dto.IntegrityReportDto;
import pt.ulisboa.tecnico.rnl.dei.ems.audit.service.AuditService;

/**
 * Admin-only endpoints for the immutable audit log (Registo de Auditoria
 * Inviolável). {@code AUDIT_READ} is granted only to administrators, so staff,
 * teachers and students get a 403 — the log is consulted by the admin when
 * resolving revision disputes.
 */
@RestController
public class AuditController {

	@Autowired
	private AuditService auditService;

	/** All audit entries, newest first. */
	@GetMapping("/audit-logs")
	@PreAuthorize("hasAuthority('AUDIT_READ')")
	public List<AuditLogDto> list() {
		return auditService.list();
	}

	/** Verifies the hash chain — detects any in-database tampering. */
	@GetMapping("/audit-logs/verify")
	@PreAuthorize("hasAuthority('AUDIT_READ')")
	public IntegrityReportDto verify() {
		return auditService.verifyChain();
	}
}