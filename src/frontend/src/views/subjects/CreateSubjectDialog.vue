<template>
  <v-dialog v-model="dialog" max-width="400">
    <template v-slot:activator="{ props: activatorProps }">
      <v-btn
        class="text-none font-weight-regular"
        prepend-icon="mdi-plus"
        text="Adicionar Disciplina"
        v-bind="activatorProps"
        color="primary"
      ></v-btn>
    </template>

    <v-card prepend-icon="mdi-book-open-variant" title="Nova Disciplina">
      <v-card-text>
        <v-text-field label="Nome*" required v-model="newSubject.name"></v-text-field>
        <v-text-field label="Código*" required v-model="newSubject.code"></v-text-field>
      </v-card-text>

      <v-divider></v-divider>

      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn text="Fechar" variant="plain" @click="dialog = false"></v-btn>
        <v-btn
          color="primary"
          text="Guardar"
          variant="tonal"
          @click="dialog = false, saveSubject()"
        ></v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type SubjectDto from '@/models/SubjectDto'
import RemoteService from '@/services/RemoteService'

const dialog = ref(false)
const emit = defineEmits(['subject-created'])

const newSubject = ref<SubjectDto>({
  name: '',
  code: ''
})

const saveSubject = async () => {
  await RemoteService.createSubject(newSubject.value)
  newSubject.value = { name: '', code: '' }
  emit('subject-created')
}
</script>