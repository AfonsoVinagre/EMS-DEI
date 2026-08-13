<template>
  <v-dialog v-model="dialog" max-width="500">
    <template v-slot:activator="{ props: activatorProps }">
      <v-btn
        class="text-none font-weight-regular"
        prepend-icon="mdi-plus"
        text="Adicionar Escola"
        v-bind="activatorProps"
        color="primary"
      ></v-btn>
    </template>

    <v-card prepend-icon="mdi-school" title="Nova Escola">
      <v-card-text>
        <v-text-field label="Nome*" required v-model="newSchool.name"></v-text-field>
        <v-text-field label="Código*" required v-model="newSchool.code"></v-text-field>
        <v-text-field label="Região*" required v-model="newSchool.region"></v-text-field>
      </v-card-text>

      <v-divider></v-divider>

      <v-card-actions>
        <v-spacer></v-spacer>
        <v-btn text="Fechar" variant="plain" @click="dialog = false"></v-btn>
        <v-btn
          color="primary"
          text="Guardar"
          variant="tonal"
          @click="dialog = false, saveSchool()"
        ></v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type SchoolDto from '@/models/SchoolDto'
import RemoteService from '@/services/RemoteService'

const dialog = ref(false)
const emit = defineEmits(['school-created'])

const newSchool = ref<SchoolDto>({
  name: '',
  code: '',
  region: ''
})

const saveSchool = async () => {
  await RemoteService.createSchool(newSchool.value)
  newSchool.value = { name: '', code: '', region: '' }
  emit('school-created')
}
</script>