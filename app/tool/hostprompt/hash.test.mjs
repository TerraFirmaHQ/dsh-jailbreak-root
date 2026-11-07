import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { test } from 'node:test'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const prompt = readFileSync(join(here, '../../assets/engine/hostprompt/prompt.md'))
const plugin = readFileSync(join(here, '../../assets/engine/hostprompt/index.mjs'), 'utf8')

test('prompt bytes are the 4.1 file', () => {
  assert.equal(prompt.length, 21403)
  assert.equal(createHash('sha256').update(prompt).digest('hex'), '4fbd3a104fcd16065800e4e730a67f7781698300eec33af8dc67cfd4f328c118')
})

test('plugin places that text first and does not interpolate it', () => {
  assert.match(plugin, /SECTION_ORDER = -2000/)
  assert.match(plugin, /interpolate: false/)
  assert.match(plugin, /dsh:builtin-prompt/)
})
