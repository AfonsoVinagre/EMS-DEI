export default class PersonDto {
  id?: number
  name?: string
  email?: string
  // Write-only: sent when creating/updating, never returned by the backend.
  password?: string
  type?: string

  // Null for ADMINISTRATOR; required for SCHOOL_STAFF/TEACHER/STUDENT.
  // The backend enforces these rules on create/update.
  schoolId?: number | null
  schoolName?: string | null

  constructor(obj?: Partial<PersonDto>) {
    Object.assign(this, obj)
  }
}