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
import pt.ulisboa.tecnico.rnl.dei.ems.security.FragmentAuthorization;
import org.springframework.security.access.AccessDeniedException;

/**
 * Fragment reads are role-filtered:
 *
 * <ul>
 *   <li>ADMINISTRATOR / SCHOOL_STAFF: see everything (staff scoped to their
 *       school).</li>
 *   <li>TEACHER: only see fragments where they have at least one task.</li>
 *   <li>STUDENT: only see fragments of their own exams once the exam has been
 *       released (consultation access).</li>
 * </ul>
 */
@Service
@Transactional
public class FragmentService {

	@Autowired
	private FragmentRepository fragmentRepository;

	@Autowired
	private FragmentAuthorization fragmentAuthorization;

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
	 * The <em>definitive</em> grade of the fragment: the grade of its submitted
	 * {@code REVISION} task if any (the final grade set during revisão), falling
	 * back to the grade of its submitted {@code PRIMARY} task. Null while the
	 * fragment is not yet corrected (no SUBMITTED task), so the UI renders "--".
	 *
	 * <p>Kept in sync with {@code ExamService.definitiveGrade} so the per-fragment
	 * and per-exam views agree.
	 */
	private Double computeGrade(List<CorrectionTask> tasks) {
		Double revision = tasks.stream()
				.filter(t -> t.getPhase() == CorrectionTask.Phase.REVISION
						&& t.getStatus() == CorrectionTask.Status.SUBMITTED)
				.map(CorrectionTask::getGrade)
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.orElse(null);
		if (revision != null) {
			return revision;
		}
		return tasks.stream()
				.filter(t -> t.getPhase() == CorrectionTask.Phase.PRIMARY
						&& t.getStatus() == CorrectionTask.Status.SUBMITTED)
				.map(CorrectionTask::getGrade)
				.filter(java.util.Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	/**
	 * Computes the fragment's status and grade. For a TEACHER, only that
	 * teacher's own tasks on the fragment are considered — so a fragment whose
	 * REVISION was assigned to a *different* teacher does not show up as
	 * "Em revisão" for the teacher who only did the PRIMARY (and vice-versa).
	 * Admins/staff/students see the global view (all tasks).
	 */
	private void populateStatusAndGrade(Fragment fragment, Person current) {
		List<CorrectionTask> tasks = correctionTaskRepository.findByFragmentId(fragment.getId());
		if (current != null && current.getType() == PersonType.TEACHER) {
			long teacherId = current.getId();
			tasks = tasks.stream()
					.filter(t -> t.getTeacher().getId().equals(teacherId))
					.toList();
		}
		fragment.setStatus(computeStatus(tasks));
		fragment.setGrade(computeGrade(tasks));
	}


	@Transactional
	public List<FragmentDto> listAll() {
		Person current = SecurityUtils.currentPerson();
		if (current == null) {
			throw new DEIException(ErrorMessage.NOT_AUTHENTICATED);
		}
		List<Fragment> all = fragmentRepository.findAll();
		return all.stream()
				.filter(fragmentAuthorization::canReadFragment)
				.map(f -> {
					populateStatusAndGrade(f, current);
					return new FragmentDto(f, f.getStatus(), f.getGrade());
				})
				.toList();
	}

	@Transactional
	public FragmentDto get(long id) {
		Fragment fragment = fragmentRepository.findById(id)
				.orElseThrow(() -> new DEIException(ErrorMessage.NO_SUCH_FRAGMENT, Long.toString(id)));
		if (!fragmentAuthorization.canReadFragment(fragment)) {
			throw new DEIException(ErrorMessage.NO_SUCH_FRAGMENT, Long.toString(id));
		}
		populateStatusAndGrade(fragment, SecurityUtils.currentPerson());
		return new FragmentDto(fragment, fragment.getStatus(), fragment.getGrade());
	}

	@Transactional
	public List<FragmentDto> listByExam(long examId) {
		Person current = SecurityUtils.currentPerson();
		List<Fragment> fragments = fragmentRepository.findByExamId(examId);
		return fragments.stream()
				.filter(fragmentAuthorization::canReadFragment)
				.map(f -> {
					populateStatusAndGrade(f, current);
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

	/**
	 * Resolves the on-disk path of the <em>definitive</em> task's annotated PNG
	 * for a fragment — the annotation the student sees in consultation. The
	 * definitive task is the submitted {@code REVISION} task if any (the final
	 * grade set during revisão), falling back to the submitted {@code PRIMARY}
	 * task — the same selection as {@link #computeGrade}. Gated by the shared authorization dependency
	 * (teachers use their own task annotation; students see only own released exams).
	 *
	 * @throws DEIException {@link ErrorMessage#NO_SUCH_FRAGMENT} if the fragment
	 *     does not exist or the caller may not see it; {@link ErrorMessage#ANNOTATION_NOT_FOUND}
	 *     if the definitive task has no annotation (the frontend then falls
	 *     back to the raw fragment image).
	 */
	@Transactional
	public Path resolveDefinitiveAnnotatedImagePath(long fragmentId) {
		// Teachers access annotations through their own correction task instead.
		if (!fragmentAuthorization.canReadDefinitiveAnnotation(fragmentId)) {
			throw new AccessDeniedException("Fragment annotation access denied");
		}
		get(fragmentId);
		List<CorrectionTask> tasks = correctionTaskRepository.findByFragmentId(fragmentId);
		CorrectionTask definitive = tasks.stream()
				.filter(t -> t.getPhase() == CorrectionTask.Phase.REVISION
						&& t.getStatus() == CorrectionTask.Status.SUBMITTED)
				.findFirst()
				.orElseGet(() -> tasks.stream()
						.filter(t -> t.getPhase() == CorrectionTask.Phase.PRIMARY
								&& t.getStatus() == CorrectionTask.Status.SUBMITTED)
						.findFirst()
						.orElse(null));
		if (definitive == null || definitive.getAnnotatedImagePath() == null) {
			throw new DEIException(ErrorMessage.ANNOTATION_NOT_FOUND);
		}
		Path resolved = Paths.get(definitive.getAnnotatedImagePath());
		if (!Files.exists(resolved)) {
			throw new DEIException(ErrorMessage.ANNOTATION_NOT_FOUND);
		}
		return resolved;
	}
}
