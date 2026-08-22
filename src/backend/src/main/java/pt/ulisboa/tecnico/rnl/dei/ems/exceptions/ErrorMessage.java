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
	FILE_STORAGE_ERROR("Erro ao guardar o ficheiro no servidor.", 4004),

	NO_SUCH_FRAGMENT("Não existe nenhum fragmento com o ID %s", 5010),
	EXAM_NOT_SEGMENTABLE("O exame não pode ser segmentado no estado atual (%s).", 5011),
	EXAM_NOT_DISTRIBUTABLE("O exame não pode ser distribuído no estado atual (%s).", 5013),
	PDF_RENDER_ERROR("Erro ao processar o PDF do exame.", 5014),
	FRAGMENT_IMAGE_NOT_FOUND("A imagem do fragmento não foi encontrada no servidor.", 5015),
	NO_SUCH_CORRECTION_TASK("Não existe nenhuma tarefa de correção com o ID %s", 5016),
	TASK_NOT_OPEN("A tarefa de correção já foi submetida.", 5017),
	NOT_THE_ASSIGNED_TEACHER("Não é o professor atribuído a esta tarefa.", 5018),
	TEACHER_CANNOT_REVISE_HIMSELF("O professor que fez uma tarefa anterior não pode ser atribuído a uma revisão do mesmo fragmento.", 5019),
	NO_TEACHER_FOR_SUBJECT("Não há professores disponíveis para a disciplina deste exame.", 5020),
	GRADE_NOT_VALID("A nota atribuída tem de ser maior ou igual a zero.", 5021),
	SUBJECT_NOT_ASSIGNED("A correção não está atribuída a este fragmento.", 5022),
	EXAM_UPLOADS_GLOBALLY_LOCKED("Os uploads de exames estão bloqueados neste momento.", 5023),
	EXAMS_NOT_LOCKED("Os exames têm de estar bloqueados antes de serem distribuídos.", 5024),
	STAFF_UPLOAD_TO_OTHER_SCHOOL("Só pode submeter exames de alunos da sua escola.", 5025),
	EXAMS_ALREADY_DISTRIBUTED("Os exames já foram distribuídos — o lock não pode ser revertido.", 5030),
	PERSON_SCHOOL_REQUIRED("Esta conta tem de ter uma escola atribuída.", 5026),
	PERSON_SCHOOL_NOT_APPLICABLE("Um administrador não tem escola atribuída.", 5027),

	NO_SUCH_CONSULTATION_REQUEST("Não existe nenhum pedido de consulta com o ID %s", 6001),
	CONSULTATION_REQUEST_NOT_PENDING("O pedido de consulta já foi decidido.", 6002),
	EXAM_NOT_CORRECTED("O exame ainda não está totalmente corrigido.", 6003),
	CONSULTATION_REQUEST_ALREADY_PENDING("Já existe um pedido de consulta pendente para este exame.", 6004),
	EXAM_ALREADY_RELEASED("O exame já foi disponibilizado.", 6005),

	NO_SUCH_REVISION_REQUEST("Não existe nenhum pedido de revisão com o ID %s", 7001),
	REVISION_WINDOW_CLOSED("O prazo para pedir revisão deste exame já terminou ou a revisão já foi processada.", 7002),
	REVISION_REQUEST_ALREADY_EXISTS("Já existe um pedido de revisão pendente para este exame.", 7003),
	REVISION_ITEM_INVALID("Cada pergunta do pedido de revisão tem de pertencer ao exame e ter uma justificação.", 7004),

	ANNOTATION_NOT_FOUND("A anotação não está disponível para este fragmento.", 5030);

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
