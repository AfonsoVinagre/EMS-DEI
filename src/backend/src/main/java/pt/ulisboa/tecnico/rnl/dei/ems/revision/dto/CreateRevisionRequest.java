package pt.ulisboa.tecnico.rnl.dei.ems.revision.dto;

import java.util.List;

/**
 * Body for {@code POST /revision-requests}: the exam to revise and the list of
 * targeted questions, each with the student's justification. The justification
 * lives on the request side only — never persisted on the correction task.
 */
public record CreateRevisionRequest(
		long examId,
		List<Item> items) {

	public record Item(long fragmentId, String justification) {
	}
}