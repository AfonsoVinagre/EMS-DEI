package pt.ulisboa.tecnico.rnl.dei.ems.subject.dto;

import pt.ulisboa.tecnico.rnl.dei.ems.subject.domain.Subject;

public record SubjectDto(long id, String name, String code) {
	public SubjectDto(Subject subject) {
		this(subject.getId(), subject.getName(), subject.getCode());
	}
}
