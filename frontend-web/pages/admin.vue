<template>
  <div class="admin">
    <div v-if="checking" class="admin__center">Checking session…</div>

    <form v-else-if="!authenticated" class="admin__login" @submit.prevent="login">
      <h1 class="admin__title">Pretupid Admin</h1>
      <p class="admin__muted">Sign in with the admin key. Five failed attempts lock this network out for 15 minutes.</p>
      <input v-model="adminKey" class="input" type="password" autocomplete="current-password" placeholder="Admin key" required />
      <button class="btn" type="submit" :disabled="busy">{{ busy ? 'Signing in' : 'Sign in' }}</button>
      <p v-if="error" class="admin__error">{{ error }}</p>
    </form>

    <template v-else>
      <header class="admin__header">
        <h1 class="admin__title">Pretupid Admin</h1>
        <button class="btn btn--ghost btn--small" title="Signs out every admin session" @click="logout">Sign out everywhere</button>
      </header>

      <div class="admin__layout">
      <nav class="admin__nav" aria-label="Admin sections">
        <div v-for="group in tabGroups" :key="group.label" class="admin__nav-group">
          <span class="admin__nav-label">{{ group.label }}</span>
          <button
            v-for="item in group.items"
            :key="item.id"
            class="admin__tab"
            :class="{ 'admin__tab--active': tab === item.id }"
            :aria-current="tab === item.id ? 'page' : undefined"
            @click="selectTab(item.id)"
          >
            {{ item.label }}
            <span v-if="item.id === 'capes' && (stats.reportedCapes ?? 0) > 0" class="admin__badge">{{ stats.reportedCapes }}</span>
          </button>
        </div>
      </nav>

      <main class="admin__main">
      <p v-if="error" class="admin__error" role="alert">{{ error }}</p>
      <p v-if="notice" class="admin__notice" role="status">{{ notice }}</p>

      <section v-if="tab === 'overview'" class="admin__grid">
        <div v-for="stat in statCards" :key="stat.label" class="admin__stat">
          <span class="admin__stat-value">{{ stat.value }}</span>
          <span class="admin__muted">{{ stat.label }}</span>
        </div>
      </section>

      <section v-if="tab === 'capes'" class="admin__section">
        <div class="admin__row">
          <button class="btn btn--small" :class="capeStatus === 'active' ? '' : 'btn--ghost'" @click="loadCapes('active', 0)">Active</button>
          <button class="btn btn--small" :class="capeStatus === 'removed' ? '' : 'btn--ghost'" @click="loadCapes('removed', 0)">Removed</button>
          <span class="admin__muted">Reported capes are listed first.</span>
        </div>
        <p v-if="capes.length === 0" class="admin__muted">No capes here.</p>
        <div class="admin__capes">
          <article v-for="cape in capes" :key="cape.id" class="admin__cape" :class="{ 'admin__cape--reported': cape.reports > 0 }">
            <img v-if="cape.status === 'active'" :src="cape.preview" :alt="`Cape of ${cape.name}`" class="admin__cape-image" />
            <div v-else class="admin__cape-image admin__cape-image--gone">Deleted</div>
            <div class="admin__cape-info">
              <strong>{{ cape.name || 'Unknown player' }}</strong>
              <span class="admin__muted admin__mono">{{ cape.uuid }}</span>
              <span class="admin__muted">{{ cape.width }}×{{ cape.height }} · {{ formatDate(cape.created_at) }}</span>
              <span v-if="cape.reports > 0" class="admin__warning">{{ cape.reports }} report{{ cape.reports === 1 ? '' : 's' }}</span>
              <span v-if="cape.removed_reason" class="admin__muted">Removed: {{ cape.removed_reason }}</span>
              <span v-if="cape.upload_banned" class="admin__warning">Uploads banned</span>
            </div>
            <div v-if="cape.status === 'active'" class="admin__actions">
              <button class="btn btn--small btn--danger" @click="removeCape(cape, false)">Remove</button>
              <button class="btn btn--small btn--danger" @click="removeCape(cape, true)">Remove + ban uploads</button>
              <button v-if="cape.reports > 0" class="btn btn--small btn--ghost" @click="dismissReports(cape)">Dismiss reports</button>
            </div>
          </article>
        </div>
        <div class="admin__row">
          <button class="btn btn--ghost btn--small" :disabled="capePage === 0" @click="loadCapes(capeStatus, capePage - 1)">Previous</button>
          <button class="btn btn--ghost btn--small" :disabled="!capesHasMore" @click="loadCapes(capeStatus, capePage + 1)">Next</button>
        </div>
      </section>

      <section v-if="tab === 'players'" class="admin__section">
        <form class="admin__row" @submit.prevent="loadPlayers">
          <input v-model="playerQuery" class="input" placeholder="Name prefix or UUID" />
          <button class="btn btn--small" type="submit">Search</button>
        </form>
        <table class="admin__table">
          <thead>
            <tr><th>Player</th><th>Last seen</th><th>Client</th><th>Uploads</th><th>Account</th></tr>
          </thead>
          <tbody>
            <tr v-for="player in players" :key="player.uuid">
              <td>
                <strong>{{ player.name }}</strong>
                <span v-if="player.online" class="admin__online">online</span><br />
                <span class="admin__muted admin__mono">{{ player.uuid }}</span>
              </td>
              <td>{{ formatDate(player.lastSeen) }}</td>
              <td>{{ player.clientVersion || '—' }}</td>
              <td>
                <button class="btn btn--small" :class="player.uploadBanned ? '' : 'btn--ghost'" @click="updatePlayer(player, { uploadBanned: !player.uploadBanned })">
                  {{ player.uploadBanned ? 'Allow uploads' : 'Ban uploads' }}
                </button>
              </td>
              <td>
                <button class="btn btn--small" :class="player.banned ? '' : 'btn--danger'" @click="updatePlayer(player, { banned: !player.banned })">
                  {{ player.banned ? 'Unsuspend' : 'Suspend' }}
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <section v-if="tab === 'news'" class="admin__section">
        <div class="admin__split">
          <div class="admin__list">
            <button class="btn btn--small" @click="editNews(null)">New post</button>
            <button v-for="post in news" :key="post.id" class="admin__list-item" :class="{ 'admin__list-item--active': newsForm.id === post.id }" @click="editNews(post)">
              <strong>{{ post.title }}</strong>
              <span class="admin__muted">{{ post.published ? 'Published' : 'Draft' }} · {{ post.category }}{{ post.pinned ? ' · pinned' : '' }}</span>
            </button>
          </div>
          <form class="admin__editor" @submit.prevent="saveNews">
            <input v-model="newsForm.title" class="input" placeholder="Title" maxlength="120" required />
            <input v-model="newsForm.summary" class="input" placeholder="One-line summary (shown in lists and in game)" maxlength="240" />
            <div class="admin__row">
              <select v-model="newsForm.category" class="input">
                <option v-for="category in categories" :key="category" :value="category">{{ category }}</option>
              </select>
              <label class="admin__check"><input v-model="newsForm.pinned" type="checkbox" /> Pinned</label>
              <label class="admin__check"><input v-model="newsForm.published" type="checkbox" /> Published</label>
            </div>
            <div class="admin__md">
              <textarea v-model="newsForm.body" class="input admin__textarea" placeholder="Markdown: # Heading, **bold**, *italic*, - lists, [link](https://…), [[Button]](https://…), ![Image](https://…/image.png)" maxlength="20000" required />
              <div class="admin__preview markdown" v-html="newsPreview" />
            </div>
            <div class="admin__row">
              <button class="btn" type="submit" :disabled="busy">{{ newsForm.id ? 'Save changes' : 'Create post' }}</button>
              <button v-if="newsForm.id" class="btn btn--danger" type="button" @click="deleteNews">Delete</button>
            </div>
          </form>
        </div>
      </section>

      <section v-if="tab === 'cosmetics'" class="admin__section">
        <div class="admin__chips" role="tablist">
          <button
            v-for="category in cosmeticCategories"
            :key="category.id"
            class="admin__chip"
            :class="{ 'admin__chip--active': cosmeticCategory === category.id }"
            role="tab"
            :aria-selected="cosmeticCategory === category.id"
            @click="selectCosmeticCategory(category.id)"
          >
            {{ category.label }} <span class="admin__chip-count">{{ categoryCount(category.id) }}</span>
          </button>
        </div>
        <p class="admin__muted">{{ activeCosmeticCategory.hint }} Changes reach players within a minute, no client update needed.</p>

        <div class="admin__split" :class="{ 'admin__split--wide': isWearable }">
          <div class="admin__list">
            <button class="btn btn--small" @click="editCosmetic(null)">New {{ activeCosmeticCategory.singular.toLowerCase() }}</button>
            <button v-if="cosmeticCategory === 'cape'" class="admin__list-item" @click="selectTab('capes')">
              <strong>Player capes</strong>
              <span class="admin__muted">{{ customCapes.active }} uploaded · {{ customCapes.equipped }} wearing · moderate in Capes</span>
            </button>
            <p v-if="catalogItems.length === 0" class="admin__muted">No {{ activeCosmeticCategory.label.toLowerCase() }} yet.</p>
            <button
              v-for="item in catalogItems"
              :key="item.id"
              class="admin__list-item admin__list-item--media"
              :class="{ 'admin__list-item--active': cosmeticForm.existing && cosmeticForm.id === item.id }"
              @click="editCosmetic(item)"
            >
              <span v-if="item.type === 'cape' && item.imageUrl" class="admin__cape-thumb" :style="{ backgroundImage: `url(${item.imageUrl})` }" />
              <span class="admin__list-text">
                <strong>{{ item.name }}</strong>
                <span class="admin__muted">{{ item.id }}{{ item.enabled ? '' : ' · hidden' }}{{ item.equipped !== null ? ` · ${item.equipped} wearing` : '' }}</span>
              </span>
            </button>
          </div>
          <form class="admin__editor" @submit.prevent="saveCosmetic">
            <div class="admin__row">
              <input v-model="cosmeticForm.id" class="input" placeholder="id (e.g. founders-cape)" :disabled="cosmeticForm.existing" pattern="[a-z0-9][a-z0-9_\-]{0,31}" required />
              <span class="admin__type">{{ activeCosmeticCategory.singular }}</span>
            </div>
            <input v-model="cosmeticForm.name" class="input" placeholder="Display name" maxlength="40" required />
            <input v-model="cosmeticForm.description" class="input" placeholder="Description" maxlength="160" />
            <div class="admin__row">
              <label class="admin__check"><input v-model="cosmeticForm.enabled" type="checkbox" /> Visible to players</label>
              <label class="admin__check">Sort <input v-model.number="cosmeticForm.sort" class="input admin__narrow" type="number" /></label>
            </div>
            <template v-if="cosmeticForm.type === 'cape'">
              <p class="admin__muted">PNG cape texture, 64×32 (or 128×64, 256×128, 512×256), up to 128 KB.</p>
              <input type="file" accept="image/png" @change="pickImage" />
              <img v-if="cosmeticForm.preview" :src="cosmeticForm.preview" alt="Cape preview" class="admin__cape-image" />
            </template>
            <div v-else-if="isWearable" class="admin__wearable-editor">
              <div class="admin__wearable-fields">
                <div class="admin__texture">
                  <strong>Texture (optional)</strong>
                  <p class="admin__muted">
                    PNG with sides of 16, 32, 64, 128 or 256 pixels, up to 128 KB. Boxes with a <code>uv</code> use it with the Minecraft box layout;
                    boxes without one are painted from their colors and pattern.
                  </p>
                  <div class="admin__row">
                    <input type="file" accept="image/png" @change="pickImage" />
                    <button v-if="cosmeticForm.preview" class="btn btn--ghost btn--small" type="button" @click="clearTexture">Remove texture</button>
                  </div>
                  <img v-if="cosmeticForm.preview" :src="cosmeticForm.preview" alt="Texture" class="admin__texture-image" />
                </div>
                <details class="admin__help">
                  <summary>Model format</summary>
                  <p>
                    <code>nodes</code>: parts with <code>name</code>, <code>pivot</code> [x, y, z], <code>rotation</code> [x, y, z] in degrees, <code>boxes</code>,
                    <code>children</code> and <code>animations</code>. Units are Minecraft pixels; y points down. Hats hang off the head (top of the head is y −8),
                    back items off the body (the back is z 2), pets float beside the player.
                  </p>
                  <p>
                    Box: <code>from</code> [x, y, z], <code>size</code> [w, h, d], <code>color</code>, <code>accent</code>, <code>pattern</code>
                    ({{ wearableOptions.patterns.join(', ') }}), optional <code>uv</code> [u, v] and <code>mirror</code>.
                  </p>
                  <p>
                    Animation: <code>property</code> ({{ wearableOptions.properties.join(', ') }}), <code>wave</code> ({{ wearableOptions.waves.join(', ') }}),
                    <code>amplitude</code>, <code>speed</code> (per tick), <code>phase</code>, <code>offset</code>, <code>walk</code> (amplitude boost while walking),
                    <code>walkOffset</code> and <code>crouch</code> (added while sneaking). Rotations are degrees, scale values multiply.
                  </p>
                  <p>Limits: {{ wearableOptions.limits.nodes }} nodes, {{ wearableOptions.limits.boxes }} boxes, {{ wearableOptions.limits.depth }} levels, {{ wearableOptions.limits.channels }} animations per node.</p>
                </details>
                <textarea v-model="cosmeticForm.data" class="input admin__textarea admin__textarea--tall admin__mono" spellcheck="false" />
                <p v-if="modelError" class="admin__warning">{{ modelError }}</p>
              </div>
              <ClientOnly>
                <WearablePreview :kind="wearableKind" :model="previewModel" :texture-url="cosmeticForm.preview || null" />
              </ClientOnly>
            </div>
            <template v-else>
              <p class="admin__muted">
                <template v-if="cosmeticForm.type === 'trail'">
                  JSON: particle ({{ particles.join(', ') }}), colors (up to 4 hex), density 1–5, pattern ({{ patterns.join(', ') }}), size 0.5–2.
                </template>
                <template v-else>
                  JSON: duration (seconds), loop, ease (smooth|linear), parts ({{ parts.join(', ') }}) → keyframes with t (0–1) and x/y/z rotation in degrees. The root part turns and moves the whole player (px/py/pz in pixels, 16 per block). hold keeps the last pose until the player stops it; stop: "sneak" lets only sneaking end it.
                </template>
              </p>
              <textarea v-model="cosmeticForm.data" class="input admin__textarea admin__mono" spellcheck="false" />
            </template>
            <div class="admin__row">
              <button class="btn" type="submit" :disabled="busy">{{ cosmeticForm.existing ? 'Save changes' : `Create ${activeCosmeticCategory.singular.toLowerCase()}` }}</button>
              <button v-if="cosmeticForm.existing" class="btn btn--ghost" type="button" @click="duplicateCosmetic">Duplicate</button>
              <button v-if="cosmeticForm.existing" class="btn btn--danger" type="button" @click="deleteCosmetic">Delete</button>
            </div>
          </form>
        </div>
      </section>

      <section v-if="tab === 'rules'" class="admin__section">
        <p class="admin__muted">
          Pretupid Client pauses the listed modules while a player is on a matching server and turns them back on when they leave.
          Use an exact address (play.example.net) or a wildcard (*.example.net).
        </p>
        <div class="admin__split">
          <div class="admin__list">
            <button class="btn btn--small" @click="editRule(null)">New rule</button>
            <button v-for="rule in rules" :key="rule.id" class="admin__list-item" :class="{ 'admin__list-item--active': ruleForm.id === rule.id }" @click="editRule(rule)">
              <strong>{{ rule.pattern }}</strong>
              <span class="admin__muted">{{ rule.modules.length }} module{{ rule.modules.length === 1 ? '' : 's' }} paused</span>
            </button>
          </div>
          <form class="admin__editor" @submit.prevent="saveRule">
            <input v-model="ruleForm.pattern" class="input" placeholder="Server address, e.g. play.example.net" maxlength="253" required />
            <input v-model="ruleForm.reason" class="input" placeholder="Reason shown to players (optional)" maxlength="160" />
            <div class="admin__modules">
              <label v-for="module in moduleIds" :key="module" class="admin__check">
                <input v-model="ruleForm.modules" type="checkbox" :value="module" />
                {{ module }}
              </label>
            </div>
            <div class="admin__row">
              <button class="btn" type="submit" :disabled="busy || ruleForm.modules.length === 0">Save rule</button>
              <button v-if="ruleForm.id" class="btn btn--danger" type="button" @click="deleteRule">Delete</button>
            </div>
          </form>
        </div>
      </section>

      <section v-if="tab === 'audit'" class="admin__section">
        <table class="admin__table">
          <thead>
            <tr><th>When</th><th>Action</th><th>Target</th><th>Detail</th><th>IP</th></tr>
          </thead>
          <tbody>
            <tr v-for="entry in auditLog" :key="entry.id">
              <td>{{ formatDate(entry.created_at) }}</td>
              <td>{{ entry.action }}</td>
              <td class="admin__mono">{{ entry.target }}</td>
              <td>{{ entry.detail }}</td>
              <td class="admin__mono">{{ entry.ip }}</td>
            </tr>
          </tbody>
        </table>
      </section>
      </main>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
type Tab = 'overview' | 'capes' | 'players' | 'news' | 'cosmetics' | 'rules' | 'audit'

interface ServerRule {
  id: number
  pattern: string
  modules: string[]
  reason: string
}

interface Cape {
  id: string
  uuid: string
  name: string | null
  width: number
  height: number
  status: string
  reports: number
  created_at: number
  removed_reason: string | null
  upload_banned: number | null
  preview: string
}

interface Player {
  uuid: string
  name: string
  lastSeen: number
  online: boolean
  clientVersion: string
  uploadBanned: boolean
  banned: boolean
}

interface NewsPost {
  id: string
  title: string
  summary: string
  body: string
  category: string
  pinned: boolean
  published: boolean
}

interface Cosmetic {
  id: string
  type: CosmeticCategory
  name: string
  description: string
  imageUrl: string | null
  data: unknown
  enabled: boolean
  sort: number
  equipped: number | null
}

type CosmeticCategory = 'cape' | 'trail' | 'emote' | WearableKind

interface WearableOptions {
  patterns: string[]
  properties: string[]
  waves: string[]
  limits: { nodes: number; boxes: number; depth: number; channels: number }
}

interface AuditEntry {
  id: number
  action: string
  target: string
  detail: string
  ip: string
  created_at: number
}

definePageMeta({ layout: 'default' })
useHead({ title: 'Admin · Pretupid', meta: [{ name: 'robots', content: 'noindex, nofollow' }] })

const tabGroups: { label: string; items: { id: Tab; label: string }[] }[] = [
  { label: 'Dashboard', items: [{ id: 'overview', label: 'Overview' }] },
  { label: 'Content', items: [{ id: 'news', label: 'News' }, { id: 'cosmetics', label: 'Cosmetics' }] },
  { label: 'Moderation', items: [{ id: 'capes', label: 'Capes' }, { id: 'players', label: 'Players' }] },
  { label: 'Client', items: [{ id: 'rules', label: 'Server rules' }] },
  { label: 'Security', items: [{ id: 'audit', label: 'Audit log' }] }
]
const emptyRule = () => ({ id: 0, pattern: '', reason: '', modules: [] as string[] })
const categories = ['news', 'update', 'announcement', 'event', 'maintenance']
const emptyNews = () => ({ id: '', title: '', summary: '', body: '', category: 'news', pinned: false, published: false })
const emptyCosmetic = () => ({
  existing: false,
  id: '',
  type: 'trail' as Cosmetic['type'],
  name: '',
  description: '',
  enabled: true,
  sort: 0,
  data: '{\n  "particle": "end_rod",\n  "colors": ["#FFFFFF"],\n  "density": 2,\n  "pattern": "stream",\n  "size": 1\n}',
  image: '',
  preview: '',
  removeImage: false
})

const WEARABLE_TEMPLATES: Record<WearableKind, object> = {
  hat: {
    nodes: [
      {
        name: 'hat',
        pivot: [0, 0, 0],
        boxes: [
          { from: [-4.5, -9, -4.5], size: [9, 1, 9], color: '#2B2235' },
          { from: [-3, -14, -3], size: [6, 5, 6], color: '#2B2235', accent: '#C0392B', pattern: 'banded' }
        ],
        animations: []
      }
    ]
  },
  back: {
    nodes: [
      {
        name: 'left_wing',
        pivot: [1.5, 1.5, 3.6],
        boxes: [{ from: [0, -1, 0], size: [10, 8, 1], color: '#F4F5FA', accent: '#C9D2E3', pattern: 'tipped' }],
        animations: [{ property: 'rotZ', wave: 'sine', amplitude: -6, speed: 0.12, offset: -12, walk: 2 }]
      },
      {
        name: 'right_wing',
        pivot: [-1.5, 1.5, 3.6],
        boxes: [{ from: [-10, -1, 0], size: [10, 8, 1], color: '#F4F5FA', accent: '#C9D2E3', pattern: 'tipped' }],
        animations: [{ property: 'rotZ', wave: 'sine', amplitude: 6, speed: 0.12, offset: 12, walk: 2 }]
      }
    ]
  },
  pet: {
    nodes: [
      {
        name: 'pet',
        pivot: [0, 0, 0],
        boxes: [
          { from: [-3, -3, -3], size: [6, 6, 6], color: '#8FC4FF', accent: '#4A9EFF', pattern: 'rim' },
          { from: [-2, -1.5, -3.4], size: [1, 2, 1], color: '#1E1A22', pattern: 'glow' },
          { from: [1, -1.5, -3.4], size: [1, 2, 1], color: '#1E1A22', pattern: 'glow' }
        ],
        animations: [{ property: 'y', wave: 'sine', amplitude: 1.2, speed: 0.12 }]
      }
    ]
  }
}

function isWearableCategory(value: string): value is WearableKind {
  return value === 'hat' || value === 'back' || value === 'pet'
}

const checking = ref(true)
const authenticated = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const adminKey = ref('')
const tab = ref<Tab>('overview')
const stats = ref<Record<string, number>>({})
const capes = ref<Cape[]>([])
const capeStatus = ref<'active' | 'removed'>('active')
const capePage = ref(0)
const capesHasMore = ref(false)
const players = ref<Player[]>([])
const playerQuery = ref('')
const news = ref<NewsPost[]>([])
const newsForm = reactive(emptyNews())
const cosmetics = ref<Cosmetic[]>([])
const cosmeticForm = reactive(emptyCosmetic())
const customCapes = ref({ active: 0, equipped: 0 })
const wearableOptions = ref<WearableOptions>({ patterns: [], properties: [], waves: [], limits: { nodes: 0, boxes: 0, depth: 0, channels: 0 } })
const cosmeticCategory = ref<CosmeticCategory>('cape')
const cosmeticCategories: { id: CosmeticCategory; label: string; singular: string; hint: string }[] = [
  { id: 'cape', label: 'Capes', singular: 'Cape', hint: 'Official capes everyone can equip.' },
  { id: 'trail', label: 'Trails', singular: 'Trail', hint: 'Particle trails that follow the player.' },
  { id: 'emote', label: 'Emotes', singular: 'Emote', hint: 'Animations players start from the emote wheel.' },
  { id: 'hat', label: 'Hats', singular: 'Hat', hint: 'Models worn on the head, hidden when a helmet is worn.' },
  { id: 'back', label: 'Back items', singular: 'Back item', hint: 'Wings, shields and other models on the back, hidden while gliding.' },
  { id: 'pet', label: 'Pets', singular: 'Pet', hint: 'Companions that float beside the player and follow them.' }
]
const activeCosmeticCategory = computed(() => cosmeticCategories.find((category) => category.id === cosmeticCategory.value) ?? cosmeticCategories[0])
const catalogItems = computed(() => cosmetics.value.filter((item) => item.type === cosmeticCategory.value))
const isWearable = computed(() => isWearableCategory(cosmeticForm.type))
const wearableKind = computed<WearableKind>(() => (isWearableCategory(cosmeticForm.type) ? cosmeticForm.type : 'hat'))
const parsedModel = computed<{ model: PreviewModel | null; error: string }>(() => {
  if (!isWearable.value) return { model: null, error: '' }
  try {
    const value = JSON.parse(cosmeticForm.data) as PreviewModel
    if (!value || !Array.isArray(value.nodes)) return { model: null, error: 'The model needs a "nodes" list' }
    return { model: value, error: '' }
  } catch (problem) {
    return { model: null, error: problem instanceof Error ? `JSON: ${problem.message}` : 'Invalid JSON' }
  }
})
const lastValidModel = ref<PreviewModel | null>(null)
watch(parsedModel, (value) => {
  if (value.model) lastValidModel.value = value.model
})
const previewModel = computed(() => parsedModel.value.model ?? lastValidModel.value)
const modelError = computed(() => parsedModel.value.error)

function categoryCount(category: CosmeticCategory): number {
  return cosmetics.value.filter((item) => item.type === category).length
}

function selectCosmeticCategory(category: CosmeticCategory) {
  cosmeticCategory.value = category
  editCosmetic(null)
}
const particles = ref<string[]>([])
const patterns = ref<string[]>([])
const parts = ref<string[]>([])
const auditLog = ref<AuditEntry[]>([])
const rules = ref<ServerRule[]>([])
const moduleIds = ref<string[]>([])
const ruleForm = reactive(emptyRule())

const newsPreview = computed(() => renderMarkdown(newsForm.body))
const statCards = computed(() => [
  { label: 'Players online', value: stats.value.online ?? 0 },
  { label: 'Known players', value: stats.value.players ?? 0 },
  { label: 'Active custom capes', value: stats.value.activeCapes ?? 0 },
  { label: 'Reported capes', value: stats.value.reportedCapes ?? 0 },
  { label: 'Published posts', value: stats.value.publishedNews ?? 0 },
  { label: 'Catalog cosmetics', value: stats.value.cosmetics ?? 0 }
])

function messageOf(caught: unknown): string {
  const data = (caught as { data?: { statusMessage?: string; message?: string } })?.data
  return data?.statusMessage || data?.message || (caught instanceof Error ? caught.message : 'Request failed')
}

async function api<T>(url: string, options: { method?: 'GET' | 'POST' | 'PUT' | 'DELETE'; body?: unknown } = {}): Promise<T> {
  error.value = ''
  try {
    return (await $fetch(url, {
      method: options.method ?? 'GET',
      body: options.body as Record<string, unknown> | undefined,
      headers: { 'X-Pretupid-Admin': '1' },
      credentials: 'same-origin'
    })) as T
  } catch (caught) {
    const status = (caught as { status?: number })?.status
    if (status === 401) {
      authenticated.value = false
    }
    error.value = messageOf(caught)
    throw caught
  }
}

function flash(text: string) {
  notice.value = text
  setTimeout(() => {
    if (notice.value === text) notice.value = ''
  }, 3000)
}

function formatDate(seconds: number | null | undefined): string {
  if (!seconds) return '—'
  return new Date(seconds * 1000).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' })
}

async function login() {
  busy.value = true
  try {
    await api('/api/admin/login', { method: 'POST', body: { key: adminKey.value } })
    adminKey.value = ''
    authenticated.value = true
    await selectTab('overview')
  } catch {
    void 0
  } finally {
    busy.value = false
  }
}

async function logout() {
  await api('/api/admin/logout', { method: 'POST' }).catch(() => undefined)
  authenticated.value = false
}

async function selectTab(next: Tab) {
  tab.value = next
  if (next === 'overview') stats.value = await api('/api/admin/stats')
  if (next === 'capes') await loadCapes(capeStatus.value, capePage.value)
  if (next === 'players') await loadPlayers()
  if (next === 'news') await loadNews()
  if (next === 'cosmetics') {
    await loadCosmetics()
    if (!cosmeticForm.existing) editCosmetic(null)
  }
  if (next === 'rules') await loadRules()
  if (next === 'audit') auditLog.value = (await api<{ entries: AuditEntry[] }>('/api/admin/audit')).entries
}

async function loadCapes(status: 'active' | 'removed', page: number) {
  const result = await api<{ capes: Cape[]; hasMore: boolean }>(`/api/admin/capes?status=${status}&page=${page}`)
  capeStatus.value = status
  capePage.value = page
  capes.value = result.capes
  capesHasMore.value = result.hasMore
}

async function removeCape(cape: Cape, banUploads: boolean) {
  const reason = window.prompt(`Reason for removing ${cape.name ?? 'this'}'s cape (shown to the player):`, 'Inappropriate content')
  if (reason === null) return
  await api(`/api/admin/capes/${cape.id}`, { method: 'POST', body: { action: 'remove', reason, banUploads } })
  flash('Cape removed')
  await loadCapes(capeStatus.value, capePage.value)
}

async function dismissReports(cape: Cape) {
  await api(`/api/admin/capes/${cape.id}`, { method: 'POST', body: { action: 'dismiss' } })
  flash('Reports dismissed')
  await loadCapes(capeStatus.value, capePage.value)
}

async function loadPlayers() {
  players.value = (await api<{ players: Player[] }>(`/api/admin/players?q=${encodeURIComponent(playerQuery.value)}`)).players
}

async function updatePlayer(player: Player, change: { uploadBanned?: boolean; banned?: boolean }) {
  if (change.banned && !window.confirm(`Suspend ${player.name} from all Pretupid services? Their custom cape is removed.`)) return
  await api(`/api/admin/players/${player.uuid}`, { method: 'POST', body: change })
  flash('Player updated')
  await loadPlayers()
}

async function loadNews() {
  news.value = (await api<{ posts: NewsPost[] }>('/api/admin/news')).posts
}

function editNews(post: NewsPost | null) {
  Object.assign(newsForm, post ? { ...post } : emptyNews())
}

async function saveNews() {
  busy.value = true
  try {
    const body = { title: newsForm.title, summary: newsForm.summary, body: newsForm.body, category: newsForm.category, pinned: newsForm.pinned, published: newsForm.published }
    const result = newsForm.id
      ? await api<{ id: string }>(`/api/admin/news/${newsForm.id}`, { method: 'PUT', body })
      : await api<{ id: string }>('/api/admin/news', { method: 'POST', body })
    newsForm.id = result.id
    flash('Post saved')
    await loadNews()
  } finally {
    busy.value = false
  }
}

async function deleteNews() {
  if (!newsForm.id || !window.confirm('Delete this post permanently?')) return
  await api(`/api/admin/news/${newsForm.id}`, { method: 'DELETE' })
  editNews(null)
  flash('Post deleted')
  await loadNews()
}

async function loadRules() {
  const result = await api<{ rules: ServerRule[]; modules: string[] }>('/api/admin/server-rules')
  rules.value = result.rules
  moduleIds.value = result.modules
}

function editRule(rule: ServerRule | null) {
  Object.assign(ruleForm, rule ? { ...rule, modules: [...rule.modules] } : emptyRule())
}

async function saveRule() {
  busy.value = true
  try {
    await api('/api/admin/server-rules', { method: 'POST', body: { pattern: ruleForm.pattern, reason: ruleForm.reason, modules: ruleForm.modules } })
    flash('Rule saved. Clients pick it up within a few hours.')
    await loadRules()
    const saved = rules.value.find((rule) => rule.pattern === ruleForm.pattern.trim().toLowerCase())
    if (saved) editRule(saved)
  } finally {
    busy.value = false
  }
}

async function deleteRule() {
  if (!ruleForm.id || !window.confirm(`Delete the rule for ${ruleForm.pattern}?`)) return
  await api(`/api/admin/server-rules/${ruleForm.id}`, { method: 'DELETE' })
  editRule(null)
  flash('Rule deleted')
  await loadRules()
}

async function loadCosmetics() {
  const result = await api<{
    cosmetics: Cosmetic[]
    customCapes: { active: number; equipped: number }
    particles: string[]
    patterns: string[]
    parts: string[]
    wearable: WearableOptions
  }>('/api/admin/cosmetics')
  cosmetics.value = result.cosmetics
  customCapes.value = result.customCapes
  wearableOptions.value = result.wearable
  particles.value = result.particles
  patterns.value = result.patterns
  parts.value = result.parts
}

function editCosmetic(item: Cosmetic | null) {
  if (!item) {
    Object.assign(cosmeticForm, emptyCosmetic())
    cosmeticForm.type = cosmeticCategory.value
    cosmeticForm.data = defaultCosmeticData(cosmeticForm.type)
    lastValidModel.value = null
    return
  }
  lastValidModel.value = null
  Object.assign(cosmeticForm, {
    existing: true,
    id: item.id,
    type: item.type,
    name: item.name,
    description: item.description,
    enabled: item.enabled,
    sort: item.sort,
    data: prettyJson(item.data),
    image: '',
    preview: item.imageUrl ?? '',
    removeImage: false
  })
}

function duplicateCosmetic() {
  const base = cosmeticForm.id.replace(/-copy(-\d+)?$/, '')
  let candidate = `${base}-copy`.slice(0, 32)
  let counter = 2
  while (cosmetics.value.some((item) => item.id === candidate)) {
    candidate = `${base}-copy-${counter++}`.slice(0, 32)
  }
  Object.assign(cosmeticForm, { existing: false, id: candidate, name: `${cosmeticForm.name} copy`.slice(0, 40), removeImage: false })
  if (cosmeticForm.preview && !cosmeticForm.image) {
    if (cosmeticForm.type === 'cape' || isWearableCategory(cosmeticForm.type)) {
      void fetch(cosmeticForm.preview)
        .then((response) => response.blob())
        .then(
          (blob) =>
            new Promise<void>((resolve) => {
              const reader = new FileReader()
              reader.onload = () => {
                cosmeticForm.image = String(reader.result)
                resolve()
              }
              reader.readAsDataURL(blob)
            })
        )
        .catch(() => undefined)
    }
  }
  flash('Copy ready — change the id and save to create it')
}

function clearTexture() {
  cosmeticForm.image = ''
  cosmeticForm.preview = ''
  cosmeticForm.removeImage = true
}

function pickImage(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 128 * 1024) {
    error.value = 'Image must be 128 KB or smaller'
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    cosmeticForm.image = String(reader.result)
    cosmeticForm.preview = String(reader.result)
    cosmeticForm.removeImage = false
    if (isWearableCategory(cosmeticForm.type)) {
      const image = new Image()
      image.onload = () => {
        try {
          const model = JSON.parse(cosmeticForm.data) as Record<string, unknown>
          model.textureWidth = image.naturalWidth
          model.textureHeight = image.naturalHeight
          cosmeticForm.data = prettyJson(model)
        } catch {
          return
        }
      }
      image.src = cosmeticForm.preview
    }
  }
  reader.readAsDataURL(file)
}

async function saveCosmetic() {
  let data: unknown = {}
  if (cosmeticForm.type !== 'cape') {
    try {
      data = JSON.parse(cosmeticForm.data)
    } catch {
      error.value = 'Data is not valid JSON'
      return
    }
  }
  busy.value = true
  try {
    const body = {
      id: cosmeticForm.id,
      type: cosmeticForm.type,
      name: cosmeticForm.name,
      description: cosmeticForm.description,
      enabled: cosmeticForm.enabled,
      sort: cosmeticForm.sort,
      data,
      image: cosmeticForm.image || undefined,
      removeImage: cosmeticForm.removeImage || undefined
    }
    if (cosmeticForm.existing) {
      await api(`/api/admin/cosmetics/${cosmeticForm.id}`, { method: 'PUT', body })
    } else {
      await api('/api/admin/cosmetics', { method: 'POST', body })
      cosmeticForm.existing = true
    }
    cosmeticForm.image = ''
    flash('Cosmetic saved — players see it within a minute, no update needed')
    await loadCosmetics()
  } finally {
    busy.value = false
  }
}

async function deleteCosmetic() {
  if (!window.confirm(`Delete ${cosmeticForm.name}? Players who equipped it lose it.`)) return
  await api(`/api/admin/cosmetics/${cosmeticForm.id}`, { method: 'DELETE' })
  editCosmetic(null)
  flash('Cosmetic deleted')
  await loadCosmetics()
}

function prettyJson(value: unknown): string {
  return JSON.stringify(value, null, 2).replace(/\[\s*(-?[\d.e+-]+(?:,\s*-?[\d.e+-]+)*)\s*\]/g, (_match, inner: string) => `[${inner.split(/,\s*/).join(', ')}]`)
}

function defaultCosmeticData(type: CosmeticCategory): string {
  if (isWearableCategory(type)) {
    return prettyJson(WEARABLE_TEMPLATES[type])
  }
  if (type === 'emote') {
    return JSON.stringify(
      { duration: 1.5, loop: false, ease: 'smooth', parts: { right_arm: [{ t: 0, x: 0, y: 0, z: 0 }, { t: 0.5, x: -160, y: 0, z: -20 }, { t: 1, x: 0, y: 0, z: 0 }] } },
      null,
      2
    )
  }
  return type === 'trail' ? emptyCosmetic().data : '{}'
}

onMounted(async () => {
  try {
    const session = await $fetch<{ authenticated: boolean }>('/api/admin/session', { credentials: 'same-origin' })
    authenticated.value = session.authenticated
    if (session.authenticated) await selectTab('overview')
  } catch {
    authenticated.value = false
  } finally {
    checking.value = false
  }
})
</script>

<style scoped lang="scss">
.admin {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.admin__center {
  padding: 4rem 0;
  text-align: center;
  color: var(--text-secondary);
}

.admin__login {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  width: min(420px, 100%);
  margin: 4rem auto;
}

.admin__title {
  margin: 0;
  font-family: var(--font-pixel);
  font-size: 1.6rem;
}

.admin__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
  justify-content: space-between;
}

.admin__layout {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  align-items: start;
  gap: 1.5rem;
}

.admin__nav {
  position: sticky;
  top: 1rem;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1rem;
  background: var(--bg-secondary);
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.admin__nav-group {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.admin__nav-label {
  padding: 0 0.6rem 0.25rem;
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-secondary);
}

.admin__tab {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.5rem 0.6rem;
  font: inherit;
  text-align: left;
  color: var(--text-secondary);
  background: none;
  border: none;
  border-radius: var(--edge-radius);
  cursor: pointer;

  &:hover {
    color: var(--text-primary);
    background: var(--bg-tertiary);
  }
}

.admin__tab--active {
  color: var(--text-primary);
  background: color-mix(in srgb, var(--accent) 20%, transparent);
  box-shadow: inset 3px 0 0 var(--accent);
}

.admin__badge {
  min-width: 1.4rem;
  padding: 0.05rem 0.4rem;
  font-size: 0.75rem;
  font-weight: 700;
  text-align: center;
  color: #fff;
  background: var(--danger);
  border-radius: 999px;
}

.admin__main {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  min-width: 0;
}

.admin__modules {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 0.35rem 0.75rem;
  max-height: 260px;
  overflow-y: auto;
  padding: 0.75rem;
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
}

@media (max-width: 760px) {
  .admin__layout {
    grid-template-columns: 1fr;
  }

  .admin__nav {
    position: static;
  }
}

.admin__muted {
  color: var(--text-secondary);
  font-size: 0.9rem;
}

.admin__mono {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  word-break: break-all;
}

.admin__error,
.admin__warning {
  color: var(--danger);
}

.admin__notice {
  color: var(--success);
}

.admin__online {
  margin-left: 0.4rem;
  color: var(--success);
  font-size: 0.8rem;
}

.admin__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 1rem;
}

.admin__stat {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  padding: 1.2rem;
  background: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.admin__stat-value {
  font-family: var(--font-pixel);
  font-size: 1.6rem;
}

.admin__section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.admin__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.admin__capes {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1rem;
}

.admin__cape {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 1rem;
  background: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
}

.admin__cape--reported {
  border-color: var(--danger);
}

.admin__cape-image {
  width: 100%;
  max-width: 256px;
  aspect-ratio: 2 / 1;
  image-rendering: pixelated;
  background: repeating-conic-gradient(var(--bg-tertiary) 0 25%, var(--bg-primary) 0 50%) 0 0 / 16px 16px;
  border: 1px solid var(--border-color);
}

.admin__cape-image--gone {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
}

.admin__cape-info {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.admin__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

.admin__table {
  width: 100%;
  border-collapse: collapse;

  th,
  td {
    padding: 0.6rem;
    text-align: left;
    vertical-align: top;
    border-bottom: 1px solid var(--border-color);
  }
}

.admin__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 0.75rem;
}

.admin__chip {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.4rem 0.8rem;
  border: 1px solid var(--border-color);
  border-radius: 999px;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  font: inherit;
  font-size: 0.85rem;
  cursor: pointer;

  &:hover {
    color: var(--text-primary);
  }
}

.admin__chip--active {
  border-color: var(--accent);
  color: var(--text-primary);
  background: color-mix(in srgb, var(--accent) 18%, var(--bg-tertiary));
}

.admin__chip-count {
  min-width: 1.4rem;
  padding: 0 0.35rem;
  border-radius: 999px;
  background: var(--bg-secondary);
  font-size: 0.75rem;
  text-align: center;
}

.admin__type {
  display: inline-flex;
  align-items: center;
  padding: 0 0.75rem;
  border: 1px solid var(--border-color);
  border-radius: var(--edge-radius);
  color: var(--text-secondary);
  white-space: nowrap;
}

.admin__list-item--media {
  flex-direction: row;
  align-items: center;
  gap: 0.75rem;
}

.admin__list-text {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.admin__cape-thumb {
  flex: none;
  width: 20px;
  height: 32px;
  background-repeat: no-repeat;
  background-size: 128px 64px;
  background-position: -2px -2px;
  image-rendering: pixelated;
  border: 1px solid var(--border-color);
}






.admin__split.admin__split--wide {
  grid-template-columns: minmax(200px, 260px) 1fr;
}

.admin__wearable-editor {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 320px);
  gap: 1rem;
  align-items: start;

  @media (max-width: 1000px) {
    grid-template-columns: 1fr;
  }
}

.admin__wearable-fields {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  min-width: 0;
}

.admin__texture {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.admin__texture-image {
  width: 128px;
  height: 128px;
  object-fit: contain;
  image-rendering: pixelated;
  background: repeating-conic-gradient(#3a3f4b 0% 25%, #2a2e37 0% 50%) 50% / 16px 16px;
  border: 1px solid var(--border-color);
}

.admin__field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 140px;
  font-size: 0.85rem;
  color: var(--text-secondary);
}

.admin__color {
  width: 100%;
  height: 36px;
  padding: 0;
  background: none;
  border: 1px solid var(--border-color);
  cursor: pointer;
}

.admin__help {
  font-size: 0.85rem;
  color: var(--text-secondary);

  summary {
    cursor: pointer;
    color: var(--text-primary);
  }

  p {
    margin: 0.4rem 0 0;
  }
}

.admin__textarea--tall {
  min-height: 340px;
}

.admin__split {
  display: grid;
  grid-template-columns: minmax(220px, 300px) 1fr;
  gap: 1.25rem;

  @media (max-width: 860px) {
    grid-template-columns: 1fr;
  }
}

.admin__list {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.admin__list-item {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  padding: 0.6rem 0.8rem;
  font: inherit;
  text-align: left;
  color: var(--text-primary);
  background: var(--bg-secondary);
  border: 2px solid var(--border-color);
  border-radius: var(--edge-radius);
  cursor: pointer;
}

.admin__list-item--active {
  border-color: var(--accent);
}

.admin__editor {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.admin__md {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;

  @media (max-width: 1000px) {
    grid-template-columns: 1fr;
  }
}

.admin__textarea {
  min-height: 320px;
  resize: vertical;
}

.admin__preview {
  min-height: 320px;
  max-height: 520px;
  overflow-y: auto;
  padding: 1rem;
  background: var(--bg-secondary);
  border: 2px dashed var(--border-color);
  border-radius: var(--edge-radius);
}

.admin__check {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.admin__narrow {
  width: 90px;
}
</style>
