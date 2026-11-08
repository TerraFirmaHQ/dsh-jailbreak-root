// Channel system prompt. null means never set. "" means the user cleared it.
// Those two are not the same, and neither is trimmed into the other.

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

/**
 * The selected route on one assemble call.
 * dsh 0.2.2-rc.1 passes `{ modelSelection, agent, scope, signal, toolSet }`
 * from `dsh-agent-loop` `preStep`. The route is `modelSelection.provider`.
 * A missing selection is null. Callers must not invent the default channel.
 */
export function providerIdOf(context) {
  const selected = context?.modelSelection?.provider
  if (typeof selected === 'string' && selected.length > 0) return selected
  const current = context?.agent?.model?.current
  if (typeof current === 'function') {
    let live
    try {
      live = current.call(context.agent.model)
    } catch {
      live = undefined
    }
    if (typeof live?.provider === 'string' && live.provider.length > 0) return live.provider
  }
  const configured = context?.agent?.options?.provider
  if (typeof configured === 'string' && configured.length > 0) return configured
  return null
}

/** Request-time frame. Null or "" stays unsent. The stored text is not trimmed. */
export function channelSystemText(channelText) {
  if (typeof channelText !== 'string' || channelText.length === 0) return null
  return '<system-reminder>\n' + channelText + '\n</system-reminder>'
}

/**
 * The only system section for this request. Tool schemas are not sections.
 * Does not edit [sections]. Does not add a source label.
 */
export function rewriteSections(sections, channelText) {
  const text = channelSystemText(channelText)
  if (text == null) return []
  return [{ name: 'dsh:channel-prompt', text, interpolate: false }]
}

/**
 * Outbound copy. [history] is not modified.
 * Only a system row whose text is exactly [replacedGlobal] is left out.
 * Other system rows, including a user note or a tool instruction, stay.
 */
export function projectRequest(history, systemText, api, replacedGlobal) {
  const rest = []
  const source = Array.isArray(history) ? history : []
  for (const message of source) {
    if (message != null && message.role === 'system' && typeof replacedGlobal === 'string' && message.content === replacedGlobal) continue
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
