export default defineEventHandler(async (event) => {
  setResponseHeader(event, 'Cache-Control', 'public, max-age=300')
  const rules = await listServerRules(event)
  return { rules: rules.map(({ pattern, modules, reason }) => ({ pattern, modules, reason })) }
})
