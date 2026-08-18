<template>
  <v-row align="center">
    <v-col>
      <h2 class="text-left ml-1">Listagem de Pessoas</h2>
    </v-col>
    <v-col cols="auto">
      <CreatePersonDialog @person-created="getPeople" />
    </v-col>
  </v-row>


  <v-text-field
    v-model="search"
    label="Search"
    prepend-inner-icon="mdi-magnify"
    variant="outlined"
    hide-details
    single-line
  ></v-text-field>

  <v-data-table
    :headers="headers"
    :items="people"
    :search="search"
    :loading="loading"
    :custom-filter="fuzzySearch"
    item-key="id"
    class="text-left"
    no-data-text="Sem pessoas a apresentar."
  >
    <template v-slot:[`item.type`]="{ item }">
      <v-chip :color="roleColor(item.type)" size="small" variant="tonal">
        {{ roleLabel(item.type) }}
      </v-chip>
    </template>
    <template v-slot:[`item.actions`]="{ item }">
      <v-icon
        v-if="auth.hasPermission('PERSON_IMPERSONATE') && item.id !== auth.user?.id"
        @click="impersonate(item)"
        class="mr-2"
        color="primary"
        title="Personificar"
      >mdi-account-switch</v-icon>
      <v-icon @click="editPerson(item)" class="mr-2" title="Editar">mdi-pencil</v-icon>
      <v-icon @click="deletePerson(item)" color="error" title="Eliminar">mdi-delete</v-icon>
    </template>

  </v-data-table>

</template>

<script setup lang="ts">
import type PeopleDto from '@/models/PersonDto'
import RemoteService from '@/services/RemoteService'
import CreatePersonDialog from './CreatePersonDialog.vue'
import { reactive, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

let search = ref('')
let loading = ref(true)
const headers = [
  { title: 'ID', key: 'id', value: 'id', sortable: true, filterable: false },
  {
    title: 'Nome',
    key: 'name',
    value: 'name',
    sortable: true,
    filterable: true
  },
  {
    title: 'IST ID',
    key: 'istId',
    value: 'istId',
    sortable: true,
    filterable: true
  },
  {
    title: 'Tipo',
    key: 'type',
    value: 'type',
    sortable: true,
    filterable: true
  },
  {
    title: 'Ações',
    key: 'actions',
    value: 'actions',
    sortable: false,
    filterable: false
  }
  // TODO: maybe add another column with possible actions? (edit / delete)
]

const people: PeopleDto[] = reactive([])

// Cores e labels consistentes com os tokens do tema (chips tonal, como o
// resto da app). ADMIN→primary, TEACHER→error, SCHOOL_STAFF→warning, STUDENT→success.
const ROLE_LABELS: Record<string, string> = {
  ADMINISTRATOR: 'Administrador',
  TEACHER: 'Professor',
  SCHOOL_STAFF: 'Funcionário',
  STUDENT: 'Aluno'
}
const ROLE_COLORS: Record<string, string> = {
  ADMINISTRATOR: 'primary',
  TEACHER: 'error',
  SCHOOL_STAFF: 'warning',
  STUDENT: 'success'
}
const roleLabel = (role: string | undefined) => ROLE_LABELS[role ?? ''] ?? (role ?? '')
const roleColor = (role: string | undefined) => ROLE_COLORS[role ?? ''] ?? 'grey'

getPeople()
async function getPeople() {
  people.splice(0, people.length)
  people.push(...(await RemoteService.getPeople()))
  loading.value = false
}

const editPerson = (person: PeopleDto) => {
  // TODO: por implementar (precisa de endpoint de update).
}

const deletePerson = (person: PeopleDto) => {
  // TODO: por implementar (precisa de endpoint de delete).
}

// Admin-only: log in as the selected person, then refresh the list under their
// permissions.
const impersonate = async (person: any) => {
  await auth.impersonate(person.id)
  await getPeople()
}


const fuzzySearch = (value: string, search: string) => {
  // Regex to match any character in between the search characters
  let searchRegex = new RegExp(search.split('').join('.*'), 'i')
  return searchRegex.test(value)
}

</script>
