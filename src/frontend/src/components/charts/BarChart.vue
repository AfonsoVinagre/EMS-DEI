<template>
  <div class="bar-chart-wrap">
    <canvas ref="canvasEl"></canvas>
    <div v-if="!labels.length" class="text-center text-medium-emphasis pa-4">
      {{ noDataText }}
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Bar chart (single-series, magnitude) driven directly by Chart.js — sem
 * wrapper, para controlo total do tema. Eixos/texto vestem tokens de tinta
 * (nunca a cor da série); a cor das barras é um hue único (magnitude), não
 * categorical. Re-renderiza quando os dados ou o tema mudam.
 */
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useTheme } from 'vuetify'
import Chart from 'chart.js/auto'
import type { ChartConfiguration } from 'chart.js'

const props = withDefaults(
  defineProps<{
    labels: string[]
    data: number[]
    color?: string
    horizontal?: boolean
    unit?: string
    noDataText?: string
  }>(),
  {
    color: '#2196F3',
    horizontal: false,
    unit: '',
    noDataText: 'Sem dados.'
  }
)

const theme = useTheme()
const canvasEl = ref<HTMLCanvasElement | null>(null)
let chart: Chart<'bar'> | null = null

function inkColor(): string {
  return theme.current.value.dark ? 'rgba(255,255,255,0.87)' : 'rgba(0,0,0,0.87)'
}
function mutedColor(): string {
  return theme.current.value.dark ? 'rgba(255,255,255,0.6)' : 'rgba(0,0,0,0.6)'
}
function gridColor(): string {
  return theme.current.value.dark ? 'rgba(255,255,255,0.12)' : 'rgba(0,0,0,0.08)'
}

function buildConfig(): ChartConfiguration<'bar'> {
  const indexAxis = props.horizontal ? ('y' as const) : ('x' as const)
  return {
    type: 'bar',
    data: {
      labels: props.labels,
      datasets: [
        {
          label: '',
          data: props.data,
          backgroundColor: props.color,
          hoverBackgroundColor: props.color,
          borderRadius: 4,
          borderSkipped: false,
          maxBarThickness: 40
        }
      ]
    },
    options: {
      indexAxis,
      responsive: true,
      maintainAspectRatio: false,
      // Texto/legend em tinta, não na cor da série.
      color: inkColor(),
      plugins: {
        legend: { display: false }, // single-series: o título nomeia-a.
        tooltip: {
          callbacks: {
            label: (ctx) => {
              const v = ctx.parsed[props.horizontal ? 'x' : 'y']
              return `${v}${props.unit}`
            }
          }
        }
      },
      scales: {
        // beginAtZero é inofensivo no eixo de categoria; forma uniforme para
        // evitar divergência de tipos entre indexAxis 'x' e 'y'.
        x: { grid: { color: gridColor() }, ticks: { color: mutedColor() }, beginAtZero: true },
        y: { grid: { color: gridColor() }, ticks: { color: mutedColor() }, beginAtZero: true }
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
  if (!props.labels.length) return
  chart = new Chart(canvasEl.value, buildConfig())
}

onMounted(render)

onBeforeUnmount(() => {
  if (chart) {
    chart.destroy()
    chart = null
  }
})

// Re-renderiza em mudança de dados, orientação ou tema (cores de eixos).
watch(
  () => [props.labels, props.data, props.horizontal, theme.current.value.dark],
  render,
  { deep: true }
)
</script>

<style scoped>
.bar-chart-wrap {
  position: relative;
  height: 260px;
}
</style>