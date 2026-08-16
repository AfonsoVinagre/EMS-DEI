import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import PeopleView from '@/views/people/PeopleView.vue'
import StatisticsView from '@/views/statistics/StatisticsView.vue'
import SchoolsView from '@/views/schools/SchoolsView.vue'
import SubjectsView from '@/views/subjects/SubjectsView.vue'
import TeacherAssignmentsView from '@/views/teacherassignments/TeacherAssignmentsView.vue'
import ExamsView from '@/views/exams/ExamsView.vue'
import ExamFragmentsView from '@/views/exams/ExamFragmentsView.vue'
import CorrectionsView from '@/views/corrections/CorrectionsView.vue'
import { useAuthStore } from '@/stores/auth'

// Roles that may enter each page (in addition to the permission check).
// During impersonation, auth.user.role reflects the *impersonated* role, so
// admin impersonating staff effectively becomes a staff-equivalent for
// navigation.
const EXAM_ROLES = ['ADMINISTRATOR', 'SCHOOL_STAFF']
const TEACHER_ROLES = ['TEACHER']
const ADMIN_ONLY = ['ADMINISTRATOR']

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { public: true }
    },
    {
      path: '/',
      name: 'home',
      component: HomeView
    },
    {
      path: '/people',
      name: 'people',
      component: PeopleView,
      meta: { permission: 'PERSON_READ', allowedRoles: ADMIN_ONLY }
    },
    {
      path: '/statistics',
      name: 'statistics',
      component: StatisticsView,
      meta: { permission: 'STATISTICS_READ', allowedRoles: ['SCHOOL_STAFF'] }
    },
    {
      path: '/schools',
      name: 'schools',
      component: SchoolsView,
      meta: { permission: 'SCHOOL_READ', allowedRoles: ADMIN_ONLY }
    },
    {
      path: '/subjects',
      name: 'subjects',
      component: SubjectsView,
      meta: { permission: 'SUBJECT_READ', allowedRoles: ADMIN_ONLY }
    },
    {
      path: '/teacher-assignments',
      name: 'teacher-assignments',
      component: TeacherAssignmentsView,
      meta: { permission: 'TEACHER_ASSIGNMENT_READ', allowedRoles: ADMIN_ONLY }
    },
    {
      path: '/exams',
      name: 'exams',
      component: ExamsView,
      meta: { permission: 'EXAM_READ', allowedRoles: EXAM_ROLES }
    },
    {
      path: '/exams/:id/fragments',
      name: 'exam-fragments',
      component: ExamFragmentsView,
      meta: { permission: 'EXAM_READ', allowedRoles: EXAM_ROLES }
    },
    {
      path: '/corrections',
      name: 'corrections',
      component: CorrectionsView,
      meta: { permission: 'EXAM_READ', allowedRoles: TEACHER_ROLES }
    }
  ]
})

// Auth guard:
//  - /login is public.
//  - Every other route needs a session.
//  - If meta.allowedRoles is set, the active role must be in it. Impersonating
//    effectively swaps the active role, so admin impersonating staff loses
//    access to admin-only pages during the impersonation.
//  - If meta.permission is set, the user must have that authority.
router.beforeEach((to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    if (auth.isAuthenticated && to.name === 'login') {
      return { name: 'home' }
    }
    return true
  }

  if (!auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  // School staff and teachers each have their own landing page.
  if (to.name === 'home') {
    if (auth.user?.role === 'SCHOOL_STAFF') return { name: 'exams' }
    if (auth.user?.role === 'TEACHER') return { name: 'corrections' }
  }

  const allowedRoles = to.meta.allowedRoles as string[] | undefined
  if (allowedRoles && (!auth.user?.role || !allowedRoles.includes(auth.user.role))) {
    return { name: 'home' }
  }

  const required = to.meta.permission as string | undefined
  if (required && !auth.hasPermission(required)) {
    return { name: 'home' }
  }

  return true
})

export default router
