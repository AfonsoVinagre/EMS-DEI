// The authenticated user, as returned by /auth/login and /auth/me.
export default interface AuthUser {
  id: number
  name: string
  email: string
  role: string
  permissions: string[]
  impersonating: boolean
  impersonatorEmail?: string | null
  // Null for ADMINISTRATOR; required for SCHOOL_STAFF/TEACHER/STUDENT.
  schoolId?: number | null
  schoolName?: string | null
}
