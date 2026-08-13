<template>
  <v-row align="center">
    <v-col>
      <h2 class="text-left ml-1">Listagem de Escolas</h2>
    </v-col>
    <v-col cols="auto">
      <CreateSchoolDialog @school-created="getSchools" />
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
    :items="schools"
    :search="search"
    :loading="loading"
    :custom-filter="fuzzySearch"
    item-key="id"
    class="text-left"
    no-data-text="Sem escolas a apresentar."
  >
  </v-data-table>
</template>

<script setup lang="ts">
import type SchoolDto from '@/models/SchoolDto'
import RemoteService from '@/services/RemoteService'
import CreateSchoolDialog from './CreateSchoolDialog.vue'
import { reactive, ref } from 'vue'

let search = ref('')
let loading = ref(true)
const headers = [
  { title: 'ID', key: 'id', value: 'id', sortable: true, filterable: false },
  { title: 'Nome', key: 'name', value: 'name', sortable: true, filterable: true },
  { title: 'Código', key: 'code', value: 'code', sortable: true, filterable: true },
  { title: 'Região', key: 'region', value: 'region', sortable: true, filterable: true }
]

const schools: SchoolDto[] = reactive([])

getSchools()
async function getSchools() {
  schools.splice(0, schools.length)
  schools.push(...(await RemoteService.getSchools()))
  loading.value = false
}

const fuzzySearch = (value: string, search: string) => {
  let searchRegex = new RegExp(search.split('').join('.*'), 'i')
  return searchRegex.test(value)
}
</script>