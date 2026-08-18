<template>
  <v-layout id="app">
    <TopBar v-if="authStore.isAuthenticated" />
    <v-main
      :style="{ marginTop: authStore.isAuthenticated ? '96px' : '0' }"
      class="d-flex flex-column flex-grow-1 flex-shrink-1"
    >
      <v-container fluid class="pa-4 pa-sm-6 mx-auto" style="max-width: 1280px;">
        <ErrorMessage />
        <LoadingOverlay />

        <RouterView />
      </v-container>
    </v-main>
  </v-layout>
</template>

<script setup lang="ts">
import TopBar from '@/components/TopBar.vue'
import ErrorMessage from '@/components/ErrorMessage.vue';
import LoadingOverlay from '@/components/LoadingOverlay.vue';


import { RouterLink, RouterView } from 'vue-router'

import { useAppearanceStore } from './stores/appearance';
import { useAuthStore } from './stores/auth';
import { onMounted, onUnmounted } from 'vue';

const appearanceStore = useAppearanceStore();
const authStore = useAuthStore();

const setWidth = () => (appearanceStore.windowWidth = window.innerWidth);
setWidth();
onMounted(async () => {
  window.addEventListener('resize', setWidth);
  appearanceStore.clearErrors();

  // Refresh identity/permissions if a persisted token exists.
  if (authStore.isAuthenticated) {
    try {
      await authStore.fetchMe();
    } catch {
      authStore.logout();
    }
  }
});
onUnmounted(() => {
  window.removeEventListener('resize', setWidth);
});
</script>

<style scoped>
#app {
  display: flex;
  flex-direction: column;
}
</style>
