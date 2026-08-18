<template>
  <h2 class="text-left ml-1">As minhas provas</h2>

  <p class="text-left text-medium-emphasis mb-4">
    Aqui constam os seus exames. Só pode consultar o PDF e as classificações
    depois de o exame estar totalmente corrigido e a escola lhe ter concedido
    acesso.
  </p>

  <v-data-table
    :headers="headers"
    :items="exams"
    :loading="loading"
    item-key="id"
    class="text-left"
    no-data-text="Ainda não tem exames."
  >
    <template v-slot:[`item.status`]="{ item }">
      {{ examStatusLabel(item.status) }}
    </template>
    <template v-slot:[`item.revision`]="{ item }">
      <v-chip
        v-if="item.status === 'RELEASED' && revisionRequestFor(item.id) != null"
        :color="revisionColor(item)"
        :text="revisionLabel(item)"
        size="small"
        variant="tonal"
      ></v-chip>
      <span v-else class="text-medium-emphasis">—</span>
    </template>
    <template v-slot:[`item.grade`]="{ item }">
      {{ item.status === 'RELEASED' && item.grade != null ? item.grade : '--' }}
    </template>
    <template v-slot:[`item.actions`]="{ item }">
      <v-btn
        v-if="item.status === 'RELEASED'"
        color="primary"
        size="small"
        prepend-icon="mdi-eye-outline"
        :to="`/my-exams/${item.id ?? ''}`"
        text="Consultar"
      ></v-btn>
      <v-btn
        v-else-if="item.status === 'CORRECTED' && requestFor(item.id)?.status !== 'PENDING'"
        color="secondary"
        size="small"
        variant="tonal"
        prepend-icon="mdi-hand-extended-outline"
        :loading="busyExamId === item.id"
        :disabled="busyExamId === item.id"
        :text="requestFor(item.id)?.status === 'REJECTED' ? 'Pedir de novo' : 'Pedir consulta'"
        @click="askConsultation(item.id)"
      ></v-btn>
      <span v-else-if="item.status === 'CORRECTED'" class="text-medium-emphasis">
        A aguardar aprovação
      </span>
      <span v-else class="text-medium-emphasis">—</span>
    </template>
  </v-data-table>
</template>

<script setup lang="ts">
import type ExamDto from '@/models/ExamDto'
import type ConsultationRequestDto from '@/models/ConsultationRequestDto'
import type RevisionRequestDto from '@/models/RevisionRequestDto'
import RemoteService from '@/services/RemoteService'
import { ref } from 'vue'

const exams = ref<ExamDto[]>([])
const requests = ref<ConsultationRequestDto[]>([])
const revisionRequests = ref<RevisionRequestDto[]>([])
const loading = ref(true)
const busyExamId = ref<number | null>(null)

const headers = [
  { title: 'Disciplina', key: 'subjectName', value: 'subjectName', sortable: true },
  { title: 'Estado', key: 'status', value: 'status', sortable: true },
  { title: 'Revisão', key: 'revision', value: 'revision', sortable: false },
  { title: 'Nota', key: 'grade', value: 'grade', sortable: true },
  { title: 'Ação', key: 'actions', value: 'actions', sortable: false }
]

function examStatusLabel(status: string | undefined): string {
  switch (status) {
    case 'UPLOADED': return 'Submetido'
    case 'DISTRIBUTED': return 'Em correção'
    case 'CORRECTED': return 'Corrigido'
    case 'RELEASED': return 'Disponível'
    default: return status ?? ''
  }
}

function revisionLabel(exam: ExamDto): string {
  // The chip only renders when this student has a revision request for the
  // exam (see template). revisionDistributed is an exam-level flag ("the
  // scheduler closed this exam's window"), true for every released exam once
  // 48h pass — not a per-student "processed" signal.
  const deadline = exam.revisionDeadline ? Date.parse(exam.revisionDeadline) : null
  if (exam.revisionDistributed) return 'Concluída'
  if (deadline != null && Date.now() >= deadline) return 'Em curso'
  return 'Submetida'
}

function revisionColor(exam: ExamDto): string {
  const deadline = exam.revisionDeadline ? Date.parse(exam.revisionDeadline) : null
  if (exam.revisionDistributed) return 'success'
  if (deadline != null && Date.now() >= deadline) return 'warning'
  return 'info'
}

function requestFor(examId: number | undefined): ConsultationRequestDto | undefined {
  if (examId == null) return undefined
  return requests.value.find((r) => r.examId === examId)
}

function revisionRequestFor(examId: number | undefined): RevisionRequestDto | undefined {
  if (examId == null) return undefined
  return revisionRequests.value.find((r) => r.examId === examId)
}

async function refresh() {
  loading.value = true
  try {
    const [allExams, myReqs, myRevisionReqs] = await Promise.all([
      RemoteService.getExams(),
      RemoteService.getMyConsultationRequests(),
      RemoteService.getMyRevisionRequests()
    ])
    exams.value = allExams
    requests.value = myReqs
    revisionRequests.value = myRevisionReqs
  } finally {
    loading.value = false
  }
}

async function askConsultation(examId: number | undefined) {
  if (examId == null) return
  busyExamId.value = examId
  try {
    await RemoteService.createConsultationRequest(examId)
    await refresh()
  } catch {
    // the error toast is already pushed by RemoteService.handleError
  } finally {
    busyExamId.value = null
  }
}

refresh()
</script>