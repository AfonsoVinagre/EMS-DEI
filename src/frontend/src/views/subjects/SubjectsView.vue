<template>
  <v-row align="center">
    <v-col>
      <h2 class="text-left ml-1">Listagem de Disciplinas</h2>
    </v-col>
    <v-col cols="auto">
      <CreateSubjectDialog @subject-created="getSubjects" />
    </v-col>
  </v-row>

  <v-text-field
    v-model="search"
    label="Pesquisar"
    prepend-inner-icon="mdi-magnify"
    variant="outlined"
    hide-details
    single-line
  ></v-text-field>

  <v-data-table
    :headers="headers"
    :items="subjects"
    :search="search"
    :loading="loading"
    :custom-filter="fuzzySearch"
    item-key="id"
    class="text-left"
    no-data-text="Sem disciplinas a apresentar."
  >
  </v-data-table>
</template>

<script setup lang="ts">
import type SubjectDto from '@/models/SubjectDto'
import RemoteService from '@/services/RemoteService'
import CreateSubjectDialog from './CreateSubjectDialog.vue'
import { reactive, ref } from 'vue'

let search = ref('')
let loading = ref(true)
const headers = [
  { title: 'ID', key: 'id', value: 'id', sortable: true, filterable: false },
  { title: 'Nome', key: 'name', value: 'name', sortable: true, filterable: true },
  { title: 'Código', key: 'code', value: 'code', sortable: true, filterable: true }
]

const subjects: SubjectDto[] = reactive([])

getSubjects()
async function getSubjects() {
  subjects.splice(0, subjects.length)
  subjects.push(...(await RemoteService.getSubjects()))
  loading.value = false
}

const fuzzySearch = (value: string, search: string) => {
  let searchRegex = new RegExp(search.split('').join('.*'), 'i')
  return searchRegex.test(value)
}
</script>