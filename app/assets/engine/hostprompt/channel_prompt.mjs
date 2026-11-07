// Channel system prompt. null means never set. "" means the user cleared it.
// Those two are not the same, and neither is trimmed into the other.

export const HARNESS_MARK = 'You are an AI agent powered by DeepSeek Harness.'

const DROPPED = new Set(['dsh:builtin-prompt', 'deployment:persona-prefix', 'identity'])

export function effectiveChannelPrompt(systemPrompt, useBuiltinPrompt, defaultText) {
  if (systemPrompt === null || systemPrompt === undefined) {
    return useBuiltinPrompt === false ? null : defaultText
  }
  if (typeof systemPrompt !== 'string' || systemPrompt.length === 0) return null
  return systemPrompt
}

export function recordFor(policy, id) {
  if (typeof id !== 'string' || id.length === 0 || policy == null || policy.channels == null) {
    return { systemPrompt: null, useBuiltinPrompt: true, api: null }
  }
  const row = policy.channels[id]
  if (row == null || typeof row !== 'object') {
    return { systemPrompt: null, useBuiltinPrompt: true, api: null }
  }
  const hasText = Object.prototype.hasOwnProperty.call(row, 'systemPrompt')
  const systemPrompt = hasText && (row.systemPrompt === null || typeof row.systemPrompt === 'string')
    ? row.systemPrompt
    : null
  const useBuiltinPrompt = Object.prototype.hasOwnProperty.call(row, 'useBuiltinPrompt')
    ? row.useBuiltinPrompt === true
    : true
  const api = typeof row.api === 'string' ? row.api : null
  return { systemPrompt, useBuiltinPrompt, api }
}

export function readPolicy(text) {
  if (typeof text !== 'string' || text.length === 0) return { channels: {} }
  try {
    const parsed = JSON.parse(text)
    if (parsed == null || typeof parsed !== 'object' || parsed.channels == null) return { channels: {} }
    return { channels: parsed.channels }
  } catch {
    return { channels: {} }
  }
}

export function providerIdOf(context, assembly) {
  const queue = []
  if (context != null) queue.push(context)
  if (assembly != null) queue.push(assembly)
  const seen = new Set()
  let depth = 0
  while (queue.length > 0 && depth < 8) {
    const cur = queue.shift()
    depth += 1
    if (cur == null || typeof cur !== 'object' || seen.has(cur)) continue
    seen.add(cur)
    if (typeof cur.providerId === 'string' && cur.providerId.length > 0) return cur.providerId
    if (typeof cur.provider === 'string' && cur.provider.length > 0) return cur.provider
    for (const key of ['agent', 'session', 'model', 'selection', 'request']) {
      if (cur[key] != null) queue.push(cur[key])
    }
  }
  return null
}

function keepSection(section) {
  if (section == null || typeof section.text !== 'string' || section.text.length === 0) return false
  if (DROPPED.has(section.name)) return false
  if (section.name === 'dsh:persona' || section.name === 'dsh:channel-prompt') return true
  if (section.text.startsWith(HARNESS_MARK)) return false
  return true
}

/** New section list. Does not edit [sections] or anything inside it. */
export function rewriteSections(sections, channelText, api) {
  const kept = []
  const source = Array.isArray(sections) ? sections : []
  for (const section of source) {
    if (!keepSection(section)) continue
    kept.push({ name: section.name, text: section.text })
  }
  if (typeof channelText === 'string' && channelText.length > 0) {
    kept.unshift({ name: 'dsh:channel-prompt', text: channelText })
  }
  if (api === 'openai-completions') {
    const text = kept.map((section) => section.text).join('\n\n')
    return text.length === 0 ? [] : [{ name: 'dsh:system', text }]
  }
  return kept
}

/** Outbound copy. [history] is not modified. Old system rows are not sent again. */
export function projectRequest(history, systemText, api) {
  const rest = []
  const source = Array.isArray(history) ? history : []
  for (const message of source) {
    if (message != null && message.role === 'system') continue
    rest.push(message)
  }
  const send = typeof systemText === 'string' && systemText.length > 0
  if (api === 'anthropic-messages') {
    return { system: send ? systemText : undefined, messages: rest }
  }
  if (api === 'openai-responses') {
    return { instructions: send ? systemText : undefined, input: rest }
  }
  const messages = send ? [{ role: 'system', content: systemText }, ...rest] : rest
  return { messages }
}
