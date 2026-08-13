<template>
  <UtilBar />
  <NavBar :navbarItems="navbarItems" />
</template>

<script setup lang="ts">
import UtilBar from '@/components/UtilBar.vue'
import NavBar from '@/components/NavBar.vue'

const navbarItems = [
  { name: 'Pessoal', path: '/people', icon: 'mdi-account-group' },
  { name: 'Escolas', path: '/schools', icon: 'mdi-school' },
  { name: 'Disciplinas', path: '/subjects', icon: 'mdi-book-open-variant' },
  { name: 'Associações', path: '/teacher-assignments', icon: 'mdi-account-tie' },
  { name: 'Exames', path: '/exams', icon: 'mdi-file-pdf-box' },
  { name: 'Estatísticas', path: '/statistics', icon: 'mdi-chart-bar' },
]
</script>
