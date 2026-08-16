<template>
  <v-btn
    icon="mdi-arrow-left"
    size="small"
    variant="text"
    :to="'/exams'"
    title="Voltar aos exames"
  ></v-btn>
  <h2 class="text-left ml-1">Classificações Finais</h2>

  <p class="text-left text-medium-emphasis mb-4">
    Escola: <strong>{{ schoolName || '—' }}</strong>. Só são listados exames já
    totalmente corrigidos (todas as perguntas submetidas).
  </p>

  <v-row>
    <v-col cols="12" md="6">
      <v-select
        v-model="selectedSubjectId"
        :items="subjects"
        :loading="loadingSubjects"
        item-title="name"
        item-value="id"
        label="Disciplina"
        clearable
      ></v-select>
    </v-col>
  </v-row>

  <!-- Disciplina mostrada como cabeçalho no topo da tabela, não como coluna. -->
  <div v-if="selectedSubjectName" class="text-left mt-2 mb-2">
    <h3>Disciplina: {{ selectedSubjectName }}</h3>
  </div>

  <v-data-table
    v-if="selectedSubjectId != null"
    :headers="headers"
    :items="rows"
    :loading="loadingExams"
    item-key="id"
    class="text-left"
    no-data-text="Não há exames corrigidos para esta disciplina."
  >
    <template v-slot:[`item.grade`]="{ item }">
      {{ item.grade }}
    </template>
  </v-data-table>

  <v-alert
    v-else
    type="info"
    variant="tonal"
    class="text-left mt-2"
  >
    Selecione uma disciplina para gerar a tabela.
  </v-alert>
</template>

<script setup lang="ts">
import type ExamDto from '@/models/ExamDto'
import type SubjectDto from '@/models/SubjectDto'
import RemoteService from '@/services/RemoteService'
import { useAuthStore } from '@/stores/auth'
import { computed, ref } from 'vue'

const auth = useAuthStore()

const subjects = ref<SubjectDto[]>([])
const exams = ref<ExamDto[]>([])
const loadingSubjects = ref(true)
const loadingExams = ref(false)
const selectedSubjectId = ref<number | undefined>(undefined)

const headers = [
  { title: 'Aluno', key: 'studentName', value: 'studentName', sortable: true },
  { title: 'Nota', key: 'grade', value: 'grade', sortable: true }
]

// Exames da própria escola do caller, já corrigidos (grade != null só quando
// CORRECTED/RELEASED — i.e. todos os fragmentos submetidos), da disciplina
// escolhida. getExams() é scoping-por-escola server-side para staff, pelo que
// um staff nunca vê aqui exames de outra escola.
const rows = computed(() =>
  exams.value
    .filter(
      (e) =>
        e.grade != null &&
        selectedSubjectId.value != null &&
        e.subjectId === selectedSubjectId.value
    )
    .slice()
    .sort((a, b) => (a.studentName ?? '').localeCompare(b.studentName ?? ''))
)

const selectedSubjectName = computed(() => {
  if (selectedSubjectId.value == null) return ''
  const subj = subjects.value.find((s) => s.id === selectedSubjectId.value)
  return subj?.name ?? ''
})

// Nome da escola para contexto. Staff tem sempre escola; fallback ao
// schoolName de algum exame se necessário.
const schoolName = computed(
  () =>
    auth.user?.schoolName ??
    exams.value.find((e) => e.schoolName)?.schoolName ??
    ''
)

async function loadSubjects() {
  loadingSubjects.value = true
  try {
    subjects.value = await RemoteService.getSubjects()
  } finally {
    loadingSubjects.value = false
  }
}

async function loadExams() {
  loadingExams.value = true
  try {
    exams.value = await RemoteService.getExams()
  } finally {
    loadingExams.value = false
  }
}

loadSubjects()
loadExams()
</script>