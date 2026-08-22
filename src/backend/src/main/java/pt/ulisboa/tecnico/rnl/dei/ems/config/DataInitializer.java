package pt.ulisboa.tecnico.rnl.dei.ems.config;

import java.io.File;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import pt.ulisboa.tecnico.rnl.dei.ems.exam.repository.ExamRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.exam.service.ExamService;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person;
import pt.ulisboa.tecnico.rnl.dei.ems.person.domain.Person.PersonType;
import pt.ulisboa.tecnico.rnl.dei.ems.person.repository.PersonRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.school.domain.School;
import pt.ulisboa.tecnico.rnl.dei.ems.school.repository.SchoolRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.subject.domain.Subject;
import pt.ulisboa.tecnico.rnl.dei.ems.subject.repository.SubjectRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.domain.SystemState;
import pt.ulisboa.tecnico.rnl.dei.ems.systemstate.repository.SystemStateRepository;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.domain.TeacherAssignment;
import pt.ulisboa.tecnico.rnl.dei.ems.teacherassignment.repository.TeacherAssignmentRepository;

/**
 * Seeds the database with demo data on first start (only inserts rows that
 * are missing, so re-runs are safe and never overwrite existing data).
 *
 * DEV CONVENIENCE ONLY
 *
 * Schools:
 *   IST  Instituto Superior Técnico      (Lisboa)
 *   FCT  Faculdade de Ciências e Tecnologia (Lisboa)
 *
 * Subjects:
 *   ALG  Álgebra
 *   POO  Programação Orientada por Objectos
 *   FIS  Física
 *   MAT  Matemática
 *
 * People (all use password "password123" except the four @dei.tecnico
 * accounts which keep the original "admin123/staff123/teacher123/student123"):
 *   admin@dei.tecnico.ulisboa.pt        ADMINISTRATOR
 *   staff@dei.tecnico.ulisboa.pt        SCHOOL_STAFF  (Escola Demo / IST)
 *   teacher@dei.tecnico.ulisboa.pt      TEACHER       (Escola Demo / IST)
 *   student@dei.tecnico.ulisboa.pt      STUDENT       (Escola Demo / IST)
 *
 *   admin@ems.pt                        ADMINISTRATOR
 *   staff.ist@ems.pt                    SCHOOL_STAFF  (IST)
 *   staff.fct@ems.pt                    SCHOOL_STAFF  (FCT)
 *   ana.marques@ems.pt                  TEACHER       (IST, ALG + MAT)
 *   bruno.costa@ems.pt                  TEACHER       (IST, POO)
 *   carla.sousa@ems.pt                  TEACHER       (FCT, ALG + FIS)
 *   diogo.ramos@ems.pt                  TEACHER       (FCT, MAT + POO)
 *   ines.lopes@ems.pt                   STUDENT       (IST)
 *   joao.mendes@ems.pt                  STUDENT       (IST)
 *   marta.ferreira@ems.pt               STUDENT       (IST)
 *   pedro.almeida@ems.pt                STUDENT       (IST)
 *   beatriz.santos@ems.pt               STUDENT       (IST)
 *   rita.cardoso@ems.pt                 STUDENT       (FCT)
 *   tiago.nunes@ems.pt                  STUDENT       (FCT)
 *   sofia.reis@ems.pt                   STUDENT       (FCT)
 *   hugo.pinto@ems.pt                   STUDENT       (FCT)
 *   goncalo.pereira@ems.pt              STUDENT       (FCT)
 */
@Component
public class DataInitializer implements CommandLineRunner {

	private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

	private final PersonRepository personRepository;
	private final PasswordEncoder passwordEncoder;
	private final SystemStateRepository systemStateRepository;
	private final SchoolRepository schoolRepository;
	private final SubjectRepository subjectRepository;
	private final TeacherAssignmentRepository teacherAssignmentRepository;
	private final ExamService examService;
	private final ExamRepository examRepository;

	@Value("${ems.storage.exams-dir}")
	private String examsDir;

	public DataInitializer(PersonRepository personRepository, PasswordEncoder passwordEncoder,
			SystemStateRepository systemStateRepository, SchoolRepository schoolRepository,
			SubjectRepository subjectRepository, TeacherAssignmentRepository teacherAssignmentRepository,
			ExamService examService, ExamRepository examRepository) {
		this.personRepository = personRepository;
		this.passwordEncoder = passwordEncoder;
		this.systemStateRepository = systemStateRepository;
		this.schoolRepository = schoolRepository;
		this.subjectRepository = subjectRepository;
		this.teacherAssignmentRepository = teacherAssignmentRepository;
		this.examService = examService;
		this.examRepository = examRepository;
	}

	@Override
	public void run(String... args) {
		logger.info("DataInitializer: seeding demo data");
		School ist = upsertSchool("Instituto Superior Técnico",         "IST", "Lisboa");
		School fct = upsertSchool("Faculdade de Ciências e Tecnologia", "FCT", "Lisboa");
		School demo = upsertSchool("Escola Demo", "DEMO", "Lisboa");

		Subject alg = upsertSubject("Álgebra",                           "ALG");
		Subject poo = upsertSubject("Programação Orientada por Objectos", "POO");
		Subject fis = upsertSubject("Física",                            "FIS");
		Subject mat = upsertSubject("Matemática",                        "MAT");

		// Original demo accounts (kept for backwards compatibility).
		upsertPerson("Admin User",    "admin@dei.tecnico.ulisboa.pt",   "admin123",   PersonType.ADMINISTRATOR, null);
		upsertPerson("Staff User",    "staff@dei.tecnico.ulisboa.pt",   "staff123",   PersonType.SCHOOL_STAFF,  demo);
		upsertPerson("Teacher User",  "teacher@dei.tecnico.ulisboa.pt", "teacher123", PersonType.TEACHER,       demo);
		upsertPerson("Student User",  "student@dei.tecnico.ulisboa.pt", "student123", PersonType.STUDENT,       demo);

		// Rich seed (password is "password123" for all of them).
		upsertPerson("Admin Global",   "admin@ems.pt",     "password123", PersonType.ADMINISTRATOR, null);

		upsertPerson("Staff IST",      "staff.ist@ems.pt", "password123", PersonType.SCHOOL_STAFF,  ist);
		upsertPerson("Staff FCT",      "staff.fct@ems.pt", "password123", PersonType.SCHOOL_STAFF,  fct);

		Person ana   = upsertPerson("Ana Marques", "ana.marques@ems.pt", "password123", PersonType.TEACHER, ist);
		Person bruno = upsertPerson("Bruno Costa", "bruno.costa@ems.pt", "password123", PersonType.TEACHER, ist);
		Person carla = upsertPerson("Carla Sousa", "carla.sousa@ems.pt", "password123", PersonType.TEACHER, fct);
		Person diogo = upsertPerson("Diogo Ramos", "diogo.ramos@ems.pt", "password123", PersonType.TEACHER, fct);

		upsertTeacherAssignment(ana,   ist, alg);
		upsertTeacherAssignment(ana,   ist, mat);
		upsertTeacherAssignment(bruno, ist, poo);
		upsertTeacherAssignment(carla, fct, alg);
		upsertTeacherAssignment(carla, fct, fis);
		upsertTeacherAssignment(diogo, fct, mat);
		upsertTeacherAssignment(diogo, fct, poo);

		upsertPerson("Inês Lopes",     "ines.lopes@ems.pt",     "password123", PersonType.STUDENT, ist);
		upsertPerson("João Mendes",    "joao.mendes@ems.pt",    "password123", PersonType.STUDENT, ist);
		upsertPerson("Marta Ferreira", "marta.ferreira@ems.pt", "password123", PersonType.STUDENT, ist);
		upsertPerson("Pedro Almeida",  "pedro.almeida@ems.pt",  "password123", PersonType.STUDENT, ist);
		upsertPerson("Rita Cardoso",   "rita.cardoso@ems.pt",   "password123", PersonType.STUDENT, fct);
		upsertPerson("Tiago Nunes",    "tiago.nunes@ems.pt",    "password123", PersonType.STUDENT, fct);
		upsertPerson("Sofia Reis",     "sofia.reis@ems.pt",     "password123", PersonType.STUDENT, fct);
		upsertPerson("Hugo Pinto",     "hugo.pinto@ems.pt",     "password123", PersonType.STUDENT, fct);
		upsertPerson("Beatriz Santos", "beatriz.santos@ems.pt", "password123", PersonType.STUDENT, ist);
		upsertPerson("Gonçalo Pereira","goncalo.pereira@ems.pt","password123", PersonType.STUDENT, fct);

		upsertSystemState();

		// Turn every PDF currently sitting in the exams directory into a fully
		// fragmented exam (Exam + per-page Fragment PNGs), ready for the
		// normal lock → distribute flow. Safe to re-run: PDFs already seeded
		// (matched by their on-disk path) are skipped.
		seedExamsFromFolder();

		logger.info("DataInitializer: done");
	}

	/**
	 * Scans the exams storage directory for PDFs and creates a fragmented exam
	 * for each one that is not already seeded. Students and subjects are
	 * assigned round-robin among the seeded demo data; the school comes from
	 * the student (as in a real upload). Logged in as the global admin, so the
	 * staff upload-lock guard does not apply.
	 */
	private void seedExamsFromFolder() {
		File dir = new File(examsDir);
		if (!dir.isDirectory()) {
			logger.info("Seeding exams: exams dir '{}' not found, skipping", examsDir);
			return;
		}
		File[] pdfs = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
		if (pdfs == null || pdfs.length == 0) {
			logger.info("Seeding exams: no PDFs in '{}', skipping", examsDir);
			return;
		}
		List<Person> students = personRepository.findAll().stream()
				.filter(p -> p.getType() == PersonType.STUDENT)
				.toList();
		List<Subject> subjects = subjectRepository.findAll();
		Person admin = personRepository.findByEmail("admin@ems.pt").orElse(null);
		if (students.isEmpty() || subjects.isEmpty() || admin == null) {
			logger.warn("Seeding exams: missing students/subjects/admin, skipping");
			return;
		}
		int i = 0;
		int seeded = 0;
		for (File pdf : pdfs) {
			String path = pdf.getAbsolutePath();
			if (examRepository.existsByPdfPath(path)) {
				continue;
			}
			Person student = students.get(i % students.size());
			Subject subject = subjects.get(i % subjects.size());
			try {
				examService.seedExamFromDisk(path, pdf.getName(),
						student.getId(), subject.getId(), admin.getId());
				logger.info("Seeded exam from file: {} (student={}, subject={})",
						pdf.getName(), student.getEmail(), subject.getCode());
				seeded++;
			} catch (Exception e) {
				logger.warn("Failed to seed exam from {}: {}", pdf.getName(), e.getMessage());
			}
			i++;
		}
		logger.info("Seeding exams: {} new exam(s) created from folder", seeded);
	}

	private School upsertSchool(String name, String code, String region) {
		return schoolRepository.findByCode(code).orElseGet(() -> {
			School created = schoolRepository.save(new School(name, code, region));
			logger.info("Seeded school: {} ({})", name, code);
			return created;
		});
	}

	private Subject upsertSubject(String name, String code) {
		return subjectRepository.findByCode(code).orElseGet(() -> {
			Subject created = subjectRepository.save(new Subject(name, code));
			logger.info("Seeded subject: {} ({})", name, code);
			return created;
		});
	}

	private Person upsertPerson(String name, String email, String rawPassword, PersonType type, School school) {
		return personRepository.findByEmail(email).orElseGet(() -> {
			Person created = personRepository.save(new Person(
					name, email, passwordEncoder.encode(rawPassword), type, school));
			logger.info("Seeded account: {} ({}, school={})",
					email, type, school == null ? "none" : school.getCode());
			return created;
		});
	}

	private void upsertTeacherAssignment(Person teacher, School school, Subject subject) {
		if (!teacherAssignmentRepository.existsByTeacherIdAndSchoolIdAndSubjectId(
				teacher.getId(), school.getId(), subject.getId())) {
			teacherAssignmentRepository.save(new TeacherAssignment(teacher, school, subject));
			logger.info("Seeded assignment: {} ↔ {} ↔ {}",
					teacher.getEmail(), school.getCode(), subject.getCode());
		}
	}

	private void upsertSystemState() {
		if (!systemStateRepository.existsById(1L)) {
			systemStateRepository.save(new SystemState(false));
			logger.info("Seeded system_state singleton (examsLocked=false)");
		}
	}
}
