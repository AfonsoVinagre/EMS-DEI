<template>
  <h2 class="text-left ml-1">Upload e Listagem de Exames</h2>

  <v-row>
    <v-col cols="12" md="6">
      <v-file-input
        v-model="pdfFile"
        label="PDF do exame"
        accept="application/pdf"
        prepend-inner-icon="mdi-file-pdf-box"
        variant="outlined"
        density="comfortable"
        :rules="[(v: File | File[] | undefined) => !v || (Array.isArray(v) ? v.length > 0 : true) || 'PDF obrigatório']"
      ></v-file-input>
    </v-col>
    <v-col cols="12" md="6">
      <v-select
        v-model="newStudentId"
        :items="students"
        :loading="loadingStudents"
        item-title="name"
        item-value="id"
        label="Aluno"
        clearable
      ></v-select>
    </v-col>
    <v-col cols="12" md="6">
      <v-select
        v-model="newSchoolId"
        :items="schools"
        :loading="loadingSchools"
        item-title="name"
        item-value="id"
        label="Escola"
        clearable
      ></v-select>
    </v-col>
    <v-col cols="12" md="6">
      <v-select
        v-model="newSubjectId"
        :items="subjects"
        :loading="loadingSubjects"
        item-title="name"
        item-value="id"
        label="Disciplina"
        clearable
      ></v-select>
    </v-col>
    <v-col cols="auto" class="d-flex align-center">
      <v-btn
        color="primary"
        prepend-icon="mdi-upload"
        text="Submeter"
        :disabled="!canUpload"
        @click="uploadExam"
      ></v-btn>
    </v-col>
  </v-row>

  <v-row>
    <v-col cols="12" md="6">
      <v-select
        v-model="filterSchoolId"
        :items="schools"
        :loading="loadingSchools"
        item-title="name"
        item-value="id"
        label="Filtrar por escola"
        clearable
      ></v-select>
    </v-col>
    <v-col cols="12" md="6">
      <v-select
        v-model="filterSubjectId"
        :items="subjects"
        :loading="loadingSubjects"
        item-title="name"
        item-value="id"
        label="Filtrar por disciplina"
        clearable
      ></v-select>
    </v-col>
  </v-row>

  <v-data-table
    :headers="headers"
    :items="filteredExams"
    :loading="loadingExams"
    item-key="id"
    class="text-left"
    no-data-text="Sem exames a apresentar."
  >
  </v-data-table>
</template>

<script setup lang="ts">
import type PersonDto from '@/models/PersonDto'
import type SchoolDto from '@/models/SchoolDto'
import type SubjectDto from '@/models/SubjectDto'
import type ExamDto from '@/models/ExamDto'
import RemoteService from '@/services/RemoteService'
import { computed, ref } from 'vue'

const pdfFile = ref<File | File[] | undefined>(undefined)
const newStudentId = ref<number | undefined>(undefined)
const newSchoolId = ref<number | undefined>(undefined)
const newSubjectId = ref<number | undefined>(undefined)

const filterSchoolId = ref<number | undefined>(undefined)
const filterSubjectId = ref<number | undefined>(undefined)

const students = ref<PersonDto[]>([])
const schools = ref<SchoolDto[]>([])
const subjects = ref<SubjectDto[]>([])
const exams = ref<ExamDto[]>([])

const loadingStudents = ref(true)
const loadingSchools = ref(true)
const loadingSubjects = ref(true)
const loadingExams = ref(false)

const headers = [
  { title: 'ID', key: 'id', value: 'id', sortable: true },
  { title: 'Ficheiro', key: 'originalFilename', value: 'originalFilename', sortable: true },
  { title: 'Aluno', key: 'studentName', value: 'studentName', sortable: true },
  { title: 'Escola', key: 'schoolName', value: 'schoolName', sortable: true },
  { title: 'Disciplina', key: 'subjectName', value: 'subjectName', sortable: true },
  { title: 'Estado', key: 'status', value: 'status', sortable: true }
]

function pickFile(): File | undefined {
  const f = pdfFile.value
  if (!f) return undefined
  if (Array.isArray(f)) return f[0]
  return f
}

const canUpload = computed(
  () => !!pickFile() && !!newStudentId.value && !!newSchoolId.value && !!newSubjectId.value
)

const filteredExams = computed(() =>
  exams.value.filter((e) => {
    if (filterSchoolId.value != null && e.schoolId !== filterSchoolId.value) return false
    if (filterSubjectId.value != null && e.subjectId !== filterSubjectId.value) return false
    return true
  })
)

;(async () => {
  try {
    const people = await RemoteService.getPeople()
    students.value = people.filter((p) => p.type === 'STUDENT')
  } finally {
    loadingStudents.value = false
  }
})()
;(async () => {
  try {
    schools.value = await RemoteService.getSchools()
  } finally {
    loadingSchools.value = false
  }
})()
;(async () => {
  try {
    subjects.value = await RemoteService.getSubjects()
  } finally {
    loadingSubjects.value = false
  }
})()

async function refreshExams() {
  loadingExams.value = true
  try {
    exams.value = await RemoteService.getExams()
  } finally {
    loadingExams.value = false
  }
}

refreshExams()

async function uploadExam() {
  const file = pickFile()
  if (!file || !newStudentId.value || !newSchoolId.value || !newSubjectId.value) return
  await RemoteService.uploadExam(file, newStudentId.value, newSchoolId.value, newSubjectId.value)
  pdfFile.value = undefined
  await refreshExams()
}
</script>