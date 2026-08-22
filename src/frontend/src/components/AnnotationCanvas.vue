<template>
  <div>
    <!-- Toolbar: tools, colors, stroke width, undo, clear. -->
    <div class="d-flex flex-wrap align-center ga-2 mb-3">
      <v-btn-toggle v-model="tool" mandatory density="compact" color="primary">
        <v-btn value="pen" icon="mdi-pencil" size="small" title="Caneta"></v-btn>
        <v-btn value="text" icon="mdi-format-text" size="small" title="Texto"></v-btn>
        <v-btn value="check" icon="mdi-check" size="small" title="Visto ✓"></v-btn>
        <v-btn value="cross" icon="mdi-close" size="small" title="Errado ✗"></v-btn>
      </v-btn-toggle>

      <v-divider vertical class="mx-1"></v-divider>

      <div class="d-flex align-center ga-1">
        <v-btn
          v-for="c in colors"
          :key="c"
          :color="color === c ? 'primary' : undefined"
          icon
          size="x-small"
          variant="outlined"
          :style="{ backgroundColor: c, borderColor: c }"
          @click="color = c"
        ></v-btn>
      </div>

      <v-divider vertical class="mx-1"></v-divider>

      <v-btn-toggle v-model="widthIndex" mandatory density="compact">
        <v-btn :value="0" size="small" variant="outlined" title="Fino">F</v-btn>
        <v-btn :value="1" size="small" variant="outlined" title="Médio">M</v-btn>
        <v-btn :value="2" size="small" variant="outlined" title="Grosso">G</v-btn>
      </v-btn-toggle>

      <v-divider vertical class="mx-1"></v-divider>

      <v-btn
        icon="mdi-undo"
        size="small"
        variant="text"
        title="Desfazer"
        :disabled="strokes.length === 0"
        @click="undo"
      ></v-btn>
      <v-btn
        icon="mdi-trash-can-outline"
        size="small"
        variant="text"
        title="Limpar"
        :disabled="strokes.length === 0"
        @click="clear"
      ></v-btn>
    </div>

    <!-- The canvas holds the image + annotations composited together, so
         canvas.toBlob at submit time yields the final annotated PNG. It is
         drawn at the image's native resolution (quality) and scaled down for
         display by CSS. -->
    <div class="text-center">
      <canvas
        ref="canvasEl"
        class="annotation-canvas"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointerleave="onPointerUp"
      ></canvas>
      <v-progress-circular
        v-if="!imageLoaded"
        indeterminate
        color="primary"
        class="my-8"
      ></v-progress-circular>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{ src: string }>()

const canvasEl = ref<HTMLCanvasElement | null>(null)
const tool = ref<'pen' | 'text' | 'check' | 'cross'>('pen')
const color = ref<string>('#e53935')
const widthIndex = ref<number>(1)
const colors = ['#e53935', '#1976d2', '#2e7d32', '#000000']

const imageLoaded = ref(false)
let img: HTMLImageElement | null = null
let strokes: Stroke[] = []
let drawing = false
let current: Stroke | null = null

type Stroke =
  | { kind: 'pen'; color: string; width: number; points: number[][] }
  | { kind: 'text'; color: string; size: number; x: number; y: number; text: string }
  | { kind: 'stamp'; color: string; size: number; x: number; y: number; symbol: string }

/** Canvas pixels per "display unit" — strokes scale with image resolution so
 *  a pen mark is visible regardless of the page's native pixel size. */
function unit(): number {
  const w = canvasEl.value?.width ?? 1000
  return Math.max(2, w / 500)
}

function lineWidth(): number {
  return unit() * [1.5, 3, 5][widthIndex.value]
}

function fontSize(): number {
  return unit() * [9, 14, 20][widthIndex.value]
}

/** Convert a pointer event to canvas (image) coordinates. */
function toCanvas(e: PointerEvent): [number, number] {
  const canvas = canvasEl.value!
  const rect = canvas.getBoundingClientRect()
  const scaleX = canvas.width / rect.width
  const scaleY = canvas.height / rect.height
  return [(e.clientX - rect.left) * scaleX, (e.clientY - rect.top) * scaleY]
}

function redraw() {
  const canvas = canvasEl.value
  if (!canvas || !img) return
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(img, 0, 0, canvas.width, canvas.height)
  for (const s of strokes) drawStroke(ctx, s)
  if (current) drawStroke(ctx, current)
}

function drawStroke(ctx: CanvasRenderingContext2D, s: Stroke) {
  ctx.lineJoin = 'round'
  ctx.lineCap = 'round'
  if (s.kind === 'pen') {
    if (s.points.length === 0) return
    ctx.strokeStyle = s.color
    ctx.lineWidth = s.width
    ctx.beginPath()
    ctx.moveTo(s.points[0][0], s.points[0][1])
    for (let i = 1; i < s.points.length; i++) ctx.lineTo(s.points[i][0], s.points[i][1])
    ctx.stroke()
  } else {
    ctx.fillStyle = s.color
    ctx.font = `${s.kind === 'stamp' ? 'bold ' : ''}${s.size}px sans-serif`
    ctx.textBaseline = 'top'
    ctx.fillText(s.kind === 'stamp' ? s.symbol : s.text, s.x, s.y)
  }
}

function onPointerDown(e: PointerEvent) {
  if (!imageLoaded.value || !canvasEl.value) return
  e.preventDefault()
  const [x, y] = toCanvas(e)
  if (tool.value === 'pen') {
    drawing = true
    current = { kind: 'pen', color: color.value, width: lineWidth(), points: [[x, y]] }
    canvasEl.value.setPointerCapture(e.pointerId)
  } else if (tool.value === 'text') {
    const text = window.prompt('Texto da anotação:')
    if (text && text.trim()) {
      strokes.push({ kind: 'text', color: color.value, size: fontSize(), x, y, text: text.trim() })
      redraw()
    }
  } else if (tool.value === 'check' || tool.value === 'cross') {
    strokes.push({
      kind: 'stamp',
      color: color.value,
      size: fontSize() * 1.4,
      x,
      y,
      symbol: tool.value === 'check' ? '✓' : '✗'
    })
    redraw()
  }
}

function onPointerMove(e: PointerEvent) {
  if (!drawing || current?.kind !== 'pen') return
  const [x, y] = toCanvas(e)
  current.points.push([x, y])
  redraw()
}

function onPointerUp(e: PointerEvent) {
  if (!drawing) return
  drawing = false
  if (current && current.kind === 'pen' && current.points.length > 0) {
    strokes.push(current)
  }
  current = null
  redraw()
}

function undo() {
  strokes.pop()
  redraw()
}

function clear() {
  strokes = []
  redraw()
}

/** Returns the composited PNG (image + annotations), or null when the teacher
 *  drew nothing — the caller then submits without an annotation. */
function getBlob(): Promise<Blob | null> {
  if (strokes.length === 0) return Promise.resolve(null)
  const canvas = canvasEl.value
  if (!canvas) return Promise.resolve(null)
  return new Promise((resolve) => {
    canvas.toBlob((blob) => resolve(blob), 'image/png')
  })
}

defineExpose({ getBlob })

function loadImage(src: string) {
  imageLoaded.value = false
  strokes = []
  current = null
  const image = new Image()
  image.onload = () => {
    img = image
    const canvas = canvasEl.value
    if (canvas) {
      canvas.width = image.naturalWidth
      canvas.height = image.naturalHeight
      redraw()
    }
    imageLoaded.value = true
  }
  image.src = src
}

watch(() => props.src, (src) => { if (src) loadImage(src) }, { immediate: true })

onBeforeUnmount(() => {
  strokes = []
  img = null
})
</script>

<style scoped>
.annotation-canvas {
  max-width: 100%;
  max-height: 650px;
  object-fit: contain;
  touch-action: none;
  cursor: crosshair;
  border: 1px solid rgba(0, 0, 0, 0.12);
}
</style>