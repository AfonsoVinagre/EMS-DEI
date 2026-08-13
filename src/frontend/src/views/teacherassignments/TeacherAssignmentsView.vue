<template>
  <h2 class="text-left ml-1">Associações Professor / Escola / Disciplina</h2>

  <v-row>
    <v-col cols="12" md="6">
      <v-select
        v-model="newTeacherId"
        :items="teachers"
        :loading="loadingTeachers"
        item-title="name"
        item-value="id"
        label="Professor"
        clearable
      ></v-select>
    </v-col>
    <v-col cols="12" md="6">
      <v-select
        v-model="newSchoolId"
        :items="schools"
        :loading="loadingSchools"
        item-title="name"
        item-value="id"
        label="Escola"
        clearable
      ></v-select>
    </v-col>
    <v-col cols="12" md="6">
      <v-select
        v-model="newSubjectId"
        :items="subjects"
        :loading="loadingSubjects"
        item-title="name"
        item-value="id"
        label="Disciplina"
        clearable
      ></v-select>
    </v-col>
    <v-col cols="auto" class="d-flex align-center">
      <v-btn
        color="primary"
        prepend-icon="mdi-plus"
        text="Criar nova"
        :disabled="!canCreate"
        @click="createAssignment"
      ></v-btn>
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
    :items="filteredAssignments"
    :search="search"
    :loading="loadingAssignments"
    :custom-filter="fuzzySearch"
    item-key="id"
    class="text-left"
    no-data-text="Sem associações a apresentar."
  >
    <template v-slot:[`item.actions`]="{ item }">
      <v-icon @click="deleteAssignment(item)" color="primary" title="Remover">
        mdi-delete
      </v-icon>
    </template>
  </v-data-table>
</template>

<script setup lang="ts">
import type PersonDto from '@/models/PersonDto'
import type SchoolDto from '@/models/SchoolDto'
import type SubjectDto from '@/models/SubjectDto'
import type TeacherAssignmentDto from '@/models/TeacherAssignmentDto'
import RemoteService from '@/services/RemoteService'
import { computed, ref, watch } from 'vue'

const teachers = ref<PersonDto[]>([])
const schools = ref<SchoolDto[]>([])
const subjects = ref<SubjectDto[]>([])
const assignments = ref<TeacherAssignmentDto[]>([])

const loadingTeachers = ref(true)
const loadingSchools = ref(true)
const loadingSubjects = ref(true)
const loadingAssignments = ref(false)

const search = ref('')

const filterTeacherId = ref<number | undefined>(undefined)
const filterSchoolId = ref<number | undefined>(undefined)
const filterSubjectId = ref<number | undefined>(undefined)

const newTeacherId = ref<number | undefined>(undefined)
const newSchoolId = ref<number | undefined>(undefined)
const newSubjectId = ref<number | undefined>(undefined)

const headers = [
  { title: 'ID', key: 'id', value: 'id', sortable: true, filterable: false },
  { title: 'Professor', key: 'teacherName', value: 'teacherName', sortable: true, filterable: true },
  { title: 'Escola', key: 'schoolName', value: 'schoolName', sortable: true, filterable: true },
  { title: 'Disciplina', key: 'subjectName', value: 'subjectName', sortable: true, filterable: true },
  { title: 'Ações', key: 'actions', value: 'actions', sortable: false, filterable: false }
]

const canCreate = computed(
  () => !!newTeacherId.value && !!newSchoolId.value && !!newSubjectId.value
)

const filteredAssignments = computed(() =>
  assignments.value.filter((a) => {
    if (filterTeacherId.value != null && a.teacherId !== filterTeacherId.value) return false
    if (filterSchoolId.value != null && a.schoolId !== filterSchoolId.value) return false
    if (filterSubjectId.value != null && a.subjectId !== filterSubjectId.value) return false
    return true
  })
)

;(async () => {
  try {
    const people = await RemoteService.getPeople()
    teachers.value = people.filter((p) => p.type === 'TEACHER')
  } finally {
    loadingTeachers.value = false
  }
})()
;(async () => {
  try {
    schools.value = await RemoteService.getSchools()
  } finally {
    loadingSchools.value = false
  }
})()
;(async () => {
  try {
    subjects.value = await RemoteService.getSubjects()
  } finally {
    loadingSubjects.value = false
  }
})()

async function refreshAssignments() {
  loadingAssignments.value = true
  try {
    assignments.value = await RemoteService.getTeacherAssignments()
  } finally {
    loadingAssignments.value = false
  }
}

refreshAssignments()

watch([search, filterTeacherId, filterSchoolId, filterSubjectId], () => { /* triggers filteredAssignments */ })

async function createAssignment() {
  if (!canCreate.value) return
  await RemoteService.createTeacherAssignment({
    teacherId: newTeacherId.value!,
    schoolId: newSchoolId.value!,
    subjectId: newSubjectId.value!
  })
  newSchoolId.value = undefined
  newSubjectId.value = undefined
  await refreshAssignments()
}

async function deleteAssignment(item: TeacherAssignmentDto) {
  if (item.id == null) return
  await RemoteService.deleteTeacherAssignment(item.id)
  await refreshAssignments()
}

const fuzzySearch = (value: string, search: string) => {
  let searchRegex = new RegExp(search.split('').join('.*'), 'i')
  return searchRegex.test(value)
}
</script>