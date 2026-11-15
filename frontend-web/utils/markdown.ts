function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function safeUrl(raw: string): string | null {
  try {
    const url = new URL(raw)
    return url.protocol === 'https:' || url.protocol === 'http:' ? url.toString() : null
  } catch {
    return null
  }
}

function inline(text: string): string {
  const codes: string[] = []
  let output = escapeHtml(text).replace(/`([^`]+)`/g, (_, code: string) => {
    codes.push(`<code>${code}</code>`)
    return `\u0000${codes.length - 1}\u0000`
  })
  output = output.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (match, label: string, href: string) => {
    const url = safeUrl(href.replace(/&amp;/g, '&'))
    return url ? `<a href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer nofollow">${label}</a>` : match
  })
  output = output
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*]+)\*/g, '$1<em>$2</em>')
    .replace(/~~([^~]+)~~/g, '<del>$1</del>')
  return output.replace(/\u0000(\d+)\u0000/g, (_, index: string) => codes[Number(index)])
}

export function renderMarkdown(source: string): string {
  const lines = source.replace(/\r\n?/g, '\n').split('\n')
  const html: string[] = []
  let paragraph: string[] = []
  let list: { ordered: boolean; items: string[] } | null = null
  let quote: string[] = []
  let code: string[] | null = null

  const flushParagraph = () => {
    if (paragraph.length > 0) {
      html.push(`<p>${paragraph.map(inline).join('<br>')}</p>`)
      paragraph = []
    }
  }
  const flushList = () => {
    if (list) {
      const tag = list.ordered ? 'ol' : 'ul'
      html.push(`<${tag}>${list.items.map((item) => `<li>${inline(item)}</li>`).join('')}</${tag}>`)
      list = null
    }
  }
  const flushQuote = () => {
    if (quote.length > 0) {
      html.push(`<blockquote>${quote.map(inline).join('<br>')}</blockquote>`)
      quote = []
    }
  }
  const flushAll = () => {
    flushParagraph()
    flushList()
    flushQuote()
  }

  for (const line of lines) {
    if (code) {
      if (line.trim().startsWith('```')) {
        html.push(`<pre><code>${escapeHtml(code.join('\n'))}</code></pre>`)
        code = null
      } else {
        code.push(line)
      }
      continue
    }
    const trimmed = line.trim()
    if (trimmed.startsWith('```')) {
      flushAll()
      code = []
      continue
    }
    if (trimmed === '') {
      flushAll()
      continue
    }
    const button = /^\[\[([^\]]{1,48})\]\]\(([^)\s]+)\)$/.exec(trimmed)
    if (button) {
      const url = safeUrl(button[2])
      if (url) {
        flushAll()
        html.push(`<p class="markdown__actions"><a class="markdown__button" href="${escapeHtml(url)}" target="_blank" rel="noopener noreferrer nofollow">${escapeHtml(button[1])}</a></p>`)
        continue
      }
    }
    const image = /^!\[([^\]]*)\]\(([^)\s]+)\)$/.exec(trimmed)
    if (image) {
      const url = safeUrl(image[2])
      if (url && url.startsWith('https://')) {
        flushAll()
        html.push(`<figure class="markdown__figure"><img src="${escapeHtml(url)}" alt="${escapeHtml(image[1])}" loading="lazy" decoding="async" referrerpolicy="no-referrer"></figure>`)
        continue
      }
    }
    const heading = /^(#{1,3})\s+(.+)$/.exec(trimmed)
    if (heading) {
      flushAll()
      const level = heading[1].length + 1
      html.push(`<h${level}>${inline(heading[2])}</h${level}>`)
      continue
    }
    if (/^(-{3,}|\*{3,})$/.test(trimmed)) {
      flushAll()
      html.push('<hr>')
      continue
    }
    const bullet = /^[-*]\s+(.+)$/.exec(trimmed)
    const numbered = /^\d+[.)]\s+(.+)$/.exec(trimmed)
    if (bullet || numbered) {
      flushParagraph()
      flushQuote()
      const ordered = Boolean(numbered)
      if (!list || list.ordered !== ordered) {
        flushList()
        list = { ordered, items: [] }
      }
      list.items.push((bullet ?? numbered)![1])
      continue
    }
    if (trimmed.startsWith('>')) {
      flushParagraph()
      flushList()
      quote.push(trimmed.replace(/^>\s?/, ''))
      continue
    }
    flushList()
    flushQuote()
    paragraph.push(trimmed)
  }
  if (code) {
    html.push(`<pre><code>${escapeHtml((code as string[]).join('\n'))}</code></pre>`)
  }
  flushAll()
  return html.join('\n')
}
