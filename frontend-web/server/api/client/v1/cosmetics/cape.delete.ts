export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  await db(event).prepare("UPDATE capes SET status = 'replaced' WHERE uuid = ? AND status = 'active'").bind(player.uuid).run()
  setResponseStatus(event, 204)
  return null
})
