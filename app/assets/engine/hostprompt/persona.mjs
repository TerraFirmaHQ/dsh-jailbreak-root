// SPDX-License-Identifier: AGPL-3.0-only
//
// Optional persona. Default is blank: an empty persona.md registers no section.
// This is not a coordinator and it does not replace the locked built-in prompt.

import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

export const name = 'dsh-persona'
export const inject = ['systemPrompt']
export const SECTION_NAME = 'dsh:persona'
export const SECTION_ORDER = -1500

const here = dirname(fileURLToPath(import.meta.url))

export function loadPersona(file = join(here, 'persona.md')) {
  return readFileSync(file, 'utf8').replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n').trim()
}

export function apply(ctx) {
  const text = loadPersona()
  if (text.length === 0) return
  ctx.effect(() => ctx.systemPrompt.section({
    name: SECTION_NAME,
    order: SECTION_ORDER,
    text,
    interpolate: false,
  }), 'dsh-persona.section()')
}
