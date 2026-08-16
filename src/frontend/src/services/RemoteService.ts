import axios from 'axios'
import type { AxiosResponse } from 'axios'
import { useAppearanceStore } from '@/stores/appearance'
import { useAuthStore } from '@/stores/auth'
import DeiError from '@/models/DeiError'
import type PersonDto from '@/models/PersonDto'
import type AuthUser from '@/models/AuthUser'
import type SchoolDto from '@/models/SchoolDto'
import type SubjectDto from '@/models/SubjectDto'
import type TeacherAssignmentDto from '@/models/TeacherAssignmentDto'
import type ExamDto from '@/models/ExamDto'
import type FragmentDto from '@/models/FragmentDto'
import type CorrectionTaskDto from '@/models/CorrectionTaskDto'
import type ConsultationRequestDto from '@/models/ConsultationRequestDto'
import type SystemStateDto from '@/models/SystemStateDto'

const httpClient = axios.create()
httpClient.defaults.timeout = 50000
httpClient.defaults.baseURL = import.meta.env.VITE_ROOT_API
httpClient.defaults.headers.post['Content-Type'] = 'application/json'

export interface LoginResponse {
  token: string
  expiresInMs: number
  user: AuthUser
}

export default class RemoteServices {
  static async getPeople(): Promise<PersonDto[]> {
    return httpClient.get('/people')
  }

  static async createPerson(person: PersonDto): Promise<PersonDto> {
    return httpClient.post('/people', person)
  }

  // --- Schools ---
  static async getSchools(): Promise<SchoolDto[]> {
    return httpClient.get('/schools')
  }

  static async createSchool(school: SchoolDto): Promise<SchoolDto> {
    return httpClient.post('/schools', school)
  }

  // --- Subjects ---
  static async getSubjects(): Promise<SubjectDto[]> {
    return httpClient.get('/subjects')
  }

  static async createSubject(subject: SubjectDto): Promise<SubjectDto> {
    return httpClient.post('/subjects', subject)
  }

  // --- Teacher assignments ---
  static async getTeacherAssignments(): Promise<TeacherAssignmentDto[]> {
    return httpClient.get('/teacher-assignments')
  }

  static async getTeacherAssignmentsByTeacher(teacherId: number): Promise<TeacherAssignmentDto[]> {
    return httpClient.get(`/teacher-assignments/teacher/${teacherId}`)
  }

  static async createTeacherAssignment(payload: {
    teacherId: number
    schoolId: number
    subjectId: number
  }): Promise<TeacherAssignmentDto> {
    return httpClient.post('/teacher-assignments', payload)
  }

  static async deleteTeacherAssignment(id: number): Promise<void> {
    return httpClient.delete(`/teacher-assignments/${id}`)
  }

  // --- Exams ---
  static async getExams(): Promise<ExamDto[]> {
    return httpClient.get('/exams')
  }

  static async uploadExam(
    file: File,
    studentId: number,
    subjectId: number,
    schoolId?: number | null
  ): Promise<ExamDto> {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('studentId', String(studentId))
    formData.append('subjectId', String(subjectId))
    if (schoolId !== undefined && schoolId !== null) {
      formData.append('schoolId', String(schoolId))
    }
    return httpClient.post('/exams', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }

  static async getExamsBySchoolAndSubject(
    schoolId: number,
    subjectId: number
  ): Promise<ExamDto[]> {
    return httpClient.get(`/exams/school/${schoolId}/subject/${subjectId}`)
  }

  static async getExamPdfUrl(id: number): Promise<string> {
    // Same JWT-via-blob trick as the fragment image: <a href> can't send
    // Authorization, so we fetch via the (JWT-injecting) httpClient and
    // expose an object URL the caller is responsible for revoking.
    const blob = (await httpClient.get(`/exams/${id}/pdf`, {
      responseType: 'blob'
    })) as unknown as Blob
    return URL.createObjectURL(blob)
  }

  static async distributeAllExams(): Promise<number[]> {
    return httpClient.post('/exams/distribute-all')
  }

  static async bulkReleaseExams(subjectId: number): Promise<number[]> {
    return httpClient.post('/exams/bulk-release', null, {
      params: { subjectId }
    })
  }

  // --- Consultation requests ---
  static async createConsultationRequest(examId: number): Promise<ConsultationRequestDto> {
    return httpClient.post('/consultation-requests', { examId })
  }

  static async getMyConsultationRequests(): Promise<ConsultationRequestDto[]> {
    return httpClient.get('/consultation-requests/mine')
  }

  static async getConsultationRequests(): Promise<ConsultationRequestDto[]> {
    return httpClient.get('/consultation-requests')
  }

  static async approveConsultationRequest(id: number): Promise<ConsultationRequestDto> {
    return httpClient.post(`/consultation-requests/${id}/approve`)
  }

  static async rejectConsultationRequest(id: number): Promise<ConsultationRequestDto> {
    return httpClient.post(`/consultation-requests/${id}/reject`)
  }

  // --- Fragments ---
  static async getFragments(): Promise<FragmentDto[]> {
    return httpClient.get('/fragments')
  }

  static async getFragmentsByExam(examId: number): Promise<FragmentDto[]> {
    return httpClient.get(`/fragments/exam/${examId}`)
  }

  static async getFragmentImageUrl(fragmentId: number): Promise<string> {
    // The HTTP client sends the JWT in a separate Authorization header on
    // blob fetches too, but <img src=...> can't have custom headers — so
    // we download as a blob via the (JWT-injecting) httpClient and turn it
    // into an object URL. The caller is responsible for revoking.
    // The response interceptor unwraps response.data, so the result is a Blob.
    const blob = (await httpClient.get(`/fragments/${fragmentId}/image`, {
      responseType: 'blob'
    })) as unknown as Blob
    return URL.createObjectURL(blob)
  }

  static async getFragmentImageBlob(fragmentId: number): Promise<Blob> {
    return (await httpClient.get(`/fragments/${fragmentId}/image`, {
      responseType: 'blob'
    })) as unknown as Blob
  }

  static async getFragmentTasks(fragmentId: number): Promise<CorrectionTaskDto[]> {
    return httpClient.get(`/fragments/${fragmentId}/tasks`)
  }

  // --- Correction tasks ---
  static async getCorrectionTasks(): Promise<CorrectionTaskDto[]> {
    return httpClient.get('/correction-tasks')
  }

  static async submitGrade(taskId: number, grade: number): Promise<CorrectionTaskDto> {
    return httpClient.patch(`/correction-tasks/${taskId}/score`, { grade })
  }

  static async reassignTask(taskId: number, teacherId: number): Promise<CorrectionTaskDto> {
    return httpClient.patch(`/correction-tasks/${taskId}`, { teacherId })
  }

  // --- System state ---
  static async getSystemState(): Promise<SystemStateDto> {
    return httpClient.get('/system-state')
  }

  static async lockUploads(): Promise<SystemStateDto> {
    return httpClient.post('/system-state/lock')
  }

  static async unlockUploads(): Promise<SystemStateDto> {
    return httpClient.post('/system-state/unlock')
  }

  // --- Authentication ---
  static async login(email: string, password: string): Promise<LoginResponse> {
    return httpClient.post('/auth/login', { email, password })
  }

  static async getCurrentUser(): Promise<AuthUser> {
    return httpClient.get('/auth/me')
  }

  static async impersonate(personId: number): Promise<LoginResponse> {
    return httpClient.post(`/auth/impersonate/${personId}`)
  }

  static async stopImpersonation(): Promise<LoginResponse> {
    return httpClient.post('/auth/impersonate/stop')
  }

  static async errorMessage(error: any): Promise<string> {
    if (error.message === 'Network Error') {
      return 'Unable to connect to the server'
    } else if (error.message.split(' ')[0] === 'timeout') {
      return 'Request timeout - Server took too long to respond'
    } else {
      return error.response?.data?.message ?? 'Unknown Error'
    }
  }

  static async handleError(error: any): Promise<never> {
    // An expired/invalid token yields 401: drop the session so the router
    // guard sends the user back to the login page.
    if (error.response?.status === 401) {
      useAuthStore().logout()
    }
    const deiErr = new DeiError(
      await RemoteServices.errorMessage(error),
      error.response?.data?.code ?? -1
    )
    const appearance = useAppearanceStore()
    appearance.pushError(deiErr)
    appearance.loading = false
    throw deiErr
  }
}

// Attach the JWT (if any) to every outgoing request.
httpClient.interceptors.request.use((request) => {
  const auth = useAuthStore()
  if (auth.token) {
    request.headers.Authorization = `Bearer ${auth.token}`
  }
  return request
}, RemoteServices.handleError)
httpClient.interceptors.response.use((response) => response.data, RemoteServices.handleError)
