<template>
  <h2 class="text-left ml-1">Disponibilização de provas</h2>

  <!-- Bulk Release por disciplina -->
  <v-card variant="outlined" class="mb-6 text-left">
    <v-card-title class="text-subtitle-1">
      <v-icon icon="mdi-broadcast" class="mr-2" />
      Disponibilização em lote
    </v-card-title>
    <v-card-text>
      <p class="text-medium-emphasis mb-3">
        Disponibiliza em lote as provas já corrigidas de uma disciplina da sua
        escola. Todos os exames <strong>Corrigidos</strong> dessa disciplina
        passam a <strong>Disponíveis</strong> para os respetivos alunos.
      </p>
      <v-row>
        <v-col cols="12" md="6">
          <v-select
            v-model="bulkSubjectId"
            :items="subjects"
            :loading="loadingSubjects"
            item-title="name"
            item-value="id"
            label="Disciplina"
            clearable
            density="comfortable"
          ></v-select>
        </v-col>
        <v-col cols="auto" class="d-flex align-center">
          <v-btn
            color="primary"
            prepend-icon="mdi-broadcast"
            :loading="bulkBusy"
            :disabled="bulkSubjectId == null || bulkBusy"
            text="Disponibilizar todos"
            @click="onBulkRelease"
          ></v-btn>
        </v-col>
      </v-row>
      <v-alert
        v-if="bulkResult != null"
        :type="bulkResult > 0 ? 'success' : 'info'"
        variant="tonal"
        class="mt-3"
      >
        {{
          bulkResult > 0
            ? `${bulkResult} exame(s) disponibilizado(s).`
            : 'Não havia exames corrigidos para essa disciplina.'
        }}
      </v-alert>
    </v-card-text>
  </v-card>

  <!-- Pedidos de consulta pendentes -->
  <v-card variant="outlined" class="mb-6 text-left">
    <v-card-title class="text-subtitle-1 d-flex align-center">
      <v-icon icon="mdi-email-outline" class="mr-2" />
      Pedidos de consulta
      <v-chip size="x-small" variant="flat" color="warning" class="ml-2">
        {{ pending.length }}
      </v-chip>
    </v-card-title>
    <v-card-text>
      <v-data-table
        :headers="pendingHeaders"
        :items="pending"
        :loading="loading"
        item-key="id"
        density="comfortable"
        class="text-left"
        no-data-text="Não há pedidos pendentes."
      >
        <template v-slot:[`item.status`]="{ item }">
          <v-chip
            :color="consultationStatusColor(item.status)"
            :text="consultationStatusLabel(item.status)"
            size="small"
            variant="tonal"
          ></v-chip>
        </template>
        <template v-slot:[`item.actions`]="{ item }">
          <v-btn
            color="success"
            size="small"
            variant="tonal"
            prepend-icon="mdi-check"
            :loading="busyId === item.id"
            :disabled="busyId === item.id"
            text="Aprovar"
            @click="approve(item.id)"
          ></v-btn>
          <v-btn
            class="ml-2"
            color="error"
            size="small"
            variant="tonal"
            prepend-icon="mdi-close"
            :loading="busyId === item.id"
            :disabled="busyId === item.id"
            text="Rejeitar"
            @click="reject(item.id)"
          ></v-btn>
        </template>
      </v-data-table>
    </v-card-text>
  </v-card>

  <!-- Histórico de decisões -->
  <v-card variant="outlined" class="mb-6 text-left">
    <v-card-title class="text-subtitle-1">
      <v-icon icon="mdi-history" class="mr-2" />
      Histórico de Pedidos de Consulta
    </v-card-title>
    <v-card-text>
      <v-data-table
        :headers="historyHeaders"
        :items="history"
        :loading="loading"
        item-key="id"
        density="comfortable"
        class="text-left"
        no-data-text="Sem histórico."
      >
        <template v-slot:bottom></template>
        <template v-slot:[`item.status`]="{ item }">
          <v-chip
            :color="consultationStatusColor(item.status)"
            :text="consultationStatusLabel(item.status)"
            size="small"
            variant="tonal"
          ></v-chip>
        </template>
      </v-data-table>
    </v-card-text>
  </v-card>

  <!-- Pedidos de revisão (Fase 5) — histórico read-only por escola. As
       decisões em si (notas definitivas) estão nos tasks de revisão, visíveis
       na vista de classificações. -->
  <v-card variant="outlined" class="text-left">
    <v-card-title class="text-subtitle-1">
      <v-icon icon="mdi-file-edit-outline" class="mr-2" />
      Pedidos de revisão
    </v-card-title>
    <v-card-text>
      <v-data-table
        :headers="revisionHeaders"
        :items="revisionRequests"
        :loading="loadingRevisions"
        item-key="id"
        density="comfortable"
        class="text-left"
        no-data-text="Sem pedidos de revisão."
      >
        <template v-slot:bottom></template>
        <template v-slot:[`item.questions`]="{ item }">
          {{ item.items?.length ?? 0 }}
        </template>
        <template v-slot:[`item.revisionState`]="{ item }">
          <v-chip
            :color="revisionStateColor(item)"
            :text="revisionStateLabel(item)"
            size="small"
            variant="tonal"
          ></v-chip>
        </template>
      </v-data-table>
    </v-card-text>
  </v-card>
</template>

<script setup lang="ts">
import type SubjectDto from '@/models/SubjectDto'
import type ConsultationRequestDto from '@/models/ConsultationRequestDto'
import type ExamDto from '@/models/ExamDto'
import type RevisionRequestDto from '@/models/RevisionRequestDto'
import RemoteService from '@/services/RemoteService'
import { computed, ref } from 'vue'
import { consultationStatusLabel, consultationStatusColor } from '@/utils/labels'

const requests = ref<ConsultationRequestDto[]>([])
const loading = ref(true)

const subjects = ref<SubjectDto[]>([])
const loadingSubjects = ref(true)

const bulkSubjectId = ref<number | undefined>(undefined)
const bulkBusy = ref(false)
const bulkResult = ref<number | null>(null)

const busyId = ref<number | null>(null)

// --- Pedidos de revisão (Fase 5) ---
const revisionRequests = ref<RevisionRequestDto[]>([])
const exams = ref<ExamDto[]>([])
const loadingRevisions = ref(true)

const revisionHeaders = [
  { title: 'Aluno', key: 'studentName', value: 'studentName', sortable: true },
  { title: 'Disciplina', key: 'subjectName', value: 'subjectName', sortable: true },
  { title: 'Nº perguntas', key: 'questions', value: 'questions', sortable: true },
  { title: 'Estado da revisão', key: 'revisionState', value: 'revisionState', sortable: false }
]

function examFor(request: RevisionRequestDto): ExamDto | undefined {
  return exams.value.find((e) => e.id === request.examId)
}

function revisionStateLabel(request: RevisionRequestDto): string {
  const exam = examFor(request)
  if (exam?.revisionDistributed) return 'Concluída'
  const deadline = exam?.revisionDeadline ? Date.parse(exam.revisionDeadline) : null
  if (deadline == null) return '—'
  if (Date.now() >= deadline) return 'Em distribuição'
  return `Janela aberta até ${new Date(deadline).toLocaleString('pt-PT')}`
}

function revisionStateColor(request: RevisionRequestDto): string {
  const exam = examFor(request)
  if (exam?.revisionDistributed) return 'success'
  const deadline = exam?.revisionDeadline ? Date.parse(exam.revisionDeadline) : null
  if (deadline == null) return 'grey'
  return Date.now() >= deadline ? 'warning' : 'success'
}

const pendingHeaders = [
  { title: 'Aluno', key: 'studentName', value: 'studentName', sortable: true },
  { title: 'Disciplina', key: 'subjectName', value: 'subjectName', sortable: true },
  { title: 'Estado', key: 'status', value: 'status', sortable: true },
  { title: 'Ações', key: 'actions', value: 'actions', sortable: false }
]

const historyHeaders = [
  { title: 'Aluno', key: 'studentName', value: 'studentName', sortable: true },
  { title: 'Disciplina', key: 'subjectName', value: 'subjectName', sortable: true },
  { title: 'Estado', key: 'status', value: 'status', sortable: true },
  { title: 'Decidido por', key: 'decidedByName', value: 'decidedByName', sortable: true }
]

const pending = computed(() => requests.value.filter((r) => r.status === 'PENDING'))
const history = computed(() =>
  requests.value
    .filter((r) => r.status !== 'PENDING')
    .slice()
    .sort((a, b) => (b.decidedAt ?? '').localeCompare(a.decidedAt ?? ''))
)

async function refresh() {
  loading.value = true
  try {
    requests.value = await RemoteService.getConsultationRequests()
  } finally {
    loading.value = false
  }
}

;(async () => {
  try {
    subjects.value = await RemoteService.getSubjects()
  } finally {
    loadingSubjects.value = false
  }
})()
refresh()
refreshRevisions()

async function refreshRevisions() {
  loadingRevisions.value = true
  try {
    const [revs, allExams] = await Promise.all([
      RemoteService.getRevisionRequests(),
      RemoteService.getExams()
    ])
    revisionRequests.value = revs
    exams.value = allExams
  } catch {
    revisionRequests.value = []
    exams.value = []
  } finally {
    loadingRevisions.value = false
  }
}

async function approve(id: number | undefined) {
  if (id == null) return
  busyId.value = id
  try {
    await RemoteService.approveConsultationRequest(id)
    await refresh()
  } catch {
    // toast already pushed
  } finally {
    busyId.value = null
  }
}

async function reject(id: number | undefined) {
  if (id == null) return
  busyId.value = id
  try {
    await RemoteService.rejectConsultationRequest(id)
    await refresh()
  } catch {
    // toast already pushed
  } finally {
    busyId.value = null
  }
}

async function onBulkRelease() {
  if (bulkSubjectId.value == null) return
  bulkBusy.value = true
  bulkResult.value = null
  try {
    const ids = await RemoteService.bulkReleaseExams(bulkSubjectId.value)
    bulkResult.value = ids.length
    await refresh()
    await refreshRevisions()
  } catch {
    bulkResult.value = null
  } finally {
    bulkBusy.value = false
  }
}
</script>