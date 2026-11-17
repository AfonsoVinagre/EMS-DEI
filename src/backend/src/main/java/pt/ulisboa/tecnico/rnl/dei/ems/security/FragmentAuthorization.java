package pt.ulisboa.tecnico.rnl.dei.ems.security;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pt.ulisboa.tecnico.rnl.dei.ems.correction.domain.CorrectionTask;
import pt.ulisboa.tecnico.rnl.dei.ems.correction.repository.CorrectionTaskRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.domain.Exam;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.domain.Fragment;
import pt.ulisboa.tecnico.rnl.dei.ems.fragment.repository.FragmentRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person.PersonType;

/** Resource permissions shared by endpoint guards and service list filters. */
@Component("fragmentAuthorization")
@Transactional(readOnly = true)
public class FragmentAuthorization {
    private final FragmentRepository fragments;
    private final CorrectionTaskRepository tasks;

    public FragmentAuthorization(FragmentRepository fragments, CorrectionTaskRepository tasks) {
        this.fragments = fragments;
        this.tasks = tasks;
    }

    public boolean canReadFragment(long id) {
        return fragments.findById(id).map(this::canReadFragment).orElse(false);
    }

    public boolean canReadFragment(Fragment fragment) {
        Person current = SecurityUtils.currentPerson();
        if (current == null) return false;
        return switch (current.getType()) {
            case ADMINISTRATOR -> true;
            case SCHOOL_STAFF -> sameSchool(current, fragment);
            case TEACHER -> tasks.findByFragmentId(fragment.getId()).stream()
                    .anyMatch(task -> ownsTask(current, task));
            case STUDENT -> fragment.getExam().getStudent().getId().equals(current.getId())
                    && fragment.getExam().getStatus() == Exam.ExamStatus.RELEASED;
        };
    }

    public boolean canReadTask(long id) {
        return tasks.findById(id).map(this::canReadTask).orElse(false);
    }

    public boolean canReadTask(CorrectionTask task) {
        Person current = SecurityUtils.currentPerson();
        if (current == null) return false;
        return switch (current.getType()) {
            case ADMINISTRATOR -> true;
            case SCHOOL_STAFF -> sameSchool(current, task.getFragment());
            case TEACHER -> ownsTask(current, task);
            case STUDENT -> false;
        };
    }

    public boolean canSubmitTask(long id) {
        Person current = SecurityUtils.currentPerson();
        return current != null && current.getType() == PersonType.TEACHER
                && tasks.findById(id).map(task -> ownsTask(current, task)).orElse(false);
    }

    public boolean canReadDefinitiveAnnotation(long id) {
        Person current = SecurityUtils.currentPerson();
        // Teachers use their own task's annotation endpoint, never another corrector's work.
        return current != null && current.getType() != PersonType.TEACHER && canReadFragment(id);
    }

    private boolean ownsTask(Person current, CorrectionTask task) {
        return task.getTeacher() != null && current.getId().equals(task.getTeacher().getId());
    }

    private boolean sameSchool(Person current, Fragment fragment) {
        return current.getSchool() != null && fragment.getExam().getSchool() != null
                && current.getSchool().getId().equals(fragment.getExam().getSchool().getId());
    }
}
