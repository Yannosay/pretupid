interface StageRange {
  match: RegExp
  from: number
  to: number
  label: string
}

const STAGES: StageRange[] = [
  { match: /^(Getting ready|Resolving Minecraft|Restoring )/, from: 0, to: 4, label: 'Getting ready' },
  { match: /^Installing Java/, from: 4, to: 18, label: 'Installing Java' },
  { match: /^Downloading Minecraft/, from: 18, to: 30, label: 'Downloading Minecraft' },
  { match: /^(Preparing |Downloading .* installer libraries)/, from: 30, to: 36, label: 'Preparing the mod loader' },
  { match: /^(Checking libraries|Downloading .*libraries)/, from: 36, to: 54, label: 'Downloading libraries' },
  { match: /^(Checking assets|Downloading assets)/, from: 54, to: 94, label: 'Downloading sounds and textures' },
  { match: /^(Preparing game files|Starting Minecraft)/, from: 94, to: 100, label: 'Starting Minecraft' }
]

export interface OverallProgress {
  percent: number
  stage: string
  detail: string
}

function stageFor(message: string): StageRange | null {
  return STAGES.find((stage) => stage.match.test(message)) ?? null
}

export function overallProgress(message: string, current: number, total: number, previous: number): OverallProgress {
  const stage = stageFor(message)
  const fraction = total > 0 ? Math.min(1, Math.max(0, current / total)) : 0
  const raw = stage ? stage.from + (stage.to - stage.from) * fraction : previous
  const detail = message.match(/\(([^)]*)\)\s*$/)?.[1] ?? ''
  return {
    percent: Math.min(100, Math.max(previous, Math.round(raw))),
    stage: stage?.label ?? message.replace(/\s*\([^)]*\)\s*$/, ''),
    detail
  }
}
