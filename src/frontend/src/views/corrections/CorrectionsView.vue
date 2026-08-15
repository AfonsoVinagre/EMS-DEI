<template>
  <h2 class="text-left ml-1">Correcções atribuídas</h2>
  <p class="text-left text-medium-emphasis ml-1 mb-4">
    Lista dos fragmentos que foram distribuídos para si. Clique para ver a imagem
    e submeter a nota quando terminar a correcção.
  </p>

  <v-data-table
    :headers="headers"
    :items="fragments"
    :loading="loading"
    item-key="id"
    class="text-left"
    no-data-text="Não tem perguntas atribuídas para corrigir."
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
  </v-data-table>

  <!-- The fragment dialog is the entire correction UI for a teacher:
       just the image, the grade field and the submit button. No list of
       other fragments, no reassign, no extras. -->
  <v-dialog v-model="drawer" max-width="900">
    <v-card v-if="selected">
      <v-card-title class="d-flex align-center">
        <span>Fragmento #{{ selected.id }} — página {{ selected.pageNumber }}</span>
        <v-spacer></v-spacer>
        <v-chip
          :color="statusColor(selected.status)"
          :text="statusLabel(selected.status)"
          size="small"
          variant="tonal"
        ></v-chip>
      </v-card-title>
      <v-card-text>
        <v-img
          v-if="imageUrl"
          :src="imageUrl"
          max-height="650"
          contain
        ></v-img>
        <v-progress-circular v-else indeterminate color="primary"></v-progress-circular>

        <v-alert
          v-if="openTaskError"
          type="error"
          variant="tonal"
          class="mt-3"
        >
          {{ openTaskError }}
        </v-alert>

        <v-divider class="my-4"></v-divider>

        <div v-if="existingTask" class="text-left">
          <p class="text-medium-emphasis mb-2">
            <strong v-if="existingTask.status === 'SUBMITTED'">
              Já atribuiu a nota
              <span class="text-success">{{ existingTask.grade }}</span>.
            </strong>
            <strong v-else>
              Atribua a nota para esta pergunta.
            </strong>
          </p>
          <v-row align="center">
            <v-col cols="12" sm="6" md="4">
              <v-text-field
                v-model.number="gradeInput"
                type="number"
                min="0"
                label="Nota"
                :disabled="existingTask.status === 'SUBMITTED'"
                :rules="[(v: number) => (v == null || v < 0) ? 'A nota não pode ser negativa' : true]"
              ></v-text-field>
            </v-col>
            <v-col cols="auto">
              <v-btn
                color="primary"
                prepend-icon="mdi-check"
                :loading="submitting"
                :disabled="!canSubmit"
                :text="existingTask.status === 'SUBMITTED' ? 'Submetido' : 'Avaliar'"
                @click="submitGrade"
              ></v-btn>
            </v-col>
          </v-row>
        </div>
      </v-card-text>
      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn text="Fechar" @click="closeDrawer"></v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type FragmentDto from '@/models/FragmentDto'
import type CorrectionTaskDto from '@/models/CorrectionTaskDto'
import RemoteService from '@/services/RemoteService'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const fragments = ref<FragmentDto[]>([])
const loading = ref(true)

const selected = ref<FragmentDto | null>(null)
const drawer = ref(false)
const imageUrl = ref<string | null>(null)
const existingTask = ref<CorrectionTaskDto | null>(null)
const openTaskError = ref<string | null>(null)
const gradeInput = ref<number | null>(null)
const submitting = ref(false)

const headers = [
  { title: 'ID', key: 'id', value: 'id', sortable: true },
  { title: 'Exame', key: 'examId', value: 'examId', sortable: true },
  { title: 'Página', key: 'pageNumber', value: 'pageNumber', sortable: true },
  { title: 'Disciplina', key: 'subjectName', value: 'subjectName', sortable: true },
  { title: 'Estado', key: 'status', value: 'status', sortable: true }
]

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

const canSubmit = computed(() =>
  gradeInput.value != null && gradeInput.value >= 0
)

async function onRow(_: Event, row: { item: FragmentDto }) {
  await openFragment(row.item)
}

async function openFragment(f: FragmentDto) {
  selected.value = f
  drawer.value = true
  existingTask.value = null
  openTaskError.value = null
  gradeInput.value = null
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = null
  }
  if (f.id == null) return

  try {
    imageUrl.value = await RemoteService.getFragmentImageUrl(f.id)
  } catch {
    imageUrl.value = null
  }

  // The teacher owns at most one active PRIMARY task on this fragment.
  // We pick the most recent one belonging to the teacher.
  try {
    const tasks = await RemoteService.getFragmentTasks(f.id)
    const mine = tasks
      .filter((t) => t.teacherId === auth.user?.id)
      .sort((a, b) => (b.assignedAt ?? '').localeCompare(a.assignedAt ?? ''))
    existingTask.value = mine[0] ?? null
    gradeInput.value = mine[0]?.grade ?? null
    if (!existingTask.value) {
      openTaskError.value = 'Não tem tarefas atribuídas para este fragmento.'
    }
  } catch (err: any) {
    openTaskError.value = err?.message ?? 'Não foi possível carregar a tarefa.'
  }
}

function closeDrawer() {
  drawer.value = false
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = null
  }
}

async function submitGrade() {
  if (!existingTask.value || existingTask.value.id == null) return
  if (gradeInput.value == null || gradeInput.value < 0) return
  submitting.value = true
  try {
    const updated = await RemoteService.submitGrade(
      existingTask.value.id,
      gradeInput.value
    )
    existingTask.value = updated
    // Reflect the new status in the table without a full reload.
    if (selected.value) {
      const idx = fragments.value.findIndex((x) => x.id === selected.value!.id)
      if (idx >= 0) {
        // @Transient — recompute locally from the updated task.
        fragments.value[idx] = {
          ...fragments.value[idx],
          status: updated.status === 'SUBMITTED' ? 'CORRECTED' : 'IN_CORRECTION'
        }
      }
    }
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  try {
    fragments.value = await RemoteService.getFragments()
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
})
</script>
