// SPDX-License-Identifier: AGPL-3.0-only
//
// Default channel prompt. prompt.md is the source of that text and is not
// rewritten. A channel that has never set a prompt, with the builtin switch
// on, uses those exact bytes. A cleared channel or a switch that is off with
// no saved text sends no channel prompt. The assemble hook replaces this
// section for the request being built. It does not edit session history.
// The editable persona section stays. Tool sections stay.

import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { effectiveChannelPrompt, providerIdOf, readPolicy, recordFor, rewriteSections } from './channel_prompt.mjs'

export const name = 'dsh-builtin-prompt'
export const inject = ['systemPrompt']
export const SECTION_NAME = 'dsh:builtin-prompt'
export const SECTION_ORDER = -2000

const here = dirname(fileURLToPath(import.meta.url))

/** Exact file bytes. Not trimmed, not re-newline'd. */
export function loadPrompt(file = join(here, 'prompt.md')) {
  return new TextDecoder('utf-8', { fatal: false }).decode(readFileSync(file))
}

export function apply(ctx) {
  const text = loadPrompt()
  if (text.length === 0) return
  ctx.effect(() => ctx.systemPrompt.section({
    name: SECTION_NAME,
    order: SECTION_ORDER,
    text,
    interpolate: false,
  }), 'dsh-builtin-prompt.section()')
  ctx.on('system-prompt/assemble', async (_assembly, context, next) => {
    const value = await next()
    let policy = { channels: {} }
    try {
      policy = readPolicy(readFileSync(join(here, 'policy.json'), 'utf8'))
    } catch {
      policy = { channels: {} }
    }
    const id = providerIdOf(context, value)
    const record = recordFor(policy, id)
    const channel = effectiveChannelPrompt(record.systemPrompt, record.useBuiltinPrompt, text)
    const sections = rewriteSections(value?.sections, channel, record.api)
    return { ...value, sections }
  })
}
