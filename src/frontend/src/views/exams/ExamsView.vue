<template>
  <h2 class="text-left ml-1">Upload e Listagem de Exames</h2>

  <!-- Global lock banner (visible to the active role, so staff see when
       uploads are closed). Hidden once the system is distributed — the
       lock is permanent at that point and the banner adds no value. -->
  <v-alert
    v-if="systemState?.examsLocked && !systemState?.distributed && (auth.user?.role === 'SCHOOL_STAFF' || isAdminView)"
    type="warning"
    variant="tonal"
    class="mb-4"
  >
    Os uploads estão actualmente <strong>bloqueados</strong> a nível global.
    Nenhum staff pode submeter novos exames.
  </v-alert>

  <!-- Upload form: only visible to SCHOOL_STAFF (and admins impersonating
       them, because then auth.user.role swaps to SCHOOL_STAFF). School is
       forced server-side from the staff's own school, so it is not picked
       here. -->
  <v-row v-if="canUpload">
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
        :text="submitting ? 'A submeter…' : 'Submeter'"
        :loading="submitting"
        :disabled="!canSubmitForm || uploadsDisabled"
        @click="uploadExam"
      ></v-btn>
    </v-col>
  </v-row>

  <v-row>
    <v-col v-if="canFilterSchool" cols="12" md="6">
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

  <!-- Global action bar: lock/unlock uploads (admin-only) and distribute
       uploaded exams (admin-only, only available once the system is locked).
       Once exams have been distributed, the unlock button disappears — the
       lock is permanent at that point. -->
  <v-row class="mb-2">
    <v-col cols="auto">
      <v-btn
        v-if="canControlUploads && !systemState?.distributed"
        :color="systemState?.examsLocked ? 'warning' : 'primary'"
        :prepend-icon="systemState?.examsLocked ? 'mdi-lock-open-variant' : 'mdi-lock'"
        :loading="busy"
        :disabled="busy"
        :text="systemState?.examsLocked ? 'Desbloquear Exames' : 'Bloquear Exames'"
        @click="onToggleLock"
      ></v-btn>
    </v-col>
    <v-col cols="auto">
      <v-btn
        v-if="canDistribute && !systemState?.distributed"
        color="primary"
        prepend-icon="mdi-account-multiple-plus-outline"
        :loading="distributing"
        :disabled="distributing || uploadedCount === 0 || !systemState?.examsLocked"
        :title="!systemState?.examsLocked
          ? 'Bloqueie os exames antes de distribuir'
          : ''"
        :text="`Distribuir Exames (${uploadedCount})`"
        @click="onDistributeAll"
      ></v-btn>
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
    <template v-slot:[`item.grade`]="{ item }">
      {{ item.grade == null ? '--' : item.grade }}
    </template>
    <template v-slot:[`item.actions`]="{ item }">
      <v-btn
        v-if="hasFragments(item)"
        icon="mdi-image-multiple"
        size="small"
        :to="`/exams/${item.id}/fragments`"
        title="Ver fragmentos"
      ></v-btn>
    </template>
  </v-data-table>
</template>

<script setup lang="ts">
import type PersonDto from '@/models/PersonDto'
import type SchoolDto from '@/models/SchoolDto'
import type SubjectDto from '@/models/SubjectDto'
import type ExamDto from '@/models/ExamDto'
import type SystemStateDto from '@/models/SystemStateDto'
import RemoteService from '@/services/RemoteService'
import { useAuthStore } from '@/stores/auth'
import { computed, ref } from 'vue'

const auth = useAuthStore()

const pdfFile = ref<File | File[] | undefined>(undefined)
const newStudentId = ref<number | undefined>(undefined)
const newSubjectId = ref<number | undefined>(undefined)

const filterSchoolId = ref<number | undefined>(undefined)
const filterSubjectId = ref<number | undefined>(undefined)

const students = ref<PersonDto[]>([])
const schools = ref<SchoolDto[]>([])
const subjects = ref<SubjectDto[]>([])
const exams = ref<ExamDto[]>([])
const systemState = ref<SystemStateDto | null>(null)

const loadingStudents = ref(true)
const loadingSchools = ref(true)
const loadingSubjects = ref(true)
const loadingExams = ref(false)
const busy = ref(false)
const distributing = ref(false)
const submitting = ref(false)

const headers = [
  { title: 'ID', key: 'id', value: 'id', sortable: true },
  { title: 'Ficheiro', key: 'originalFilename', value: 'originalFilename', sortable: true },
  { title: 'Aluno', key: 'studentName', value: 'studentName', sortable: true },
  { title: 'Escola', key: 'schoolName', value: 'schoolName', sortable: true },
  { title: 'Disciplina', key: 'subjectName', value: 'subjectName', sortable: true },
  { title: 'Estado', key: 'status', value: 'status', sortable: true },
  { title: 'Nota', key: 'grade', value: 'grade', sortable: true },
  { title: 'Ações', key: 'actions', value: 'actions', sortable: false }
]

function pickFile(): File | undefined {
  const f = pdfFile.value
  if (!f) return undefined
  if (Array.isArray(f)) return f[0]
  return f
}

const isAdminView = computed(
  () => auth.user?.role === 'ADMINISTRATOR' && !auth.isImpersonating
)

const canUpload = computed(
  () => auth.user?.role === 'SCHOOL_STAFF' && auth.hasPermission('EXAM_CREATE')
)
// Staff only ever see exams from their own school (the backend filters by
// school), so a school filter would be a no-op — hide it from them.
const canFilterSchool = computed(
  () => auth.user?.role !== 'SCHOOL_STAFF'
)
const canSubmitForm = computed(
  () => !!pickFile() && !!newStudentId.value && !!newSubjectId.value
)
const canDistribute = computed(
  () => auth.user?.role === 'ADMINISTRATOR' && !auth.isImpersonating && auth.hasPermission('EXAM_DISTRIBUTE')
)
const canControlUploads = computed(
  () => auth.user?.role === 'ADMINISTRATOR' && !auth.isImpersonating && auth.hasPermission('EXAM_LOCK')
)

const uploadedCount = computed(
  () => exams.value.filter((e) => e.status === 'UPLOADED').length
)

const uploadsDisabled = computed(() => systemState.value?.examsLocked === true)

const filteredExams = computed(() =>
  exams.value.filter((e) => {
    if (filterSchoolId.value != null && e.schoolId !== filterSchoolId.value) return false
    if (filterSubjectId.value != null && e.subjectId !== filterSubjectId.value) return false
    return true
  })
)

function hasFragments(e: ExamDto): boolean {
  return (
    e.status === 'UPLOADED' ||
    e.status === 'DISTRIBUTED' ||
    e.status === 'CORRECTED' ||
    e.status === 'RELEASED'
  )
}

;(async () => {
  try {
    // The backend already filters people by the caller's role: admins see
    // everyone, school staff see only their own school's people, teachers
    // and students only see themselves. We just keep the STUDENTs out of
    // that already-filtered list to populate the upload picker.
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

async function refreshSystemState() {
  try {
    systemState.value = await RemoteService.getSystemState()
  } catch {
    systemState.value = null
  }
}

refreshExams()
refreshSystemState()

async function uploadExam() {
  const file = pickFile()
  if (!file || !newStudentId.value || !newSubjectId.value) return
  submitting.value = true
  try {
    await RemoteService.uploadExam(file, newStudentId.value, newSubjectId.value)
    pdfFile.value = undefined
    await refreshExams()
    await refreshSystemState()
  } catch {
    await refreshSystemState()
  } finally {
    submitting.value = false
  }
}

async function onDistributeAll() {
  if (uploadedCount.value === 0) return
  distributing.value = true
  try {
    await RemoteService.distributeAllExams()
    await refreshExams()
  } finally {
    distributing.value = false
  }
}

async function onToggleLock() {
  if (!systemState.value) return
  busy.value = true
  try {
    if (systemState.value.examsLocked) {
      systemState.value = await RemoteService.unlockUploads()
    } else {
      systemState.value = await RemoteService.lockUploads()
    }
    await refreshExams()
  } finally {
    busy.value = false
  }
}
</script>
