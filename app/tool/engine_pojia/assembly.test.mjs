// SPDX-License-Identifier: AGPL-3.0-only
//
// dsh Rony fork: the bundled 破甲 prompt plugin (app/assets/engine/pojia/) against DSH's real
// prompt registry (@deepseek-ai/dsh-system-prompt + cordis from the runtime's DSH install).
// Checks that the prompt is the first section of the assembled system prompt, in full, ahead
// of the harness identity, the deployment persona, a preset's own persona and tool sections.
//
//   DSH_NODE_MODULES=runtime/.work/arm64-v8a/dsh-prefix/node_modules node --test app/tool/engine_pojia/assembly.test.mjs
//
// Skipped when no DSH install is found.

import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, join, resolve } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath, pathToFileURL } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const repo = resolve(here, '../../..')
const assetDir = resolve(here, '../../assets/engine/pojia')
const nodeModules = process.env.DSH_NODE_MODULES ?? join(repo, 'runtime/.work/arm64-v8a/dsh-prefix/node_modules')
const haveDsh = existsSync(join(nodeModules, '@deepseek-ai/dsh-system-prompt/lib/index.js'))

/** sha256 of the 破甲 file as downloaded from Drive (DeepSeek-4.1-Flash-破甲提示词.md). */
const EXPECTED_SHA256 = '4fbd3a104fcd16065800e4e730a67f7781698300eec33af8dc67cfd4f328c118'

const mod = (p) => import(pathToFileURL(join(nodeModules, p)).href)

test('the asset is the downloaded file, unchanged', () => {
  const bytes = readFileSync(join(assetDir, 'prompt.md'))
  assert.ok(bytes.length > 0)
  assert.equal(createHash('sha256').update(bytes).digest('hex'), EXPECTED_SHA256)
})

test('the prompt is the first system-prompt section, in full', { skip: !haveDsh && 'no DSH install' }, async () => {
  const { Context } = await mod('@deepseek-ai/cordis/lib/index.js')
  const { SystemPrompt, renderPrompt, PERSONA_PREFIX_SECTION } = await mod('@deepseek-ai/dsh-system-prompt/lib/index.js')
  const pojia = await import(pathToFileURL(join(assetDir, 'index.mjs')).href)

  const ctx = new Context()
  ctx.plugin(SystemPrompt, { personaPrefix: 'You are a coding agent powered by the {{model}} model.' })
  ctx.plugin(pojia)
  // A tool's own section and the model variable, as the engine registers them.
  ctx.plugin({
    name: 'fake-tool',
    inject: ['systemPrompt'],
    apply(c) {
      c.effect(() => c.systemPrompt.section({ name: 'tool:bash', order: c.systemPrompt.getSectionOrder('TOOL_BASH'), text: 'BASH TOOL RULES' }))
      c.effect(() => c.systemPrompt.section({ name: 'rony:root-mcp', order: c.systemPrompt.getSectionOrder('MCP_SERVERS'), text: 'mcp__root__* RULES' }))
      c.effect(() => c.systemPrompt.variable('model', () => 'deepseek-v4-flash'))
    },
  })
  await new Promise((r) => setTimeout(r, 50))

  const assembly = await ctx.systemPrompt.assemble({})
  const names = assembly.sections.map((s) => s.name)
  assert.equal(names[0], pojia.SECTION_NAME, `first section: ${names.join(', ')}`)
  assert.ok(names.indexOf('harness:identity') > 0)
  assert.ok(names.indexOf(PERSONA_PREFIX_SECTION) > 0)
  assert.ok(names.indexOf('tool:bash') > names.indexOf(PERSONA_PREFIX_SECTION))

  const rendered = renderPrompt(assembly)
  const expected = pojia.loadPrompt()
  assert.ok(expected.length > 1000)
  assert.ok(rendered.startsWith(expected), 'rendered prompt starts with the whole 破甲 text')
  assert.ok(rendered.includes('powered by the deepseek-v4-flash model'))
  assert.ok(rendered.indexOf('BASH TOOL RULES') > expected.length)
  assert.ok(rendered.indexOf('mcp__root__* RULES') > expected.length)
  ctx.registry?.delete?.(pojia)
})

test('it is registered as literal text under its own name (a preset persona only replaces deployment:persona-prefix)', { skip: !haveDsh && 'no DSH install' }, async () => {
  const { Context } = await mod('@deepseek-ai/cordis/lib/index.js')
  const { SystemPrompt, renderPrompt } = await mod('@deepseek-ai/dsh-system-prompt/lib/index.js')
  const pojia = await import(pathToFileURL(join(assetDir, 'index.mjs')).href)
  const ctx = new Context()
  ctx.plugin(SystemPrompt, { personaPrefix: 'GLOBAL PERSONA' })
  ctx.plugin(pojia)
  await new Promise((r) => setTimeout(r, 50))
  const sections = new Map((await ctx.systemPrompt.assemble({})).sections.map((s) => [s.name, s]))
  assert.ok(sections.has(pojia.SECTION_NAME))
  assert.equal(sections.get(pojia.SECTION_NAME).interpolate, false)
  assert.ok(renderPrompt({ sections: [...sections.values()], variables: {} }).startsWith(pojia.loadPrompt()))
})
