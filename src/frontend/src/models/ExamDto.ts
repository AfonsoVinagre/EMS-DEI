export default class ExamDto {
  id?: number
  studentId?: number
  studentName?: string
  schoolId?: number
  schoolName?: string
  subjectId?: number
  subjectName?: string
  uploadedById?: number
  uploadedByName?: string
  originalFilename?: string
  status?: string
  grade?: number | null

  constructor(obj?: Partial<ExamDto>) {
    Object.assign(this, obj)
  }
}