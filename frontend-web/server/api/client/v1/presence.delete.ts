export default defineEventHandler(async (event) => {
  noStore(event)
  const player = await requirePlayer(event)
  await db(event).prepare('UPDATE players SET online = 0, server_address = NULL, emote_id = NULL, emote_at = NULL WHERE uuid = ?').bind(player.uuid).run()
  setResponseStatus(event, 204)
  return null
})
