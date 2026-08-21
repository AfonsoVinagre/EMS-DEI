package pt.ulisboa.tecnico.rnl.dei.ems.audit.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * An append-only entry in the immutable audit log (Registo de Auditoria
 * Inviolável). Each row records one critical action: who did it (actor
 * snapshot — id/email/name/type), what they did ({@link AuditAction}), the
 * target entity (type + id), a human-readable description, optional
 * before/after values, and the hash-chain fields that make the log
 * tamper-evident.
 *
 * <p><b>Inviolability</b> comes in two layers:
 * <ol>
 *   <li>The repository exposes no update/delete operations — only {@code save}.
 *       There is no mutation path in the API.</li>
 *   <li>A SHA-256 hash chain: {@code entryHash = sha256(prevHash || payload)}.
 *       Each entry stores the previous entry's hash, so any in-database
 *       tampering with a row breaks the chain and is detected by
 *       {@code AuditService.verifyChain()}.</li>
 * </ol>
 *
 * <p>The actor and target are stored as <b>snapshots</b> (denormalized strings
 * and ids), not {@code @ManyToOne} references: if a person or exam is later
 * deleted, the audit trail stays readable and intact.
 */
@Data
@Entity
@Table(name = "audit_log")
public class AuditLog {

	/** Genesis previous-hash for the first entry: 64 hex zeros. */
	public static final String GENESIS_HASH = "0".repeat(64);

	@Id
	@GeneratedValue
	private Long id;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	// Actor snapshot — null actorId means a system/scheduler action.
	@Column(name = "actor_id")
	private Long actorId;

	@Column(name = "actor_email")
	private String actorEmail;

	@Column(name = "actor_name")
	private String actorName;

	@Column(name = "actor_type")
	private String actorType;

	@Column(nullable = false)
	private String action;

	@Column(name = "target_type")
	private String targetType;

	@Column(name = "target_id")
	private Long targetId;

	@Column(nullable = false, length = 4000)
	private String description;

	@Column(name = "prev_value", length = 1000)
	private String prevValue;

	@Column(name = "new_value", length = 1000)
	private String newValue;

	@Column(name = "prev_hash", nullable = false)
	private String prevHash;

	@Column(name = "entry_hash", nullable = false)
	private String entryHash;

	protected AuditLog() {
	}

	public AuditLog(Instant occurredAt, Long actorId, String actorEmail, String actorName,
			String actorType, AuditAction action, String targetType, Long targetId,
			String description, String prevValue, String newValue,
			String prevHash, String entryHash) {
		this.occurredAt = occurredAt;
		this.actorId = actorId;
		this.actorEmail = actorEmail;
		this.actorName = actorName;
		this.actorType = actorType;
		this.action = action.name();
		this.targetType = targetType;
		this.targetId = targetId;
		this.description = description;
		this.prevValue = prevValue;
		this.newValue = newValue;
		this.prevHash = prevHash;
		this.entryHash = entryHash;
	}
}