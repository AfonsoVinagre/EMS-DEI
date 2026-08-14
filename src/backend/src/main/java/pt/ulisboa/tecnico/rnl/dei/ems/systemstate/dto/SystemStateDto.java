package pt.ulisboa.tecnico.rnl.dei.ems.systemstate.dto;

import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.domain.SystemState;

public record SystemStateDto(boolean examsLocked, boolean distributed) {
	public SystemStateDto(SystemState state) {
		this(state.isExamsLocked(), state.isDistributed());
	}
}
