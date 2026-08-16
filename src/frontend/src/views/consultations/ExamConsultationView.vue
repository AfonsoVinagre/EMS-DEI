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

  <v-row v-if="exam && !loadError" class="mt-2">
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
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import type ExamDto from '@/models/ExamDto'
import type FragmentDto from '@/models/FragmentDto'
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

const headers = [
  { title: 'Página', key: 'pageNumber', value: 'pageNumber', sortable: true },
  { title: 'Nota', key: 'grade', value: 'grade', sortable: true },
  { title: 'Ver', key: 'actions', value: 'actions', sortable: false }
]

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
  await loadFragments()
})

onBeforeUnmount(() => {
  if (pdfUrl.value) URL.revokeObjectURL(pdfUrl.value)
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
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