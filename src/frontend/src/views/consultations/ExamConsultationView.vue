<template>
  <v-btn
    icon="mdi-arrow-left"
    size="small"
    variant="text"
    :to="'/my-exams'"
    title="Voltar às minhas provas"
  ></v-btn>
  <h2 class="text-left ml-1">
    Consulta do exame
    <span v-if="exam" class="text-medium-emphasis text-h6">
      ({{ exam.subjectName }}) — Nota: {{ exam.grade ?? '--' }}
    </span>
  </h2>

  <v-alert
    v-if="loadError"
    type="error"
    variant="tonal"
    class="mt-2 text-left"
  >
    Não tem acesso a este exame. Apenas pode consultar exames seus já
    disponibilizados pela escola.
  </v-alert>

  <template v-if="exam && !loadError">
    <!-- Painel de revisão (Fase 5): só aparece se houver pedido OU a janela
         ainda estiver aberta (para o aluno poder fazer o primeiro pedido).
         Sem pedido + janela encerrada → não há nada a mostrar. -->
    <v-card
      v-if="revisionState !== 'none'"
      variant="outlined"
      class="mt-3 text-left"
    >
      <v-card-title class="text-subtitle-1 d-flex align-center">
        <v-icon icon="mdi-file-edit-outline" class="mr-2"></v-icon>
        Revisão da prova
        <v-spacer></v-spacer>
        <v-chip
          v-if="revisionState === 'open'"
          color="success" size="small" variant="tonal"
          :text="`Janela aberta — ${countdown}`"
        ></v-chip>
        <v-chip
          v-else-if="revisionState === 'submitted'"
          color="info" size="small" variant="tonal"
          text="Pedido submetido"
        ></v-chip>
        <v-chip
          v-else-if="revisionState === 'processing'"
          color="warning" size="small" variant="tonal"
          text="Janela encerrada — processamento em curso"
        ></v-chip>
        <v-chip
          v-else-if="revisionState === 'done'"
          color="success" size="small" variant="tonal"
          text="Revisão concluída"
        ></v-chip>
      </v-card-title>

      <v-card-text>
        <v-alert
          v-if="revisionState === 'open'"
          type="info" variant="tonal" class="mb-3"
        >
          Tem até <strong>{{ deadlineLabel }}</strong> para pedir a revisão de
          perguntas específicas. Selecione as perguntas, justifique o pedido e
          submeta. Só pode fazer um pedido por exame.
        </v-alert>
        <v-alert
          v-if="revisionState === 'submitted'"
          type="info" variant="tonal" class="mb-3"
        >
          O seu pedido de revisão foi submetido. Quando a janela fechar, as
          perguntas indicadas serão redistribuídas para revisão e as notas
          atualizadas passarão a ser definitivas.
        </v-alert>
        <v-alert
          v-if="revisionState === 'processing'"
          type="warning" variant="tonal" class="mb-3"
        >
          A janela de revisão encerrou. O processamento dos pedidos está em
          curso — as notas serão atualizadas em breve.
        </v-alert>
        <v-alert
          v-if="revisionState === 'done'"
          type="success" variant="tonal" class="mb-3"
        >
          A revisão foi processada. As classificações apresentadas abaixo são
          as definitivas.
        </v-alert>

        <!-- Formulário de revisão (só quando a janela está aberta e sem pedido). -->
        <template v-if="revisionState === 'open'">
          <v-data-table
            :headers="revisionHeaders"
            :items="fragments"
            item-key="id"
            :items-per-page="-1"
            class="text-left"
          >
            <template v-slot:bottom></template>
            <template v-slot:[`item.select`]="{ item }">
              <v-checkbox
                :model-value="isSelected(item)"
                hide-details
                density="compact"
                @update:model-value="toggleSelected(item, $event)"
              ></v-checkbox>
            </template>
            <template v-slot:[`item.grade`]="{ item }">
              {{ item.grade == null ? '--' : item.grade }}
            </template>
            <template v-slot:[`item.justification`]="{ item }">
              <v-textarea
                v-if="isSelected(item)"
                :model-value="justifications[item.id ?? -1]"
                @update:model-value="justifications[item.id ?? -1] = $event"
                label="Justificação"
                variant="outlined"
                density="compact"
                rows="2"
                hide-details
                class="mt-1"
              ></v-textarea>
              <span v-else class="text-medium-emphasis text-caption">
                Selecione a pergunta para justificar
              </span>
            </template>
          </v-data-table>

          <div class="d-flex align-center mt-3">
            <v-btn
              color="primary"
              prepend-icon="mdi-send"
              :loading="submittingRevision"
              :disabled="!canSubmitRevision"
              text="Submeter pedido de revisão"
              @click="submitRevision"
            ></v-btn>
            <span v-if="submitError" class="text-error ml-3 text-caption">
              {{ submitError }}
            </span>
          </div>
        </template>
      </v-card-text>
    </v-card>

    <v-row class="mt-2">
      <!-- Lado esquerdo: PDF original do exame -->
      <v-col cols="12" md="7">
        <h3 class="text-left mb-2">PDF original</h3>
        <v-card variant="outlined">
          <iframe
            v-if="pdfUrl"
            :src="pdfUrl"
            width="100%"
            height="700"
            style="border: 0"
          ></iframe>
          <div v-else class="pa-6 text-center">
            <v-progress-circular indeterminate color="primary"></v-progress-circular>
          </div>
        </v-card>
      </v-col>

      <!-- Lado direito: classificações por fragmento (pergunta/página) -->
      <v-col cols="12" md="5">
        <h3 class="text-left mb-2">Classificações por pergunta</h3>
        <v-data-table
          :headers="headers"
          :items="fragments"
          :loading="loadingFragments"
          item-key="id"
          :items-per-page="-1"
          class="text-left"
          no-data-text="Sem fragmentos."
        >
          <template v-slot:bottom></template>
          <template v-slot:[`item.grade`]="{ item }">
            {{ item.grade == null ? '--' : item.grade }}
          </template>
          <template v-slot:[`item.actions`]="{ item }">
            <v-btn
              icon="mdi-image-multiple"
              size="small"
              title="Ver pergunta"
              @click="openFragment(item)"
            ></v-btn>
          </template>
        </v-data-table>
      </v-col>
    </v-row>
  </template>

  <v-dialog v-model="drawer" max-width="900">
    <v-card v-if="selected">
      <v-card-title>
        Pergunta {{ selected.pageNumber }}
      </v-card-title>
      <v-card-text>
        <div class="text-left mb-2">
          <strong>Nota:</strong> {{ selected.grade == null ? '--' : selected.grade }}
        </div>
        <v-img
          v-if="imageUrl"
          :src="imageUrl"
          max-height="700"
          contain
        ></v-img>
        <v-progress-circular v-else indeterminate color="primary"></v-progress-circular>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn text="Fechar" @click="closeDrawer"></v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import type ExamDto from '@/models/ExamDto'
import type FragmentDto from '@/models/FragmentDto'
import type RevisionRequestDto from '@/models/RevisionRequestDto'
import RemoteService from '@/services/RemoteService'

const route = useRoute()
const examId = Number(route.params.id as string)

const exam = ref<ExamDto | null>(null)
const fragments = ref<FragmentDto[]>([])
const loadingFragments = ref(false)
const loadError = ref(false)

const pdfUrl = ref<string | null>(null)
const drawer = ref(false)
const selected = ref<FragmentDto | null>(null)
const imageUrl = ref<string | null>(null)

// --- Revisão (Fase 5) ---
const myRevision = ref<RevisionRequestDto | null>(null)
const selectedFragments = ref<Set<number>>(new Set())
const justifications = reactive<Record<number, string>>({})
const submittingRevision = ref(false)
const submitError = ref<string | null>(null)

// Reactive clock so the countdown / window state update without a reload.
const now = ref(Date.now())
let clock: ReturnType<typeof setInterval> | null = null

const headers = [
  { title: 'Página', key: 'pageNumber', value: 'pageNumber', sortable: true },
  { title: 'Nota', key: 'grade', value: 'grade', sortable: true },
  { title: 'Ver', key: 'actions', value: 'actions', sortable: false }
]

const revisionHeaders = [
  { title: 'Revisar', key: 'select', value: 'select', sortable: false },
  { title: 'Página', key: 'pageNumber', value: 'pageNumber', sortable: true },
  { title: 'Nota atual', key: 'grade', value: 'grade', sortable: false },
  { title: 'Justificação', key: 'justification', value: 'justification', sortable: false }
]

const revisionDeadlineMs = computed(() =>
  exam.value?.revisionDeadline ? Date.parse(exam.value.revisionDeadline) : null
)

const revisionState = computed<'open' | 'submitted' | 'processing' | 'done' | 'none'>(() => {
  if (!exam.value || exam.value.status !== 'RELEASED') return 'none'
  const deadline = revisionDeadlineMs.value
  // The exam-level flag revisionDistributed only means "the scheduler has
  // closed this exam's window" — it is true for every released exam once 48h
  // pass, whether or not THIS student filed a request. "Done" / "processing"
  // only make sense for students who actually asked; gate on myRevision first.
  if (myRevision.value) {
    if (exam.value.revisionDistributed) return 'done'
    if (deadline != null && now.value >= deadline) return 'processing'
    return 'submitted'
  }
  // No request: window open → can still ask; window closed → nothing to show.
  if (deadline == null || now.value >= deadline) return 'none'
  return 'open'
})

const countdown = computed(() => {
  const deadline = revisionDeadlineMs.value
  if (deadline == null) return ''
  const ms = Math.max(0, deadline - now.value)
  const totalMin = Math.floor(ms / 60000)
  const h = Math.floor(totalMin / 60)
  const m = totalMin % 60
  return h > 0 ? `${h}h ${m}m` : `${m}m`
})

const deadlineLabel = computed(() => {
  const deadline = revisionDeadlineMs.value
  return deadline ? new Date(deadline).toLocaleString('pt-PT') : ''
})

function isSelected(item: FragmentDto): boolean {
  return item.id != null && selectedFragments.value.has(item.id)
}

function toggleSelected(item: FragmentDto, value: any) {
  if (item.id == null) return
  const next = new Set(selectedFragments.value)
  if (value) next.add(item.id)
  else {
    next.delete(item.id)
    delete justifications[item.id]
  }
  selectedFragments.value = next
}

const canSubmitRevision = computed(() => {
  if (selectedFragments.value.size === 0) return false
  for (const fid of selectedFragments.value) {
    const j = justifications[fid]?.trim()
    if (!j) return false
  }
  return true
})

async function submitRevision() {
  if (!canSubmitRevision.value) return
  submittingRevision.value = true
  submitError.value = null
  try {
    const items = Array.from(selectedFragments.value).map((fragmentId) => ({
      fragmentId,
      justification: justifications[fragmentId].trim()
    }))
    myRevision.value = await RemoteService.createRevisionRequest(examId, items)
    selectedFragments.value = new Set()
  } catch (err: any) {
    submitError.value = err?.message ?? 'Não foi possível submeter o pedido.'
  } finally {
    submittingRevision.value = false
  }
}

async function refreshRevision() {
  try {
    const mine = await RemoteService.getMyRevisionRequests()
    myRevision.value = mine.find((r) => r.examId === examId) ?? null
  } catch {
    myRevision.value = null
  }
}

onMounted(async () => {
  // The exam (and its grade) is only exposed to the student once released —
  // getExams() is server-scoped and grade-gated. A non-released exam simply
  // won't appear, which we surface as "no access".
  try {
    const all = await RemoteService.getExams()
    exam.value = all.find((e) => e.id === examId) ?? null
    if (!exam.value || exam.value.status !== 'RELEASED') {
      loadError.value = true
      return
    }
  } catch {
    loadError.value = true
    return
  }

  // PDF + fragments are also gated server-side to released own exams.
  try {
    pdfUrl.value = await RemoteService.getExamPdfUrl(examId)
  } catch {
    pdfUrl.value = null
  }
  await Promise.all([loadFragments(), refreshRevision()])

  clock = setInterval(() => {
    now.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
  if (clock) clearInterval(clock)
})

async function loadFragments() {
  loadingFragments.value = true
  try {
    fragments.value = await RemoteService.getFragmentsByExam(examId)
  } catch {
    fragments.value = []
  } finally {
    loadingFragments.value = false
  }
}

async function openFragment(item: FragmentDto) {
  selected.value = item
  drawer.value = true
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = null
  }
  if (item.id != null) {
    try {
      imageUrl.value = await RemoteService.getFragmentImageUrl(item.id)
    } catch {
      imageUrl.value = null
    }
  }
}

function closeDrawer() {
  drawer.value = false
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = null
  }
}
</script>