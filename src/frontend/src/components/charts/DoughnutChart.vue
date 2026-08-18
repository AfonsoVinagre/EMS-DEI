<template>
  <div class="doughnut-wrap">
    <canvas ref="canvasEl"></canvas>
    <div v-if="!data.length || total === 0" class="text-center text-medium-emphasis pa-4">
      {{ noDataText }}
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Doughnut chart (parts-of-whole / estado) driven directly by Chart.js. As
 * fatias usam cores de STATUS (reservadas) — vão sempre com legenda (labels),
 * nunca só-cor. Eixos n/d (doughnut não tem eixos); texto/legenda em tinta.
 */
import { onMounted, onBeforeUnmount, ref, watch, computed } from 'vue'
import { useTheme } from 'vuetify'
import Chart from 'chart.js/auto'
import type { ChartConfiguration } from 'chart.js'

const props = withDefaults(
  defineProps<{
    labels: string[]
    data: number[]
    colors: string[]
    noDataText?: string
  }>(),
  {
    noDataText: 'Sem dados.'
  }
)

const theme = useTheme()
const canvasEl = ref<HTMLCanvasElement | null>(null)
let chart: Chart<'doughnut'> | null = null

const total = computed(() => props.data.reduce((a, b) => a + b, 0))

function inkColor(): string {
  return theme.current.value.dark ? 'rgba(255,255,255,0.87)' : 'rgba(0,0,0,0.87)'
}
function mutedColor(): string {
  return theme.current.value.dark ? 'rgba(255,255,255,0.6)' : 'rgba(0,0,0,0.6)'
}

function buildConfig(): ChartConfiguration<'doughnut'> {
  return {
    type: 'doughnut',
    data: {
      labels: props.labels,
      datasets: [
        {
          data: props.data,
          backgroundColor: props.colors,
          hoverBackgroundColor: props.colors,
          borderWidth: 2,
          // 2px surface ring entre fatias (gap do skill dataviz).
          borderColor: theme.current.value.dark ? '#1e1e1e' : '#ffffff'
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '60%',
      color: inkColor(),
      plugins: {
        // Legenda obrigatória: status nunca é só-cor.
        legend: {
          position: 'bottom',
          labels: { color: mutedColor(), usePointStyle: true, boxWidth: 8 }
        },
        tooltip: {
          callbacks: {
            label: (ctx) => {
              const v = ctx.parsed as number
              const pct = total.value > 0 ? Math.round((v / total.value) * 100) : 0
              return `${ctx.label}: ${v} (${pct}%)`
            }
          }
        }
      }
    }
  }
}

function render() {
  if (!canvasEl.value) return
  if (chart) {
    chart.destroy()
    chart = null
  }
  if (!props.data.length || total.value === 0) return
  chart = new Chart(canvasEl.value, buildConfig())
}

onMounted(render)

onBeforeUnmount(() => {
  if (chart) {
    chart.destroy()
    chart = null
  }
})

watch(
  () => [props.labels, props.data, props.colors, theme.current.value.dark],
  render,
  { deep: true }
)
</script>

<style scoped>
.doughnut-wrap {
  position: relative;
  height: 260px;
}
</style>