// SPDX-License-Identifier: AGPL-3.0-only
//
// Locked built-in prompt. The bytes are the owner's DeepSeek-4.1-Flash prompt,
// shipped as prompt.md and registered as the first system-prompt section.
// interpolate is false so the text is sent in full and {{…}} cannot break assembly.
// A preset persona only replaces deployment:persona-prefix; it does not move this section.

import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

export const name = 'dsh-builtin-prompt'
export const inject = ['systemPrompt']
export const SECTION_NAME = 'dsh:builtin-prompt'
export const SECTION_ORDER = -2000

const here = dirname(fileURLToPath(import.meta.url))

/** Prompt bytes as shipped, BOM dropped, newlines folded, outer blank lines trimmed. */
export function loadPrompt(file = join(here, 'prompt.md')) {
  return readFileSync(file, 'utf8').replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n').trim()
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
}
