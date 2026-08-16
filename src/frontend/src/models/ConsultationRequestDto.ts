// A student's request to consult a corrected exam (see PDF + per-fragment
// grades). Mirrors the backend ConsultationRequestDto record.
export default interface ConsultationRequestDto {
  id?: number
  examId?: number
  studentId?: number
  studentName?: string
  subjectId?: number
  subjectName?: string
  examStatus?: string
  status?: 'PENDING' | 'APPROVED' | 'REJECTED'
  requestedAt?: string
  decidedAt?: string | null
  decidedByName?: string | null
}