import { defineStore } from 'pinia'
import { ref } from 'vue'
import { installPerformancePack, installPretupidClient } from '@/api'
import { useNotificationsStore } from '@/stores/notifications'
import type { Instance } from '@/types'

export type SetupTask = 'client' | 'performance'

export const useSetupStore = defineStore('setup', () => {
  const pending = ref<Record<string, SetupTask[]>>({})
  const revisions = ref<Record<string, number>>({})

  function tasksFor(instanceId: string): SetupTask[] {
    return pending.value[instanceId] ?? []
  }

  function isPending(instanceId: string, task?: SetupTask): boolean {
    const tasks = tasksFor(instanceId)
    return task ? tasks.includes(task) : tasks.length > 0
  }

  function revision(instanceId: string): number {
    return revisions.value[instanceId] ?? 0
  }

  function finish(instanceId: string, task: SetupTask) {
    const remaining = tasksFor(instanceId).filter((entry) => entry !== task)
    const next = { ...pending.value }
    if (remaining.length > 0) next[instanceId] = remaining
    else delete next[instanceId]
    pending.value = next
    revisions.value = { ...revisions.value, [instanceId]: revision(instanceId) + 1 }
  }

  async function runExtras(instance: Instance, client: boolean, performance: boolean) {
    const tasks: SetupTask[] = []
    if (performance) tasks.push('performance')
    if (client) tasks.push('client')
    if (tasks.length === 0) return
    pending.value = { ...pending.value, [instance.id]: tasks }
    const notifications = useNotificationsStore()

    if (performance) {
      try {
        const result = await installPerformancePack(instance.id)
        if (result.installed.length > 0) {
          notifications.notifySuccess('Performance mods added', result.installed.map((m) => m.title).join(', '))
        }
      } catch (e) {
        notifications.notifyWarning('Performance mods not installed', String(e))
      } finally {
        finish(instance.id, 'performance')
      }
    }

    if (client) {
      try {
        await installPretupidClient(instance.id)
        notifications.notifySuccess('Pretupid Client added', `Press Right Shift in ${instance.name} to open the menu.`)
      } catch (e) {
        notifications.notifyWarning('Pretupid Client not installed', String(e))
      } finally {
        finish(instance.id, 'client')
      }
    }
  }

  return { pending, tasksFor, isPending, revision, runExtras }
})
