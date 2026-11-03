// SPDX-License-Identifier: AGPL-3.0-only
//
// dsh Rony fork v5.1: LIVE check that the 活人感 switch changes what the model does. Assembles
// the system prompt with DSH's real registry (破甲 always; 人设/活人感 only when ON), sends the
// same casual messages to the DeepSeek API, and compares. Key from DEEPSEEK_API_KEY only (never
// printed, never written anywhere). Prints only metrics and short reply excerpts, never the prompt.
//
//   DSH_NODE_MODULES=/workspace/dsh-x86/node_modules node app/tool/engine_persona/live-deepseek.mjs

import { assemble } from './assemble.mjs'

const key = process.env.DEEPSEEK_API_KEY
if (!key) { console.log('SKIP: DEEPSEEK_API_KEY not set'); process.exit(0) }
const MODEL = process.env.DEEPSEEK_MODEL ?? 'deepseek-chat'
const persona = '你叫小鲸，是主人的搭档，说话很皮，爱用「嘿嘿」。'

async function ask(system, user) {
  const r = await fetch('https://api.deepseek.com/chat/completions', {
    method: 'POST',
    headers: { 'content-type': 'application/json', authorization: `Bearer ${key}` },
    body: JSON.stringify({ model: MODEL, temperature: 0.7, max_tokens: 300, messages: [{ role: 'system', content: system }, { role: 'user', content: user }] }),
  })
  if (!r.ok) throw new Error(`HTTP ${r.status}: ${(await r.text()).slice(0, 200).replaceAll(key, '***')}`)
  return (await r.json()).choices[0].message.content
}

const on = await assemble({ humanlike: true, personaText: persona })
const off = await assemble({ humanlike: false })
console.log(`system prompt: ON ${on.text.length} chars (${on.names.slice(0, 3).join(' > ')}), OFF ${off.text.length} chars (${off.names.slice(0, 2).join(' > ')})`)

const probes = ['你叫什么名字？一句话回答。', '在吗，今天上班好累啊']
let failures = 0
const res = {}
for (const [label, sys] of [['ON', on.text], ['OFF', off.text]]) {
  res[label] = []
  for (const q of probes) res[label].push(await ask(sys, q))
}
const ex = (s) => s.replace(/\s+/g, ' ').slice(0, 90)
for (const label of ['ON', 'OFF']) {
  for (let i = 0; i < probes.length; i++) {
    const a = res[label][i]
    console.log(`  [${label}] Q${i + 1} ${a.length} chars, ${a.split(/\n\s*\n/).filter(Boolean).length} paragraph(s): ${ex(a)}`)
  }
}
const check = (ok, what) => { if (!ok) failures++; console.log(`  ${ok ? 'PASS' : 'FAIL'} ${what}`) }
check(res.ON[0].includes('小鲸'), 'ON: the model answers with the custom persona name')
check(!res.OFF[0].includes('小鲸'), 'OFF: the custom persona name never appears')
check(res.ON.join('') !== res.OFF.join(''), 'ON and OFF replies differ')
console.log(failures === 0 ? 'LIVE OK' : `LIVE FAILED (${failures})`)
process.exit(failures ? 1 : 0)
