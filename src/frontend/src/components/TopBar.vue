<template>
  <UtilBar />
  <NavBar :navbarItems="visibleNavbarItems" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import UtilBar from '@/components/UtilBar.vue'
import NavBar from '@/components/NavBar.vue'
import { useAuthStore } from '@/stores/auth'

interface NavItem {
  name: string
  path: string
  icon: string
  roles: string[]
}

const auth = useAuthStore()

// Each item declares which active roles may see it. The active role is
// auth.user.role, which reflects the impersonated user when impersonating —
// so an admin impersonating staff behaves like a staff (only sees Exames).
const navbarItems: NavItem[] = [
  { name: 'Pessoal', path: '/people', icon: 'mdi-account-group', roles: ['ADMINISTRATOR'] },
  { name: 'Escolas', path: '/schools', icon: 'mdi-school', roles: ['ADMINISTRATOR'] },
  { name: 'Disciplinas', path: '/subjects', icon: 'mdi-book-open-variant', roles: ['ADMINISTRATOR'] },
  { name: 'Associações', path: '/teacher-assignments', icon: 'mdi-account-tie', roles: ['ADMINISTRATOR'] },
  { name: 'Exames', path: '/exams', icon: 'mdi-file-pdf-box', roles: [
    'ADMINISTRATOR',
    'SCHOOL_STAFF'
  ] },
  { name: 'Classificações', path: '/statistics', icon: 'mdi-chart-bar', roles: ['SCHOOL_STAFF'] },
  { name: 'Consultas', path: '/staff-consultations', icon: 'mdi-eye-check-outline', roles: ['SCHOOL_STAFF'] },
  { name: 'Correcções', path: '/corrections', icon: 'mdi-clipboard-check-outline', roles: ['TEACHER'] },
  { name: 'As minhas provas', path: '/my-exams', icon: 'mdi-file-eye-outline', roles: ['STUDENT'] }
]

const visibleNavbarItems = computed(() => {
  const role = auth.user?.role
  if (!role) return []
  return navbarItems.filter((item) => item.roles.includes(role))
})
</script>
