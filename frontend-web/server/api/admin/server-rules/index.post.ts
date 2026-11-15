export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  const input = parseServerRuleInput(await readJson<Record<string, unknown>>(event, 8 * 1024))
  const now = nowSeconds()
  await db(event)
    .prepare(
      `INSERT INTO server_rules (pattern, modules, reason, created_at, updated_at) VALUES (?1, ?2, ?3, ?4, ?4)
       ON CONFLICT(pattern) DO UPDATE SET modules = ?2, reason = ?3, updated_at = ?4`
    )
    .bind(input.pattern, JSON.stringify(input.modules), input.reason, now)
    .run()
  await audit(event, 'server_rule.save', input.pattern, input.modules.join(', '))
  return { ok: true }
})
