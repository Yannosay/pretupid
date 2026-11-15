export default defineEventHandler(async (event) => {
  setResponseHeader(event, 'Cache-Control', 'public, max-age=60')
  return { posts: await publishedNews(event, 50, false) }
})
