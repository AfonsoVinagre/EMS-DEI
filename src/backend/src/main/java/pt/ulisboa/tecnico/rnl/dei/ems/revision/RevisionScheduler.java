package pt.ulisboa.tecnico.rnl.dei.ems.revision;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import pt.ulisboa.tecnico.rnl.dei.ems.revision.service.RevisionService;

/**
 * Closes revision windows automatically and per-exam. Each released exam has
 * its own deadline (releasedAt + windowHours); this scheduler periodically
 * finds exams whose deadline has passed and redistributes their targeted
 * fragments as {@code REVISION} correction tasks. Failing exams are retried on
 * the next tick — the scheduler never dies.
 */
@Component
public class RevisionScheduler {

	private static final Logger logger = LoggerFactory.getLogger(RevisionScheduler.class);

	private final RevisionService revisionService;

	public RevisionScheduler(RevisionService revisionService) {
		this.revisionService = revisionService;
	}

	@Scheduled(fixedDelayString = "${ems.revision.scheduler-interval-ms:60000}")
	public void closeWindows() {
		try {
			revisionService.processClosedWindows();
		} catch (RuntimeException e) {
			// Defensive: a single tick must never crash the scheduler thread.
			logger.warn("Revision scheduler tick failed: {}", e.getMessage());
		}
	}
}