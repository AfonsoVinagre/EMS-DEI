package pt.ulisboa.tecnico.rnl.dei.ems.school.dto;

import pt.ulisboa.tecnico.rnl.dei.ems.school.domain.School;

public record SchoolDto(long id, String name, String code, String region) {
	public SchoolDto(School school) {
		this(school.getId(), school.getName(), school.getCode(), school.getRegion());
	}
}
