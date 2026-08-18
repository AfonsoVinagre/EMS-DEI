export interface RevisionItemDto {
  fragmentId?: number
  pageNumber?: number
  justification?: string
}

export default interface RevisionRequestDto {
  id?: number
  examId?: number
  studentId?: number
  studentName?: string
  subjectId?: number
  subjectName?: string
  requestedAt?: string
  items?: RevisionItemDto[]
}