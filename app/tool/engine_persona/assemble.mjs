// SPDX-License-Identifier: AGPL-3.0-only
//
// dsh Rony fork v5.1: assemble the system prompt like the engine (破甲 always unless off; the
// persona plugin only while 活人感 is on) with DSH's real prompt registry. Shared by
// assembly.test.mjs and live-deepseek.mjs.

import { existsSync, mkdtempSync, copyFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repo = resolve(here, '../../..')
export const engine = resolve(here, '../../assets/engine')
const nodeModules = process.env.DSH_NODE_MODULES ?? join(repo, 'runtime/.work/arm64-v8a/dsh-prefix/node_modules')
export const haveDsh = existsSync(join(nodeModules, '@deepseek-ai/dsh-system-prompt/lib/index.js'))
const mod = (p) => import(pathToFileURL(join(nodeModules, p)).href)

/** Assemble like the engine: 破甲 always, persona only when [humanlike]. */
export async function assemble({ humanlike, personaText = '', pojia = true } = {}) {
  const { Context } = await mod('@deepseek-ai/cordis/lib/index.js')
  const { SystemPrompt, renderPrompt } = await mod('@deepseek-ai/dsh-system-prompt/lib/index.js')
  const ctx = new Context()
  ctx.plugin(SystemPrompt, { personaPrefix: 'You are a coding agent powered by the {{model}} model.' })
  if (pojia) ctx.plugin(await import(pathToFileURL(join(engine, 'pojia/index.mjs')).href))
  if (humanlike) {
    // The guest dir the app writes: plugin + default + rules + the owner's persona.md.
    const dir = mkdtempSync(join(tmpdir(), 'rony-persona-'))
    for (const f of ['index.mjs', 'default.md', 'rules.md']) copyFileSync(join(engine, 'persona', f), join(dir, f))
    writeFileSync(join(dir, 'persona.md'), personaText.trim() || '\n')
    ctx.plugin(await import(pathToFileURL(join(dir, 'index.mjs')).href))
  }
  ctx.plugin({ name: 'fake-tool', inject: ['systemPrompt'], apply(c) {
    c.effect(() => c.systemPrompt.section({ name: 'tool:bash', order: c.systemPrompt.getSectionOrder('TOOL_BASH'), text: 'BASH TOOL RULES' }))
    c.effect(() => c.systemPrompt.variable('model', () => 'deepseek-v4-flash'))
  } })
  await new Promise((r) => setTimeout(r, 50))
  const assembly = await ctx.systemPrompt.assemble({})
  return { names: assembly.sections.map((s) => s.name), text: renderPrompt(assembly) }
}

