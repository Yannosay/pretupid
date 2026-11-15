export default defineEventHandler(async (event) => {
  await requireAdmin(event)
  return { rules: await listServerRules(event), modules: CLIENT_MODULE_IDS }
})
