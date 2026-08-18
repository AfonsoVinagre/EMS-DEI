/**
 * Mapeamentos partilhados de labels e cores para os enums do backend.
 * Centraliza o que estava duplicado em várias views (CorrectionsView,
 * ExamFragmentsView, MyExamsView, StaffConsultationView) para haver uma
 * fonte de verdade e consistência visual em toda a app.
 *
 * As cores devolvidas são nomes/tokens do tema Vuetify (success, warning,
 * error, info, grey), usados em chips `variant="tonal"`.
 */

// ----------------------------------------------------------------- Exam

/** Exam.ExamStatus: ciclo de vida do exame (UPLOADED → RELEASED). */
export function examStatusLabel(status: string | undefined | null): string {
  switch (status) {
    case 'UPLOADED': return 'Submetido'
    case 'DISTRIBUTED': return 'Em correção'
    case 'CORRECTED': return 'Corrigido'
    case 'RELEASED': return 'Disponível'
    default: return status ?? ''
  }
}

/** Cor semântica do estado do exame (chip tonal). */
export function examStatusColor(status: string | undefined | null): string {
  switch (status) {
    case 'UPLOADED': return 'grey'
    case 'DISTRIBUTED': return 'info'
    case 'CORRECTED': return 'warning'
    case 'RELEASED': return 'success'
    default: return 'grey'
  }
}

/** Prioridade para ordenação por defeito (em curso no topo, publicado no fundo). */
export function examStatusRank(status: string | undefined | null): number {
  switch (status) {
    case 'UPLOADED': return 0
    case 'DISTRIBUTED': return 1
    case 'CORRECTED': return 2
    case 'RELEASED': return 3
    default: return 4
  }
}

// -------------------------------------------------------------- Fragment

/**
 * Fragment.status (@Transient, computado no backend a partir da latest
 * CorrectionTask): onde está o pipeline de correção, não o ciclo do exame.
 */
export function fragmentStatusLabel(status: string | undefined): string {
  switch (status) {
    case 'UNASSIGNED': return 'Pendente'
    case 'IN_CORRECTION': return 'Em correção'
    case 'IN_REVISION': return 'Em revisão'
    case 'CORRECTED': return 'Submetido'
    case 'REVISION_SUBMITTED': return 'Revisão submetida'
    default: return status ?? ''
  }
}

/** Cor semântica do estado do fragmento (chip tonal). */
export function fragmentStatusColor(status: string | undefined): string {
  switch (status) {
    case 'CORRECTED':
    case 'REVISION_SUBMITTED':
      return 'success'
    case 'IN_CORRECTION':
    case 'IN_REVISION':
      return 'warning'
    default:
      return 'grey'
  }
}

/** True se o fragmento ainda não tem nota submetida (precisa de ação do professor). */
export function isFragmentPending(status: string | undefined): boolean {
  return status === 'UNASSIGNED' || status === 'IN_CORRECTION' || status === 'IN_REVISION'
}

// ----------------------------------------------------------------- Phase

/** CorrectionTask.Phase: PRIMARY (correção) | REVISION (revisão). */
export function phaseLabel(phase: string | undefined): string {
  switch (phase) {
    case 'PRIMARY': return 'Correção'
    case 'REVISION': return 'Revisão'
    default: return ''
  }
}

// ----------------------------------------------------- ConsultationRequest

/** ConsultationRequest.status: PENDING | APPROVED | REJECTED. */
export function consultationStatusLabel(status: string | undefined): string {
  switch (status) {
    case 'PENDING': return 'Pendente'
    case 'APPROVED': return 'Aprovado'
    case 'REJECTED': return 'Rejeitado'
    default: return status ?? ''
  }
}

export function consultationStatusColor(status: string | undefined): string {
  switch (status) {
    case 'PENDING': return 'warning'
    case 'APPROVED': return 'success'
    case 'REJECTED': return 'error'
    default: return 'grey'
  }
}