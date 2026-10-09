/**
 * ES 搜索高亮渲染：检索命中时后端字段里带 <em> 命中标记（PostIndexService/GoodsSearchService）。
 *
 * 安全策略：先整体转义 HTML（用户内容里的任何标签都变成纯文本，防 XSS），
 * 再仅把转义后的 &lt;em&gt;/&lt;/em&gt; 还原为真实标签——最终能渲染的标签只有 <em>，
 * 由调用方 CSS 上色。可选 maxLen 截断（截断可能落在标签中间，做去半截/补闭合处理）。
 */
export function highlightHtml(text, maxLen) {
  if (text == null) return ''
  let s = String(text)
  if (maxLen != null && s.length > maxLen) {
    s = s.substring(0, maxLen)
    // 丢弃被截断的半个标签（如 "<em" / "</e"），再补齐未闭合的 <em>
    s = s.replace(/<(\/?[^>]*)?$/, '')
    const opens = (s.match(/<em>/g) || []).length
    const closes = (s.match(/<\/em>/g) || []).length
    if (opens > closes) s += '</em>'.repeat(opens - closes)
  }
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/&lt;em&gt;/g, '<em>')
    .replace(/&lt;\/em&gt;/g, '</em>')
}
