export default class CorrectionTaskDto {
  id?: number
  fragmentId?: number
  fragmentPageNumber?: number
  examId?: number
  subjectId?: number
  subjectName?: string
  teacherId?: number
  teacherName?: string
  phase?: 'PRIMARY' | 'REVISION'
  status?: 'ASSIGNED' | 'IN_PROGRESS' | 'SUBMITTED'
  grade?: number | null
  assignedAt?: string
  submittedAt?: string | null

  constructor(obj?: Partial<CorrectionTaskDto>) {
    Object.assign(this, obj)
  }
}
