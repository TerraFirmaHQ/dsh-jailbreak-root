// SPDX-License-Identifier: AGPL-3.0-only
//
// dsh Rony fork (v5.1): 人设自定义 + 活人感.
//
// Mounted by absolute path with its own `--patch` overlay ONLY while 活人感 is on (default on).
// When the owner turns 活人感 off the app removes the overlay, so none of this text reaches the
// model: the system prompt is back to 破甲 + stock DSH.
//
// One global system-prompt section `rony:persona` at order -1500: after 破甲 (-2000), before the
// harness identity (-1000) and every preset/tool section. Text = the owner's persona
// (persona.md, written by the app from 设置 → 人设自定义; the bundled default.md when empty)
// followed by the 活人感 rules (rules.md). Registered with `interpolate: false`.

import { existsSync, readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

export const name = 'rony-persona'
export const inject = ['systemPrompt']

export const SECTION_NAME = 'rony:persona'
export const SECTION_ORDER = -1500

const here = dirname(fileURLToPath(import.meta.url))

const read = (file) => (existsSync(file) ? readFileSync(file, 'utf8').replace(/^\uFEFF/, '').replace(/\r\n?/g, '\n').trim() : '')

/** Persona (owner's or default) + 活人感 rules, as one block. */
export function loadText(dir = here) {
  const persona = read(join(dir, 'persona.md')) || read(join(dir, 'default.md'))
  const rules = read(join(dir, 'rules.md'))
  return [persona && `# 人设\n\n${persona}`, rules].filter(Boolean).join('\n\n')
}

export function apply(ctx, config = {}) {
  const text = loadText(config.dir ?? here)
  if (text.length === 0) return
  ctx.effect(() => ctx.systemPrompt.section({
    name: SECTION_NAME,
    order: SECTION_ORDER,
    text,
    interpolate: false,
  }), 'rony-persona.section()')
}
