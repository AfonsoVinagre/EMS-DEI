export default class TeacherAssignmentDto {
  id?: number
  teacherId?: number
  teacherName?: string
  schoolId?: number
  schoolName?: string
  subjectId?: number
  subjectName?: string

  constructor(obj?: Partial<TeacherAssignmentDto>) {
    Object.assign(this, obj)
  }
}