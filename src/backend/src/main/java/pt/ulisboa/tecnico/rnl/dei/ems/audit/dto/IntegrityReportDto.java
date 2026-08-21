package pt.ulisboa.tecnico.rnl.dei.ems.audit.dto;

/**
 * Result of verifying the audit log's hash chain
 * ({@code GET /audit-logs/verify}). {@code intact} is true when every entry's
 * stored {@code entryHash} matches a recomputation from its {@code prevHash}
 * and payload, and every {@code prevHash} equals the previous entry's
 * {@code entryHash} (the first must equal the genesis hash). When broken,
 * {@code brokenAtId} is the id of the first entry whose hash no longer
 * matches — the point of suspected tampering.
 */
public record IntegrityReportDto(
		boolean intact,
		Long brokenAtId,
		long total,
		String message) {
}