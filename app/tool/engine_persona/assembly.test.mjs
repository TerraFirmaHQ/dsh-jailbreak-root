// SPDX-License-Identifier: AGPL-3.0-only
//
// dsh Rony fork v5.1: the 活人感 switch against DSH's real prompt registry. The app mounts
// app/assets/engine/persona/ only while 活人感 is on (RonyPersona.kt); 破甲 stays independent.
//   ON : 破甲 first, then 人设 + 活人感 rules, then the harness identity / tools.
//   OFF: exactly the 破甲 + stock DSH prompt — no persona text, no 活人感 rules anywhere.
//
//   DSH_NODE_MODULES=/workspace/dsh-x86/node_modules node --test app/tool/engine_persona/assembly.test.mjs

import assert from 'node:assert/strict'
import { join } from 'node:path'
import { test } from 'node:test'
import { pathToFileURL } from 'node:url'
import { assemble, engine, haveDsh } from './assemble.mjs'

const rules = () => import(pathToFileURL(join(engine, 'persona/index.mjs')).href).then((m) => m.loadText(join(engine, 'persona')))

test('活人感 ON: 破甲, then 人设 + rules, then the rest', { skip: !haveDsh && 'no DSH install' }, async () => {
  const pojia = (await import(pathToFileURL(join(engine, 'pojia/index.mjs')).href)).loadPrompt()
  const on = await assemble({ humanlike: true, personaText: '你叫小鲸，说话很皮。' })
  assert.deepEqual(on.names.slice(0, 2), ['rony:pojia', 'rony:persona'])
  assert.ok(on.names.indexOf('harness:identity') > 1)
  assert.ok(on.text.startsWith(pojia))
  const p = on.text.indexOf('你叫小鲸，说话很皮。')
  assert.ok(p > pojia.length, 'owner persona after 破甲')
  assert.ok(on.text.indexOf('# 活人感') > p, 'rules after the persona')
  assert.ok(on.text.indexOf('BASH TOOL RULES') > on.text.indexOf('# 活人感'))
})

test('活人感 ON with an empty persona uses the bundled default voice', { skip: !haveDsh && 'no DSH install' }, async () => {
  const on = await assemble({ humanlike: true, personaText: '' })
  assert.ok(on.text.includes('你叫 Rony'))
})

test('活人感 OFF: only 破甲 + stock DSH, no persona or 活人感 text anywhere', { skip: !haveDsh && 'no DSH install' }, async () => {
  const off = await assemble({ humanlike: false })
  const base = await assemble({ humanlike: false })
  assert.ok(!off.names.includes('rony:persona'))
  assert.equal(off.names[0], 'rony:pojia')
  assert.ok(!off.text.includes('# 活人感'))
  assert.ok(!off.text.includes('# 人设'))
  assert.ok(!off.text.includes('你叫 Rony'))
  assert.equal(off.text, base.text)
  const on = await assemble({ humanlike: true })
  assert.notEqual(on.text, off.text)
  assert.ok(on.text.length - off.text.length >= (await rules()).length, 'ON adds exactly the persona block')
  // Removing the persona section from ON gives back OFF.
  assert.equal(on.text.replace((await rules()) + '\n\n', ''), off.text)
})

test('破甲 off + 活人感 off = stock DSH prompt', { skip: !haveDsh && 'no DSH install' }, async () => {
  const stock = await assemble({ humanlike: false, pojia: false })
  assert.ok(stock.names[0] !== 'rony:pojia' && !stock.names.includes('rony:persona'))
})
