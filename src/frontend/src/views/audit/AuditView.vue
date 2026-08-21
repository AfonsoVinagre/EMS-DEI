<template>
  <v-row align="center" class="mb-2">
    <v-col>
      <h2 class="text-left ml-1">Auditoria</h2>
      <p class="text-left text-medium-emphasis mb-0">
        Registo inviolável das ações críticas (notas, release, revisão, downloads).
        Cada entrada está ligada à anterior por uma hash-chain — qualquer
        adulteração na base de dados é detetável.
      </p>
    </v-col>
    <v-col cols="auto" class="d-flex align-center ga-2">
      <v-btn
        color="primary"
        variant="tonal"
        prepend-icon="mdi-shield-check-outline"
        :loading="verifying"
        text="Verificar integridade"
        @click="verifyIntegrity"
      ></v-btn>
      <v-btn
        variant="tonal"
        prepend-icon="mdi-refresh"
        :loading="loading"
        text="Atualizar"
        @click="loadLogs"
      ></v-btn>
    </v-col>
  </v-row>

  <!-- Integrity status chip -->
  <div v-if="integrity" class="d-flex align-center mb-3">
    <v-chip
      :color="integrity.intact ? 'success' : 'error'"
      variant="tonal"
      size="small"
      :prepend-icon="integrity.intact ? 'mdi-check-decagram' : 'mdi-alert-decagram'"
      :text="integrity.message"
    ></v-chip>
  </div>

  <!-- Filter -->
  <v-text-field
    v-model="search"
    density="compact"
    variant="outlined"
    prepend-inner-icon="mdi-magnify"
    label="Filtrar (ator, ação, descrição, alvo...)"
    single-line
    hide-details
    class="mb-2"
    clearable
  ></v-text-field>

  <v-data-table
    :headers="headers"
    :items="filteredLogs"
    :loading="loading"
    item-key="id"
    :items-per-page="20"
    class="text-left"
    no-data-text="Sem entradas de auditoria."
  >
    <template v-slot:[`item.occurredAt`]="{ item }">
      {{ formatTime(item.occurredAt) }}
    </template>
    <template v-slot:[`item.actor`]="{ item }">
      <div>{{ item.actorName ?? '—' }}</div>
      <div class="text-caption text-medium-emphasis">{{ item.actorEmail ?? '' }}</div>
    </template>
    <template v-slot:[`item.actorType`]="{ item }">
      <v-chip :text="roleLabel(item.actorType)" size="x-small" variant="tonal"></v-chip>
    </template>
    <template v-slot:[`item.action`]="{ item }">
      <v-chip
        :color="actionColor(item.action)"
        size="small"
        variant="tonal"
        :prepend-icon="actionIcon(item.action)"
        :text="actionLabel(item.action)"
      ></v-chip>
    </template>
    <template v-slot:[`item.target`]="{ item }">
      <span v-if="item.targetType">
        {{ item.targetType }}<span v-if="item.targetId != null"> #{{ item.targetId }}</span>
      </span>
      <span v-else class="text-medium-emphasis">—</span>
    </template>
    <template v-slot:[`item.delta`]="{ item }">
      <span v-if="item.prevValue != null || item.newValue != null">
        <span class="text-medium-emphasis">{{ item.prevValue ?? '—' }}</span>
        <v-icon size="x-small" class="mx-1">mdi-arrow-right</v-icon>
        <strong>{{ item.newValue ?? '—' }}</strong>
      </span>
      <span v-else class="text-medium-emphasis">—</span>
    </template>
  </v-data-table>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import type AuditLogDto from '@/models/AuditLogDto'
import type IntegrityReportDto from '@/models/IntegrityReportDto'
import RemoteService from '@/services/RemoteService'

const logs = ref<AuditLogDto[]>([])
const loading = ref(false)
const search = ref('')
const integrity = ref<IntegrityReportDto | null>(null)
const verifying = ref(false)

const headers = [
  { title: 'Data/hora', key: 'occurredAt', sortable: true },
  { title: 'Ator', key: 'actor', sortable: true },
  { title: 'Papel', key: 'actorType', sortable: true },
  { title: 'Ação', key: 'action', sortable: true },
  { title: 'Alvo', key: 'target', sortable: false },
  { title: 'Descrição', key: 'description', sortable: false },
  { title: 'Antes → Depois', key: 'delta', sortable: false }
]

const filteredLogs = computed(() => {
  const q = (search.value ?? '').trim().toLowerCase()
  if (!q) return logs.value
  return logs.value.filter((l) => {
    const hay = [
      l.actorName,
      l.actorEmail,
      l.actorType,
      l.action,
      l.targetType,
      l.targetId != null ? String(l.targetId) : '',
      l.description,
      l.prevValue,
      l.newValue
    ]
      .filter(Boolean)
      .join(' ')
      .toLowerCase()
    return hay.includes(q)
  })
})

function formatTime(iso?: string): string {
  return iso ? new Date(iso).toLocaleString('pt-PT') : '—'
}

const ACTION_META: Record<string, { label: string; icon: string; color: string }> = {
  EXAM_UPLOAD: { label: 'Upload', icon: 'mdi-upload', color: 'primary' },
  EXAM_DISTRIBUTE: { label: 'Distribuição', icon: 'mdi-share-all-outline', color: 'info' },
  EXAM_RELEASE: { label: 'Disponibilização', icon: 'mdi-eye-check-outline', color: 'success' },
  GRADE_SUBMIT: { label: 'Nota', icon: 'mdi-pencil-outline', color: 'warning' },
  REVISION_REQUEST: { label: 'Pedido de revisão', icon: 'mdi-file-edit-outline', color: 'info' },
  REVISION_PROCESS: { label: 'Revisão (sistema)', icon: 'mdi-cog-outline', color: 'secondary' },
  CONSULTATION_REQUEST: { label: 'Pedido de consulta', icon: 'mdi-hand-extended-outline', color: 'info' },
  CONSULTATION_APPROVE: { label: 'Consulta aprovada', icon: 'mdi-check-circle-outline', color: 'success' },
  CONSULTATION_REJECT: { label: 'Consulta rejeitada', icon: 'mdi-close-circle-outline', color: 'error' },
  CONSULTATION_AUTO_APPROVE: { label: 'Consulta auto-aprovada', icon: 'mdi-check-all', color: 'success' },
  PDF_ACCESS: { label: 'Acesso ao PDF', icon: 'mdi-download-outline', color: 'primary' }
}

function actionLabel(a?: string): string {
  return (a && ACTION_META[a]?.label) || a || '—'
}
function actionIcon(a?: string): string {
  return (a && ACTION_META[a]?.icon) || 'mdi-circle-outline'
}
function actionColor(a?: string): string {
  return (a && ACTION_META[a]?.color) || 'default'
}

const ROLE_LABELS: Record<string, string> = {
  ADMINISTRATOR: 'Administrador',
  SCHOOL_STAFF: 'Funcionário',
  TEACHER: 'Professor',
  STUDENT: 'Aluno',
  SYSTEM: 'Sistema'
}
function roleLabel(r?: string | null): string {
  return (r && ROLE_LABELS[r]) || r || '—'
}

async function loadLogs() {
  loading.value = true
  try {
    logs.value = await RemoteService.getAuditLogs()
  } catch {
    logs.value = []
  } finally {
    loading.value = false
  }
}

async function verifyIntegrity() {
  verifying.value = true
  try {
    integrity.value = await RemoteService.getAuditIntegrity()
  } catch {
    integrity.value = null
  } finally {
    verifying.value = false
  }
}

onMounted(loadLogs)
</script>