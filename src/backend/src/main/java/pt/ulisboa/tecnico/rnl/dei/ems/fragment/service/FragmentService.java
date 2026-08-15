package pt.ulisboa.tecnico.rnl.dei.ems.fragment.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.repository.CorrectionTaskRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.DEIException;
import pt.ulisboa.tecnico.rnl.dei.ems.exceptions.ErrorMessage;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain.Fragment;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.dto.FragmentDto;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.repository.FragmentRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person.PersonType;
import pt.ulisboa.tecnico.rnl.dei.ems.security.SecurityUtils;

/**
 * Fragment reads are role-filtered:
 *
 * <ul>
 *   <li>ADMINISTRATOR / SCHOOL_STAFF: see everything.</li>
 *   <li>TEACHER: only see fragments where they have at least one task.</li>
 *   <li>STUDENT: none in this phase.</li>
 * </ul>
 */
@Service
@Transactional
public class FragmentService {

	@Autowired
	private FragmentRepository fragmentRepository;

	@Autowired
	private CorrectionTaskRepository correctionTaskRepository;

	@Value("${ems.storage.fragments-dir:./data/fragments}")
	private String fragmentsDir;

	private String computeStatus(List<CorrectionTask> tasks) {
		if (tasks.isEmpty()) {
			return "UNASSIGNED";
		}
		// Latest task by assignedAt; if a REVISION exists we are in revision.
		CorrectionTask latest = tasks.stream()
				.max((a, b) -> a.getAssignedAt().compareTo(b.getAssignedAt()))
				.orElse(tasks.get(tasks.size() - 1));
		if (latest.getStatus() == CorrectionTask.Status.SUBMITTED) {
			return tasks.stream().anyMatch(t -> t.getPhase() == CorrectionTask.Phase.REVISION)
					? "REVISION_SUBMITTED"
					: "CORRECTED";
		}
		return latest.getPhase() == CorrectionTask.Phase.REVISION ? "IN_REVISION" : "IN_CORRECTION";
	}

	/**
	 * The grade of the fragment's corrected PRIMARY task — i.e. the grade the
	 * assigned teacher submitted. Null while the fragment is not yet
	 * corrected (no SUBMITTED PRIMARY task), so the UI renders "--".
	 */
	private Double computeGrade(List<CorrectionTask> tasks) {
		return tasks.stream()
				.filter(t -> t.getPhase() == CorrectionTask.Phase.PRIMARY
						&& t.getStatus() == CorrectionTask.Status.SUBMITTED)
				.map(CorrectionTask::getGrade)
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	private void populateStatusAndGrade(Fragment fragment) {
		List<CorrectionTask> tasks = correctionTaskRepository.findByFragmentId(fragment.getId());
		fragment.setStatus(computeStatus(tasks));
		fragment.setGrade(computeGrade(tasks));
	}

	private boolean currentMaySee(Fragment fragment) {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			return false;
		}
		PersonType type = current.getType();
		if (type == PersonType.ADMINISTRATOR) {
			return true;
		}
		if (type == PersonType.SCHOOL_STAFF) {
			if (current.getSchool() == null) {
				return false;
			}
			return fragment.getExam().getSchool().getId()
					.equals(current.getSchool().getId());
		}
		if (type == PersonType.TEACHER) {
			return correctionTaskRepository.findByFragmentId(fragment.getId()).stream()
					.anyMatch(t -> t.getTeacher().getId().equals(current.getId()));
		}
		return false;
	}

	@Transactional
	public List<FragmentDto> listAll() {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		List<Fragment> all = fragmentRepository.findAll();
		return all.stream()
				.filter(this::currentMaySee)
				.map(f -> {
					populateStatusAndGrade(f);
					return new FragmentDto(f, f.getStatus(), f.getGrade());
				})
				.toList();
	}

	@Transactional
	public FragmentDto get(long id) {
		Fragment fragment = fragmentRepository.findById(id)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_FRAGMENT, Long.toString(id)));
		if (!currentMaySee(fragment)) {
			throw new DEIException(ErrorMessage.NO_SUCH_FRAGMENT, Long.toString(id));
		}
		populateStatusAndGrade(fragment);
		return new FragmentDto(fragment, fragment.getStatus(), fragment.getGrade());
	}

	@Transactional
	public List<FragmentDto> listByExam(long examId) {
		List<Fragment> fragments = fragmentRepository.findByExamId(examId);
		return fragments.stream()
				.filter(this::currentMaySee)
				.map(f -> {
					populateStatusAndGrade(f);
					return new FragmentDto(f, f.getStatus(), f.getGrade());
				})
				.toList();
	}

	public Path resolveImagePath(Fragment fragment) {
		Path image = Paths.get(fragmentsDir).resolve(fragment.getImagePath());
		if (!Files.exists(image)) {
			throw new DEIException(ErrorMessage.FRAGMENT_IMAGE_NOT_FOUND);
		}
		return image;
	}

	public InputStream openImage(Fragment fragment) throws IOException {
		return Files.newInputStream(resolveImagePath(fragment));
	}
}
