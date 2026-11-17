<template>
  <div class="wearable-preview">
    <canvas ref="canvasRef" class="wearable-preview__canvas" />
    <p v-if="problem" class="wearable-preview__problem">{{ problem }}</p>
    <div class="wearable-preview__controls">
      <label><input v-model="walking" type="checkbox" /> Walking</label>
      <label><input v-model="crouching" type="checkbox" /> Sneaking</label>
      <button type="button" class="btn btn--ghost btn--small" @click="resetView">Reset view</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'

const props = defineProps<{
  kind: WearableKind
  model: PreviewModel | null
  textureUrl: string | null
}>()

const canvasRef = ref<HTMLCanvasElement | null>(null)
const problem = ref('')
const walking = ref(false)
const crouching = ref(false)

let renderer: THREE.WebGLRenderer | null = null
let scene: THREE.Scene | null = null
let camera: THREE.PerspectiveCamera | null = null
let controls: OrbitControls | null = null
let frame = 0
let resizeObserver: ResizeObserver | null = null
let wearableRoot: THREE.Group | null = null
let wearableTexture: THREE.CanvasTexture | null = null
let animated: { object: THREE.Object3D; base: { position: THREE.Vector3; rotation: THREE.Euler }; channels: PreviewChannel[] }[] = []
let rebuildTimer: ReturnType<typeof setTimeout> | null = null
const started = performance.now()
const DEGREES = Math.PI / 180

interface Mannequin {
  root: THREE.Group
  parts: Record<'head' | 'body' | 'right_arm' | 'left_arm' | 'right_leg' | 'left_leg', THREE.Group>
  pet: THREE.Group
}

let mannequin: Mannequin | null = null

function boxMesh(from: [number, number, number], size: [number, number, number], color: number): THREE.Mesh {
  const geometry = new THREE.BoxGeometry(size[0], size[1], size[2])
  geometry.translate(from[0] + size[0] / 2, from[1] + size[1] / 2, from[2] + size[2] / 2)
  return new THREE.Mesh(geometry, new THREE.MeshLambertMaterial({ color }))
}

function part(parent: THREE.Object3D, pivot: [number, number, number], from: [number, number, number], size: [number, number, number], color: number): THREE.Group {
  const group = new THREE.Group()
  group.position.set(...pivot)
  group.add(boxMesh(from, size, color))
  parent.add(group)
  return group
}

function flipped(parent: THREE.Object3D, yaw: number): THREE.Group {
  const turn = new THREE.Group()
  turn.rotation.y = Math.PI - yaw
  parent.add(turn)
  const flip = new THREE.Group()
  flip.scale.set(-1, -1, 1)
  turn.add(flip)
  return flip
}

function buildMannequin(target: THREE.Scene): Mannequin {
  const root = new THREE.Group()
  target.add(root)
  const body = flipped(root, 0)
  const offset = new THREE.Group()
  offset.position.y = -24
  body.add(offset)
  const skin = 0xc7956d
  const shirt = 0x3fa7b5
  const pants = 0x3b3f8f
  const parts = {
    head: part(offset, [0, 0, 0], [-4, -8, -4], [8, 8, 8], skin),
    body: part(offset, [0, 0, 0], [-4, 0, -2], [8, 12, 4], shirt),
    right_arm: part(offset, [-5, 2, 0], [-3, -2, -2], [4, 12, 4], skin),
    left_arm: part(offset, [5, 2, 0], [-1, -2, -2], [4, 12, 4], skin),
    right_leg: part(offset, [-1.9, 12, 0], [-2, 0, -2], [4, 12, 4], pants),
    left_leg: part(offset, [1.9, 12, 0], [-2, 0, -2], [4, 12, 4], pants)
  }
  const petWorld = new THREE.Group()
  petWorld.position.set(-13.6, 24.8, -4.8)
  root.add(petWorld)
  const pet = flipped(petWorld, 0)
  const ground = new THREE.Mesh(new THREE.CircleGeometry(18, 40), new THREE.MeshLambertMaterial({ color: 0x2a3140 }))
  ground.rotation.x = -Math.PI / 2
  ground.position.y = -0.01
  root.add(ground)
  return { root, parts, pet }
}

function nodeObject(node: BakedNode, material: THREE.Material): THREE.Group {
  const group = new THREE.Group()
  group.position.set(...node.pivot)
  group.rotation.set(node.rotation[0] * DEGREES, node.rotation[1] * DEGREES, node.rotation[2] * DEGREES, 'ZYX')
  if (node.quads.length > 0) {
    const positions: number[] = []
    const uvs: number[] = []
    const normals: number[] = []
    const indices: number[] = []
    for (const quad of node.quads) {
      const start = positions.length / 3
      positions.push(...quad.positions)
      uvs.push(...quad.uvs)
      for (let i = 0; i < 4; i++) {
        normals.push(...quad.normal)
      }
      indices.push(start, start + 1, start + 2, start, start + 2, start + 3)
    }
    const geometry = new THREE.BufferGeometry()
    geometry.setAttribute('position', new THREE.Float32BufferAttribute(positions, 3))
    geometry.setAttribute('uv', new THREE.Float32BufferAttribute(uvs, 2))
    geometry.setAttribute('normal', new THREE.Float32BufferAttribute(normals, 3))
    geometry.setIndex(indices)
    group.add(new THREE.Mesh(geometry, material))
  }
  if (node.animations.length > 0) {
    animated.push({ object: group, base: { position: group.position.clone(), rotation: group.rotation.clone() }, channels: node.animations })
  }
  for (const child of node.children) {
    group.add(nodeObject(child, material))
  }
  return group
}

function disposeWearable() {
  if (wearableRoot) {
    wearableRoot.parent?.remove(wearableRoot)
    wearableRoot.traverse((object) => {
      if (object instanceof THREE.Mesh) {
        object.geometry.dispose()
      }
    })
    wearableRoot = null
  }
  wearableTexture?.dispose()
  wearableTexture = null
  animated = []
}

function loadImage(url: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.crossOrigin = 'anonymous'
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('The texture could not be loaded'))
    image.src = url
  })
}

async function rebuild() {
  if (!mannequin) return
  const model = props.model
  disposeWearable()
  if (!model) {
    problem.value = ''
    return
  }
  try {
    const image = props.textureUrl && model.textureWidth ? await loadImage(props.textureUrl) : null
    const baked = bakeWearable(model, image)
    if (!mannequin) return
    wearableTexture = new THREE.CanvasTexture(baked.canvas)
    wearableTexture.magFilter = THREE.NearestFilter
    wearableTexture.minFilter = THREE.NearestFilter
    wearableTexture.colorSpace = THREE.SRGBColorSpace
    const material = new THREE.MeshLambertMaterial({ map: wearableTexture, alphaTest: 0.1, side: THREE.DoubleSide })
    const root = new THREE.Group()
    for (const node of baked.nodes) {
      root.add(nodeObject(node, material))
    }
    const anchor = props.kind === 'hat' ? mannequin.parts.head : props.kind === 'back' ? mannequin.parts.body : mannequin.pet
    anchor.add(root)
    wearableRoot = root
    problem.value = ''
  } catch (error) {
    problem.value = error instanceof Error ? error.message : 'The model could not be previewed'
  }
}

function scheduleRebuild() {
  if (rebuildTimer) clearTimeout(rebuildTimer)
  rebuildTimer = setTimeout(() => void rebuild(), 150)
}

function poseMannequin(time: number, motion: number) {
  if (!mannequin) return
  const { parts } = mannequin
  const swing = time * 0.6662
  parts.right_arm.rotation.set(Math.cos(swing + Math.PI) * 1.4 * motion * 0.5, 0, 0)
  parts.left_arm.rotation.set(Math.cos(swing) * 1.4 * motion * 0.5, 0, 0)
  parts.right_leg.rotation.set(Math.cos(swing) * 1.4 * motion, 0, 0)
  parts.left_leg.rotation.set(Math.cos(swing + Math.PI) * 1.4 * motion, 0, 0)
  const crouch = crouching.value
  parts.body.rotation.x = crouch ? 0.5 : 0
  parts.body.position.y = crouch ? 3.2 : 0
  parts.head.position.y = crouch ? 4.2 : 0
  parts.right_arm.position.y = crouch ? 5.2 : 2
  parts.left_arm.position.y = crouch ? 5.2 : 2
  parts.right_leg.position.set(-1.9, crouch ? 12.2 : 12, crouch ? 4 : 0)
  parts.left_leg.position.set(1.9, crouch ? 12.2 : 12, crouch ? 4 : 0)
  if (crouch) {
    parts.right_arm.rotation.x += 0.4
    parts.left_arm.rotation.x += 0.4
  }
}

function animate() {
  frame = requestAnimationFrame(animate)
  if (!renderer || !scene || !camera) return
  const time = ((performance.now() - started) / 1000) * 20
  const motion = walking.value ? 1 : 0
  poseMannequin(time, motion)
  for (const entry of animated) {
    entry.object.position.copy(entry.base.position)
    entry.object.rotation.copy(entry.base.rotation)
    entry.object.scale.set(1, 1, 1)
    for (const channel of entry.channels) {
      const value = channelValue(channel, time, motion, crouching.value)
      switch (channel.property) {
        case 'rotX':
          entry.object.rotation.x += value * DEGREES
          break
        case 'rotY':
          entry.object.rotation.y += value * DEGREES
          break
        case 'rotZ':
          entry.object.rotation.z += value * DEGREES
          break
        case 'x':
          entry.object.position.x += value
          break
        case 'y':
          entry.object.position.y += value
          break
        case 'z':
          entry.object.position.z += value
          break
        case 'scale':
          entry.object.scale.multiplyScalar(value)
          break
        case 'scaleX':
          entry.object.scale.x *= value
          break
        case 'scaleY':
          entry.object.scale.y *= value
          break
        case 'scaleZ':
          entry.object.scale.z *= value
          break
      }
    }
  }
  controls?.update()
  renderer.render(scene, camera)
}

function resetView() {
  if (!camera || !controls) return
  const behind = props.kind === 'back'
  camera.position.set(behind ? -36 : 36, 34, behind ? -70 : 70)
  controls.target.set(props.kind === 'pet' ? -6 : 0, props.kind === 'hat' ? 24 : 19, 0)
  controls.update()
}

function resize() {
  const canvas = canvasRef.value
  if (!canvas || !renderer || !camera) return
  const width = canvas.clientWidth
  const height = canvas.clientHeight
  if (width === 0 || height === 0) return
  renderer.setSize(width, height, false)
  camera.aspect = width / height
  camera.updateProjectionMatrix()
}

onMounted(() => {
  const canvas = canvasRef.value
  if (!canvas) return
  renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: true })
  renderer.setPixelRatio(Math.min(2, window.devicePixelRatio))
  scene = new THREE.Scene()
  scene.add(new THREE.AmbientLight(0xffffff, 0.75))
  const sun = new THREE.DirectionalLight(0xffffff, 0.9)
  sun.position.set(30, 60, 40)
  scene.add(sun)
  camera = new THREE.PerspectiveCamera(35, 1, 1, 500)
  controls = new OrbitControls(camera, canvas)
  controls.enablePan = false
  controls.minDistance = 20
  controls.maxDistance = 140
  mannequin = buildMannequin(scene)
  resetView()
  resize()
  resizeObserver = new ResizeObserver(resize)
  resizeObserver.observe(canvas)
  void rebuild()
  animate()
})

watch(() => [props.model, props.textureUrl, props.kind], scheduleRebuild, { deep: true })
watch(() => props.kind, resetView)

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  if (rebuildTimer) clearTimeout(rebuildTimer)
  resizeObserver?.disconnect()
  disposeWearable()
  controls?.dispose()
  scene?.traverse((object) => {
    if (object instanceof THREE.Mesh) {
      object.geometry.dispose()
      ;(object.material as THREE.Material).dispose()
    }
  })
  renderer?.dispose()
  renderer = null
  scene = null
  mannequin = null
})
</script>

<style scoped lang="scss">
.wearable-preview {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.wearable-preview__canvas {
  width: 100%;
  aspect-ratio: 4 / 5;
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  background: radial-gradient(circle at 50% 35%, #3a4a6a, #151a24);
  cursor: grab;
}

.wearable-preview__problem {
  margin: 0;
  color: var(--danger);
  font-size: 0.85rem;
}

.wearable-preview__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  font-size: 0.85rem;
  color: var(--text-secondary);

  label {
    display: inline-flex;
    align-items: center;
    gap: 0.3rem;
  }
}
</style>
