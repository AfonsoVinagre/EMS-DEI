<template>
  <v-btn
    icon="mdi-arrow-left"
    size="small"
    variant="text"
    :to="'/exams'"
    title="Voltar aos exames"
  ></v-btn>
  <h2 class="text-left ml-1">
    Fragmentos do exame #{{ examId }}
    <span v-if="exam" class="text-medium-emphasis text-h6">
      ({{ exam.originalFilename }})
    </span>
  </h2>

  <v-row v-if="exam">
    <v-col cols="12" md="6">
      <strong>Aluno:</strong> {{ exam.studentName }}
    </v-col>
    <v-col cols="12" md="6">
      <strong>Escola:</strong> {{ exam.schoolName }}
    </v-col>
    <v-col cols="12" md="6">
      <strong>Disciplina:</strong> {{ exam.subjectName }}
    </v-col>
  </v-row>

  <v-data-table
    :headers="headers"
    :items="tableRows"
    :loading="loading"
    item-key="id"
    class="text-left"
    no-data-text="Este exame ainda não tem fragmentos."
  >
    <template v-slot:[`item.pageNumber`]="{ item }">
      <span v-if="(item as any)._isPdf">—</span>
      <span v-else>{{ item.pageNumber }}</span>
    </template>
    <template v-slot:[`item.subjectName`]="{ item }">
      <span v-if="(item as any)._isPdf" class="text-medium-emphasis">
        Versão original, sem segmentação
      </span>
      <span v-else>{{ item.subjectName }}</span>
    </template>
    <template v-slot:[`item.status`]="{ item }">
      <span v-if="(item as any)._isPdf" class="text-medium-emphasis">
        Não segmentado
      </span>
      <span v-else>{{ fragmentStatusLabel(item.status) }}</span>
    </template>
    <template v-slot:[`item.actions`]="{ item }">
      <v-btn
        v-if="(item as any)._isPdf"
        :loading="pdfLoading"
        :disabled="pdfLoading"
        prepend-icon="mdi-file-pdf-box"
        color="primary"
        size="small"
        text="Abrir PDF"
        @click="openPdf"
      ></v-btn>
      <v-btn
        v-else
        icon="mdi-image-multiple"
        size="small"
        :title="'Abrir fragmento'"
        @click="openFragment(item)"
      ></v-btn>
    </template>
  </v-data-table>

  <v-dialog v-model="pdfDrawer" max-width="900">
    <v-card>
      <v-card-title>PDF original do exame #{{ examId }}</v-card-title>
      <v-card-text>
        <iframe
          v-if="pdfUrl"
          :src="pdfUrl"
          width="100%"
          height="700"
          style="border: 0"
        ></iframe>
        <v-progress-circular v-else indeterminate color="primary"></v-progress-circular>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn text="Fechar" @click="closePdf"></v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>

  <v-dialog v-model="drawer" max-width="900">
    <v-card v-if="selected">
      <v-card-title>
        Fragmento #{{ selected.id }} — página {{ selected.pageNumber }}
      </v-card-title>
      <v-card-text>
        <div class="text-left mb-2">
          <strong>Estado:</strong> {{ fragmentStatusLabel(selected.status) }}
          <span v-if="latestTask" class="text-medium-emphasis">
            — {{ phaseLabel(latestTask.phase) }}
          </span>
        </div>
        <v-img
          v-if="imageUrl"
          :src="imageUrl"
          max-height="700"
          contain
        ></v-img>
        <v-progress-circular v-else indeterminate color="primary"></v-progress-circular>

        <v-divider class="my-4"></v-divider>
        <h3 class="text-left">Histórico de tarefas</h3>
        <v-data-table
          :headers="taskHeaders"
          :items="tasks"
          :loading="loadingTasks"
          item-key="id"
          no-data-text="Sem tarefas."
          class="text-left"
        >
          <template v-slot:[`item.phase`]="{ item }">
            {{ phaseLabel(item.phase) }}
          </template>
          <template v-slot:[`item.actions`]="{ item }">
            <v-btn
              v-if="canSubmit(item)"
              color="primary"
              size="small"
              variant="tonal"
              @click="openGrade(item)"
            >
              Submeter nota
            </v-btn>
            <span v-else-if="item.status === 'SUBMITTED'" class="text-success">
              ✓ {{ item.grade }}
            </span>
          </template>
        </v-data-table>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn text="Fechar" @click="closeDrawer"></v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>

  <v-dialog v-model="gradeDialog" max-width="400">
    <v-card v-if="grading">
      <v-card-title>
        Submeter nota —
        {{ grading.teacherName }} (fragmento {{ grading.fragmentId }})
      </v-card-title>
      <v-card-text>
        <v-text-field
          v-model.number="gradeInput"
          type="number"
          min="0"
          label="Nota"
          :rules="[(v: number) => (v == null || v < 0) ? 'A nota não pode ser negativa' : true]"
        ></v-text-field>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn text="Cancelar" @click="gradeDialog = false"></v-btn>
        <v-btn color="primary" :disabled="gradeInput == null || gradeInput < 0" text="Submeter" @click="submitGrade"></v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>

</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type ExamDto from '@/models/ExamDto'
import type FragmentDto from '@/models/FragmentDto'
import type CorrectionTaskDto from '@/models/CorrectionTaskDto'
import RemoteService from '@/services/RemoteService'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const auth = useAuthStore()

const examId = Number(route.params.id as string)

const exam = ref<ExamDto | null>(null)
const fragments = ref<FragmentDto[]>([])
const loading = ref(false)
const selected = ref<FragmentDto | null>(null)
const drawer = ref(false)
const imageUrl = ref<string | null>(null)
const tasks = ref<CorrectionTaskDto[]>([])
const loadingTasks = ref(false)

const pdfDrawer = ref(false)
const pdfUrl = ref<string | null>(null)
const pdfLoading = ref(false)

const gradeDialog = ref(false)
const grading = ref<CorrectionTaskDto | null>(null)
const gradeInput = ref<number | null>(null)

const headers = [
  { title: 'ID', key: 'id', value: 'id', sortable: true },
  { title: 'Página', key: 'pageNumber', value: 'pageNumber', sortable: true },
  { title: 'Disciplina', key: 'subjectName', value: 'subjectName', sortable: true },
  { title: 'Estado', key: 'status', value: 'status', sortable: true },
  { title: 'Ações', key: 'actions', value: 'actions', sortable: false }
]

// Sentinel id used to render the synthetic "PDF completo" row at the top of
// the fragments table. Real fragment ids are positive long values from the
// DB, so a negative sentinel cannot collide.
const PDF_ROW_ID = -1

/**
 * Builds the table rows: a synthetic first row pointing at the unsegmented
 * PDF, followed by every real fragment. Each row exposes `id` so the
 * item-key + sentinel logic work uniformly.
 */
const tableRows = computed<(FragmentDto & { _isPdf?: boolean })[]>(() => [
  {
    id: PDF_ROW_ID,
    examId,
    pageNumber: 0,
    subjectName: '',
    status: 'PDF',
    _isPdf: true
  },
  ...fragments.value
])

const taskHeaders = [
  { title: 'Fase', key: 'phase', value: 'phase', sortable: true },
  { title: 'Professor', key: 'teacherName', value: 'teacherName', sortable: true },
  { title: 'Estado', key: 'status', value: 'status', sortable: true },
  { title: 'Nota', key: 'grade', value: 'grade', sortable: false },
  { title: 'Ações', key: 'actions', value: 'actions', sortable: false }
]

/**
 * Translates the @Transient Fragment.status (computed in the backend from
 * the latest CorrectionTask) to a human-readable Portuguese label. The
 * status reflects where the CorrectionTask pipeline is, not the exam's
 * own lifecycle.
 */
function fragmentStatusLabel(status: string | undefined): string {
  switch (status) {
    case 'UNASSIGNED': return 'Pendente'
    case 'IN_CORRECTION': return 'Em correção'
    case 'IN_REVISION': return 'Em revisão'
    case 'CORRECTED': return 'Submetido'
    case 'REVISION_SUBMITTED': return 'Revisão submetida'
    default: return status ?? ''
  }
}

function phaseLabel(phase: CorrectionTaskDto['phase'] | undefined): string {
  switch (phase) {
    case 'PRIMARY': return 'Em correção'
    case 'REVISION': return 'Em revisão'
    default: return ''
  }
}

/** The most recent CorrectionTask for the selected fragment. */
const latestTask = computed(() => {
  if (!tasks.value.length) return null
  return tasks.value.slice().sort((a, b) => {
    const ta = a.assignedAt ?? ''
    const tb = b.assignedAt ?? ''
    return tb.localeCompare(ta)
  })[0] ?? null
})

function canSubmit(t: CorrectionTaskDto): boolean {
  if (t.status === 'SUBMITTED') return false
  if (t.teacherId == null) return false
  // Only the teacher assigned to this task may submit a grade. Admins do
  // not have a back door — once exams are distributed, correction belongs
  // to the assigned teacher.
  return auth.user?.id === t.teacherId
}

onMounted(async () => {
  try {
    exam.value = await RemoteService.getExams().then((all) => all.find((e) => e.id === examId) ?? null)
  } catch { /* ignore */ }
  await refreshFragments()
})

onBeforeUnmount(() => {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
})

async function refreshFragments() {
  loading.value = true
  try {
    fragments.value = await RemoteService.getFragmentsByExam(examId)
  } finally {
    loading.value = false
  }
}

async function openFragment(item: FragmentDto) {
  await openFragmentDialog(item)
}

async function openPdf() {
  pdfDrawer.value = true
  pdfLoading.value = true
  if (pdfUrl.value) {
    URL.revokeObjectURL(pdfUrl.value)
    pdfUrl.value = null
  }
  try {
    pdfUrl.value = await RemoteService.getExamPdfUrl(examId)
  } catch {
    pdfUrl.value = null
  } finally {
    pdfLoading.value = false
  }
}

function closePdf() {
  pdfDrawer.value = false
  if (pdfUrl.value) {
    URL.revokeObjectURL(pdfUrl.value)
    pdfUrl.value = null
  }
}

async function openFragmentDialog(f: FragmentDto) {
  selected.value = f
  drawer.value = true
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = null
  }
  if (f.id != null) {
    try {
      imageUrl.value = await RemoteService.getFragmentImageUrl(f.id)
    } catch {
      imageUrl.value = null
    }
    await loadTasks(f.id)
  }
}

async function loadTasks(fragmentId: number) {
  loadingTasks.value = true
  try {
    tasks.value = await RemoteService.getFragmentTasks(fragmentId)
  } catch {
    tasks.value = []
  } finally {
    loadingTasks.value = false
  }
}

function closeDrawer() {
  drawer.value = false
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = null
  }
}

function openGrade(t: CorrectionTaskDto) {
  grading.value = t
  gradeInput.value = t.grade ?? 0
  gradeDialog.value = true
}

async function submitGrade() {
  if (!grading.value || gradeInput.value == null || gradeInput.value < 0) return
  await RemoteService.submitGrade(grading.value.id!, gradeInput.value)
  gradeDialog.value = false
  if (selected.value?.id != null) await loadTasks(selected.value.id)
}
</script>
