package pt.ulisboa.tecnico.rnl.dei.ems.school.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.school.domain.School;
import pt.ulisboa.tecnico.rnl.dei.ems.school.dto.SchoolDto;
import pt.ulisboa.tecnico.rnl.dei.ems.school.repository.SchoolRepository;

// Service class for managing School entities
@Service
@Transactional
public class SchoolService {

	@Autowired
	private SchoolRepository schoolRepository;

	private School fetchSchoolOrThrow(long id) {
		return schoolRepository.findById(id)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_SCHOOL, Long.toString(id)));
	}

	@Transactional
	public List<SchoolDto> getSchools() {
		return schoolRepository.findAll().stream()
				.map(SchoolDto::new)
				.toList();
	}

	@Transactional
	public SchoolDto createSchool(SchoolDto schoolDto) {
		if (schoolRepository.existsByCode(schoolDto.code())) {
			throw new DEIException(ErrorMessage.SCHOOL_CODE_ALREADY_EXISTS, schoolDto.code());
		}
		School school = new School(schoolDto.name(), schoolDto.code(), schoolDto.region());
		return new SchoolDto(schoolRepository.save(school));
	}

	@Transactional
	public SchoolDto getSchool(long id) {
		return new SchoolDto(fetchSchoolOrThrow(id));
	}

	@Transactional
	public SchoolDto updateSchool(long id, SchoolDto schoolDto) {
		School school = fetchSchoolOrThrow(id);

		if (!school.getCode().equals(schoolDto.code())
				&& schoolRepository.existsByCode(schoolDto.code())) {
			throw new DEIException(ErrorMessage.SCHOOL_CODE_ALREADY_EXISTS, schoolDto.code());
		}

		school.setName(schoolDto.name());
		school.setCode(schoolDto.code());
		school.setRegion(schoolDto.region());
		return new SchoolDto(schoolRepository.save(school));
	}

	@Transactional
	public void deleteSchool(long id) {
		fetchSchoolOrThrow(id); // ensure exists
		schoolRepository.deleteById(id);
	}
}
