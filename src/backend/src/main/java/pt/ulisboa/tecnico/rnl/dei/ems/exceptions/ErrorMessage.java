package pt.ulisboa.tecnico.rnl.dei.ems.exceptions;

public enum ErrorMessage {

	NO_SUCH_PERSON("Não existe nenhuma pessoa com o ID %s", 1001),
	PERSON_NAME_NOT_VALID("O nome da pessoa especificado não é válido.", 1002),
	PERSON_ALREADY_EXISTS("Já existe uma pessoa com o ID %s", 1003),
	PERSON_EMAIL_NOT_VALID("O email especificado não é válido.", 1004),
	PERSON_PASSWORD_NOT_VALID("A palavra-passe especificada não é válida.", 1005),
	EMAIL_ALREADY_EXISTS("Já existe uma conta com o email %s", 1006),

	INVALID_CREDENTIALS("Email ou palavra-passe inválidos.", 2001),
	CANNOT_IMPERSONATE_SELF("Não é possível personificar a própria conta.", 2002),
	NOT_AUTHENTICATED("Não autenticado.", 2003),
	NOT_IMPERSONATING("Não está a personificar nenhuma conta.", 2004),
	ACCESS_DENIED("Não tem permissões para executar esta ação.", 2005),

	PERSON_NOT_TEACHER("A pessoa com o ID %s não é um professor.", 3001),
	NO_SUCH_SCHOOL("Não existe nenhuma escola com o ID %s", 3002),
	NO_SUCH_SUBJECT("Não existe nenhuma disciplina com o ID %s", 3003),
	TEACHER_ASSIGNMENT_ALREADY_EXISTS("Este professor já está associado a esta escola e disciplina.", 3004),
	NO_SUCH_TEACHER_ASSIGNMENT("Não existe nenhuma associação com o ID %s", 3005),

	SCHOOL_CODE_NOT_VALID("O código da escola especificado não é válido.", 4001),
	SCHOOL_NAME_NOT_VALID("O nome da escola especificado não é válido.", 4002),
	SCHOOL_REGION_NOT_VALID("A região da escola especificada não é válida.", 4003),
	SCHOOL_CODE_ALREADY_EXISTS("Já existe uma escola com o código %s", 4004),
	SCHOOL_NAME_ALREADY_EXISTS("Já existe uma escola com o nome %s", 4005),

	SUBJECT_CODE_NOT_VALID("O código da disciplina especificado não é válido.", 5001),
	SUBJECT_NAME_NOT_VALID("O nome da disciplina especificado não é válido.", 5002),
	SUBJECT_CODE_ALREADY_EXISTS("Já existe uma disciplina com o código %s", 5003),
	SUBJECT_NAME_ALREADY_EXISTS("Já existe uma disciplina com o nome %s", 5004),

	NO_SUCH_EXAM("Não existe nenhum exame com o ID %s", 4001),
	PERSON_NOT_STUDENT("A pessoa com o ID %s não é um aluno.", 4002),
	INVALID_PDF_FILE("O ficheiro submetido não é um PDF válido.", 4003),
	FILE_STORAGE_ERROR("Erro ao guardar o ficheiro no servidor.", 4004);

	private final String label;
	private final int code;

	ErrorMessage(String label, int code) {
		this.label = label;
		this.code = code;
	}

	public String getLabel() {
		return this.label;
	}

	public int getCode() {
		return this.code;
	}
}
