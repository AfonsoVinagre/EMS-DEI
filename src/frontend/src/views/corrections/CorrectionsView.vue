<template>
  <h2 class="text-left ml-1">Correcções atribuídas</h2>
  <p class="text-left text-medium-emphasis ml-1 mb-4">
    Lista dos fragmentos que foram distribuídos para si. Clique para ver a imagem
    e submeter a nota quando terminar a correcção.
  </p>

  <v-data-table
    :headers="headers"
    :items="myFragments"
    :loading="loading"
    item-key="id"
    class="text-left"
    no-data-text="Não tem fragmentos atribuídos para corrigir."
    @click:row="onRow"
  >
    <template v-slot:[`item.status`]="{ item }">
      <v-chip
        :color="statusColor(item.status)"
        :text="statusLabel(item.status)"
        size="small"
        variant="tonal"
      ></v-chip>
    </template>
    <template v-slot:[`item.actions`]="{ item }">
      <v-btn
        icon="mdi-image-multiple"
        size="small"
        :to="`/exams/${item.examId}/fragments`"
        title="Abrir fragmento"
      ></v-btn>
    </template>
  </v-data-table>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type FragmentDto from '@/models/FragmentDto'
import RemoteService from '@/services/RemoteService'

const router = useRouter()

const fragments = ref<FragmentDto[]>([])
const loading = ref(true)

const headers = [
  { title: 'ID', key: 'id', value: 'id', sortable: true },
  { title: 'Exame', key: 'examId', value: 'examId', sortable: true },
  { title: 'Página', key: 'pageNumber', value: 'pageNumber', sortable: true },
  { title: 'Disciplina', key: 'subjectName', value: 'subjectName', sortable: true },
  { title: 'Estado', key: 'status', value: 'status', sortable: true },
  { title: 'Ações', key: 'actions', value: 'actions', sortable: false }
]

// For teachers, /fragments is server-side filtered to the fragments where
// they have at least one CorrectionTask. Each fragment already carries a
// computed @Transient status (UNASSIGNED / IN_CORRECTION / IN_REVISION /
// CORRECTED / REVISION_SUBMITTED), so we don't need to call /correction-tasks.
const myFragments = computed(() => fragments.value)

function statusLabel(status: string | undefined): string {
  switch (status) {
    case 'UNASSIGNED': return 'Pendente'
    case 'IN_CORRECTION': return 'Em correção'
    case 'IN_REVISION': return 'Em revisão'
    case 'CORRECTED': return 'Submetido'
    case 'REVISION_SUBMITTED': return 'Revisão submetida'
    default: return status ?? ''
  }
}

function statusColor(status: string | undefined): string {
  switch (status) {
    case 'CORRECTED':
    case 'REVISION_SUBMITTED':
      return 'success'
    case 'IN_CORRECTION':
    case 'IN_REVISION':
      return 'warning'
    default:
      return 'grey'
  }
}

function onRow(_: Event, row: { item: FragmentDto }) {
  if (row.item.examId != null) {
    router.push(`/exams/${row.item.examId}/fragments`)
  }
}

onMounted(async () => {
  try {
    fragments.value = await RemoteService.getFragments()
  } finally {
    loading.value = false
  }
})
</script>
