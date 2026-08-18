/**
 * Agregações puras para o Dashboard Estatístico (admin). Recebem os arrays
 crus vindos dos endpoints existentes (getExams, getRevisionRequests,
 getCorrectionTasks, getPeople) e devolvem dados prontos para os gráficos
 Chart.js. Sem chamadas a RemoteService — são funções de cálculo, testáveis.
 *
 * Convenção de "exame corrigido": `grade != null` — o backend só popula a
 nota total para CORRECTED/RELEASED (ver ExamService.totalGrade). A mesma
 regra que StatisticsView usa para a tabela de classificações.
 */
import type ExamDto from '@/models/ExamDto'
import type RevisionRequestDto from '@/models/RevisionRequestDto'
import type CorrectionTaskDto from '@/models/CorrectionTaskDto'
import type PersonDto from '@/models/PersonDto'

// --- cores de status (hex) para o doughnut de progresso --------------
// Espelham os tokens do tema (info #2196F3, warning #FFC107, success
// #4CAF50) + um grey para UPLOADED. Status é paleta reservada: vai sempre
// acompanhada de label (a legenda do doughnut), nunca só-cor.
export const EXAM_STATUS_HEX: Record<string, string> = {
  UPLOADED: '#9E9E9E',
  DISTRIBUTED: '#2196F3',
  CORRECTED: '#FFC107',
  RELEASED: '#4CAF50'
}

export const EXAM_STATUS_ORDER = ['UPLOADED', 'DISTRIBUTED', 'CORRECTED', 'RELEASED']

export interface SubjectAverage {
  subject: string
  mean: number
  count: number
}

/**
 * Média das notas finais por disciplina. Só conta exames com `grade != null`
 * (CORRECTED/RELEASED). Ordenado por nome de disciplina para ordem estável
 * (a cor não segue o rank — é single-series de magnitude, hue único).
 */
export function subjectAverages(exams: ExamDto[]): SubjectAverage[] {
  const bySubject = new Map<string, { sum: number; count: number }>()
  for (const e of exams) {
    if (e.grade == null) continue
    const subject = e.subjectName ?? '—'
    const acc = bySubject.get(subject) ?? { sum: 0, count: 0 }
    acc.sum += e.grade
    acc.count += 1
    bySubject.set(subject, acc)
  }
  return Array.from(bySubject.entries())
    .map(([subject, acc]) => ({
      subject,
      mean: acc.sum / acc.count,
      count: acc.count
    }))
    .sort((a, b) => a.subject.localeCompare(b.subject))
}

export interface StatusSlice {
  status: string
  label: string
  color: string
  count: number
}

/**
 * Distribuição dos exames por estado, na ordem fixa do ciclo de vida.
 * `label` vem do util partilhado (consistente com os chips do resto da app);
 * `color` é o hex de EXAM_STATUS_HEX.
 */
export function statusDistribution(
  exams: ExamDto[],
  labelFn: (s: string | undefined | null) => string
): StatusSlice[] {
  const counts = new Map<string, number>()
  for (const e of exams) {
    const status = e.status ?? 'UPLOADED'
    counts.set(status, (counts.get(status) ?? 0) + 1)
  }
  return EXAM_STATUS_ORDER.map((status) => ({
    status,
    label: labelFn(status),
    color: EXAM_STATUS_HEX[status] ?? '#9E9E9E',
    count: counts.get(status) ?? 0
  }))
}

export interface RevisionVolumeEntry {
  subject: string
  count: number
}

export interface RevisionVolume {
  perSubject: RevisionVolumeEntry[]
  total: number
  /** Pedidos cujo exame ainda tem janela de revisão aberta. */
  openWindows: number
}

/**
 * Volume de pedidos de revisão por disciplina + totais. `openWindows` conta
 * os pedidos cujo exame tem `revisionDeadline` no futuro e ainda não foi
 * distribuído (janela aberta → revisão pendente de fecho pelo scheduler).
 */
export function revisionVolume(
  requests: RevisionRequestDto[],
  exams: ExamDto[]
): RevisionVolume {
  const examById = new Map<number, ExamDto>()
  for (const e of exams) if (e.id != null) examById.set(e.id, e)

  const bySubject = new Map<string, number>()
  let openWindows = 0
  for (const r of requests) {
    const subject = r.subjectName ?? '—'
    bySubject.set(subject, (bySubject.get(subject) ?? 0) + 1)
    const exam = r.examId != null ? examById.get(r.examId) : undefined
    if (exam && !exam.revisionDistributed) {
      const deadline = exam.revisionDeadline ? Date.parse(exam.revisionDeadline) : null
      if (deadline != null && Date.now() < deadline) openWindows += 1
    }
  }
  const perSubject = Array.from(bySubject.entries())
    .map(([subject, count]) => ({ subject, count }))
    .sort((a, b) => a.subject.localeCompare(b.subject))
  return { perSubject, total: requests.length, openWindows }
}

export interface TeacherLoadEntry {
  teacher: string
  submitted: number
  assigned: number
}

/**
 * Carga por professor: tarefas SUBMITTED (trabalho concluído) vs total
 * atribuído. Métrica de equilíbrio de carga — interessa ao admin. Ordenado
 * por submitted desc para destacar quem mais submeteu.
 */
export function teacherLoad(tasks: CorrectionTaskDto[]): TeacherLoadEntry[] {
  const byTeacher = new Map<string, { submitted: number; assigned: number }>()
  for (const t of tasks) {
    const teacher = t.teacherName ?? '—'
    const acc = byTeacher.get(teacher) ?? { submitted: 0, assigned: 0 }
    acc.assigned += 1
    if (t.status === 'SUBMITTED') acc.submitted += 1
    byTeacher.set(teacher, acc)
  }
  return Array.from(byTeacher.entries())
    .map(([teacher, acc]) => ({ teacher, ...acc }))
    .sort((a, b) => b.submitted - a.submitted)
}

// --- KPIs (stat tiles) ----------------------------------------------

export interface DashboardKpis {
  totalExams: number
  corrected: number
  released: number
  revisionRequests: number
  teachers: number
  students: number
}

/** Contagem de pessoas por tipo (role). */
export function countPeopleByType(people: PersonDto[]): {
  teachers: number
  students: number
} {
  let teachers = 0
  let students = 0
  for (const p of people) {
    if (p.type === 'TEACHER') teachers += 1
    else if (p.type === 'STUDENT') students += 1
  }
  return { teachers, students }
}

/** KPIs agregados para os tiles do topo do dashboard. */
export function computeKpis(
  exams: ExamDto[],
  requests: RevisionRequestDto[],
  people: PersonDto[]
): DashboardKpis {
  let corrected = 0
  let released = 0
  for (const e of exams) {
    if (e.grade != null) corrected += 1
    if (e.status === 'RELEASED') released += 1
  }
  const { teachers, students } = countPeopleByType(people)
  return {
    totalExams: exams.length,
    corrected,
    released,
    revisionRequests: requests.length,
    teachers,
    students
  }
}