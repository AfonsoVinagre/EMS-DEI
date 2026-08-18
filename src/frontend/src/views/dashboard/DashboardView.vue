<template>
  <v-row align="center" class="mb-2">
    <v-col>
      <h2 class="text-left ml-1">Dashboard</h2>
      <p class="text-left text-medium-emphasis mb-0">
        Métricas globais do sistema (atualizadas
        {{ lastRefresh ? new Date(lastRefresh).toLocaleTimeString('pt-PT') : '—' }}).
      </p>
    </v-col>
    <v-col cols="auto" class="d-flex align-center ga-2">
      <v-switch
        v-model="autoRefresh"
        color="primary"
        hide-details
        density="compact"
        label="Auto"
        :disabled="loading"
      ></v-switch>
      <v-btn
        color="primary"
        variant="tonal"
        prepend-icon="mdi-refresh"
        :loading="loading"
        text="Atualizar"
        @click="refresh"
      ></v-btn>
    </v-col>
  </v-row>

  <!-- KPI tiles -->
  <v-row class="mb-2">
    <v-col v-for="kpi in kpiTiles" :key="kpi.label" cols="6" sm="4" md="2">
      <v-card variant="outlined" class="text-center pa-3 h-100">
        <v-icon :icon="kpi.icon" :color="kpi.color" class="mb-1"></v-icon>
        <div class="text-h5 font-weight-bold">{{ kpi.value }}</div>
        <div class="text-caption text-medium-emphasis">{{ kpi.label }}</div>
      </v-card>
    </v-col>
  </v-row>

  <v-alert
    v-if="!loading && !exams.length"
    type="info"
    variant="tonal"
    class="text-left mb-4"
    text="Ainda não há exames no sistema. As métricas aparecem assim que houver dados."
  ></v-alert>

  <!-- Charts grid -->
  <v-row>
    <v-col cols="12" md="6">
      <v-card variant="outlined" class="text-left pa-4 h-100">
        <v-card-title class="text-subtitle-1 px-0">
          <v-icon icon="mdi-chart-bar" class="mr-2" />
          Média por disciplina
        </v-card-title>
        <v-card-text class="px-0">
          <BarChart
            :labels="avgLabels"
            :data="avgData"
            :horizontal="true"
            unit=" val"
            no-data-text="Sem exames corrigidos (com nota final)."
          />
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" md="6">
      <v-card variant="outlined" class="text-left pa-4 h-100">
        <v-card-title class="text-subtitle-1 px-0">
          <v-icon icon="mdi-chart-donut" class="mr-2" />
          Progresso global da correção
        </v-card-title>
        <v-card-text class="px-0">
          <DoughnutChart
            :labels="distLabels"
            :data="distData"
            :colors="distColors"
            no-data-text="Sem exames."
          />
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" md="6">
      <v-card variant="outlined" class="text-left pa-4 h-100">
        <v-card-title class="text-subtitle-1 px-0 d-flex align-center">
          <v-icon icon="mdi-file-edit-outline" class="mr-2" />
          Volume de pedidos de revisão
          <v-chip v-if="revVol.openWindows > 0" size="x-small" color="warning" variant="tonal" class="ml-2">
            {{ revVol.openWindows }} janela(s) aberta(s)
          </v-chip>
        </v-card-title>
        <v-card-text class="px-0">
          <BarChart
            :labels="revLabels"
            :data="revData"
            no-data-text="Sem pedidos de revisão."
          />
        </v-card-text>
      </v-card>
    </v-col>

    <v-col cols="12" md="6">
      <v-card variant="outlined" class="text-left pa-4 h-100">
        <v-card-title class="text-subtitle-1 px-0">
          <v-icon icon="mdi-account-tie" class="mr-2" />
          Carga por professor (submetidas / atribuídas)
        </v-card-title>
        <v-card-text class="px-0">
          <BarChart
            :labels="loadLabels"
            :data="loadData"
            :horizontal="true"
            no-data-text="Sem tarefas de correção atribuídas."
          />
        </v-card-text>
      </v-card>
    </v-col>
  </v-row>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type ExamDto from '@/models/ExamDto'
import type RevisionRequestDto from '@/models/RevisionRequestDto'
import type CorrectionTaskDto from '@/models/CorrectionTaskDto'
import type PersonDto from '@/models/PersonDto'
import RemoteService from '@/services/RemoteService'
import BarChart from '@/components/charts/BarChart.vue'
import DoughnutChart from '@/components/charts/DoughnutChart.vue'
import { examStatusLabel } from '@/utils/labels'
import {
  subjectAverages,
  statusDistribution,
  revisionVolume,
  teacherLoad,
  computeKpis
} from '@/utils/dashboard'

const exams = ref<ExamDto[]>([])
const revisionRequests = ref<RevisionRequestDto[]>([])
const tasks = ref<CorrectionTaskDto[]>([])
const people = ref<PersonDto[]>([])

const loading = ref(true)
const lastRefresh = ref<number | null>(null)
const autoRefresh = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

// --- agregações (computeds a partir dos refs) -----------------------

const averages = computed(() => subjectAverages(exams.value))
const avgLabels = computed(() => averages.value.map((a) => a.subject))
const avgData = computed(() => averages.value.map((a) => Math.round(a.mean * 10) / 10))

const distribution = computed(() => statusDistribution(exams.value, examStatusLabel))
const distLabels = computed(() => distribution.value.map((d) => d.label))
const distData = computed(() => distribution.value.map((d) => d.count))
const distColors = computed(() => distribution.value.map((d) => d.color))

const revVol = computed(() => revisionVolume(revisionRequests.value, exams.value))
const revLabels = computed(() => revVol.value.perSubject.map((r) => r.subject))
const revData = computed(() => revVol.value.perSubject.map((r) => r.count))

const load = computed(() => teacherLoad(tasks.value))
// "submetidas / atribuídas" no label do eixo via tooltip; o gráfico mostra
// só as submetidas (trabalho concluído) — magnitude única.
const loadLabels = computed(() => load.value.map((t) => t.teacher))
const loadData = computed(() => load.value.map((t) => t.submitted))

const kpis = computed(() => computeKpis(exams.value, revisionRequests.value, people.value))

const kpiTiles = computed(() => [
  { label: 'Exames (total)', value: kpis.value.totalExams, icon: 'mdi-file-multiple', color: 'primary' },
  { label: 'Corrigidos', value: kpis.value.corrected, icon: 'mdi-check-circle-outline', color: 'warning' },
  { label: 'Disponíveis', value: kpis.value.released, icon: 'mdi-eye-check-outline', color: 'success' },
  { label: 'Pedidos de revisão', value: kpis.value.revisionRequests, icon: 'mdi-file-edit-outline', color: 'info' },
  { label: 'Professores', value: kpis.value.teachers, icon: 'mdi-account-tie', color: 'error' },
  { label: 'Alunos', value: kpis.value.students, icon: 'mdi-account-multiple', color: 'primary' }
])

// --- refresh -------------------------------------------------------

async function refresh() {
  loading.value = true
  try {
    // allSettled (em vez de Promise.all) para que a falha de um endpoint não
    // faça zerar todos os gráficos: cada fonte é atribuída independentemente.
    const results = await Promise.allSettled([
      RemoteService.getExams(),
      RemoteService.getRevisionRequests(),
      RemoteService.getCorrectionTasks(),
      RemoteService.getPeople()
    ])
    if (results[0].status === 'fulfilled') exams.value = results[0].value
    if (results[1].status === 'fulfilled') revisionRequests.value = results[1].value
    if (results[2].status === 'fulfilled') tasks.value = results[2].value
    if (results[3].status === 'fulfilled') people.value = results[3].value
    lastRefresh.value = Date.now()
  } finally {
    loading.value = false
  }
}

function startTimer() {
  stopTimer()
  timer = setInterval(refresh, 30000)
}
function stopTimer() {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

watch(autoRefresh, (on) => {
  if (on) startTimer()
  else stopTimer()
})

onMounted(refresh)
onBeforeUnmount(stopTimer)
</script>