import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  effectiveChannelPrompt,
  projectRequest,
  providerIdOf,
  readPolicy,
  recordFor,
  rewriteSections,
} from '../../assets/engine/hostprompt/channel_prompt.mjs'

const fallback = 'DEFAULT\r\n正文'

test('null falls back only while the switch is on', () => {
  assert.equal(effectiveChannelPrompt(null, true, fallback), fallback)
  assert.equal(effectiveChannelPrompt(undefined, true, fallback), fallback)
  assert.equal(effectiveChannelPrompt(null, false, fallback), null)
  assert.equal(effectiveChannelPrompt('', true, fallback), null)
  assert.equal(effectiveChannelPrompt('', false, fallback), null)
  assert.equal(effectiveChannelPrompt('  ', false, fallback), '  ')
  assert.equal(effectiveChannelPrompt('自定', false, fallback), '自定')
})

test('a missing channel is null and builtin on, and another channel is not used', () => {
  const policy = readPolicy(JSON.stringify({
    channels: { a: { systemPrompt: 'PROMPT-A', useBuiltinPrompt: false, api: 'openai-completions' } },
  }))
  assert.equal(recordFor(policy, 'b').systemPrompt, null)
  assert.equal(recordFor(policy, 'b').useBuiltinPrompt, true)
  assert.equal(recordFor(policy, null).systemPrompt, null)
  assert.equal(effectiveChannelPrompt(recordFor(policy, 'a').systemPrompt, recordFor(policy, 'a').useBuiltinPrompt, fallback), 'PROMPT-A')
  const tool = 'Check the [exit code: N] marker on every bash result; investigate failures before moving on.'
  const history = [
    { role: 'system', content: 'OLD' },
    { role: 'system', content: 'USER-NOTE' },
    { role: 'system', content: tool },
    { role: 'user', content: 'hi' },
  ]
  const before = JSON.stringify(history)
  const a = projectRequest(history, 'PROMPT-A', 'openai-completions', 'OLD')
  const b = projectRequest(history, null, 'anthropic-messages', 'OLD')
  assert.equal(JSON.stringify(history), before)
  assert.equal(a.messages[0].content, 'PROMPT-A')
  assert.equal(a.messages.some((m) => m.content === 'USER-NOTE'), true)
  assert.equal(a.messages.some((m) => m.content === tool), true)
  assert.equal(a.messages.some((m) => m.content === 'OLD'), false)
  assert.equal(b.system, undefined)
  assert.equal(b.messages.some((m) => m.content === 'USER-NOTE'), true)
  assert.equal(b.messages.some((m) => m.content === tool), true)
  assert.equal(b.messages.some((m) => JSON.stringify(m).includes('PROMPT-A')), false)
})

test('chat completions merge in order and leave the input sections alone', () => {
  const sections = [
    { name: 'dsh:builtin-prompt', text: 'OLD' },
    { name: 'identity', text: 'You are an AI agent powered by DeepSeek Harness. stay out' },
    { name: 'dsh:persona', text: 'PERSONA' },
    { name: 'tools', text: 'call the tool' },
    { name: 'blank', text: '' },
  ]
  const before = JSON.stringify(sections)
  const merged = rewriteSections(sections, 'CHANNEL', 'openai-completions')
  assert.equal(JSON.stringify(sections), before)
  assert.deepEqual(merged, [{ name: 'dsh:system', text: 'CHANNEL\n\nPERSONA\n\ncall the tool' }])
  assert.deepEqual(rewriteSections(sections, '', 'anthropic-messages').map((s) => s.text), ['PERSONA', 'call the tool'])
})

test('provider id is the selected route on the 0.2.2-rc.1 assemble context', () => {
  const selected = { provider: 'channel-a', model: 'fixture' }
  assert.equal(providerIdOf({
    agent: { options: { provider: 'other' } },
    scope: {},
    modelSelection: selected,
  }), 'channel-a')
  assert.equal(providerIdOf({
    agent: { model: { current() { return { provider: 'channel-b', model: 'fixture' } } } },
  }), 'channel-b')
  assert.equal(providerIdOf({ agent: { options: { provider: 'channel-c' } } }), 'channel-c')
  assert.equal(providerIdOf({ agent: { model: { provider: 'not-a-selection' } } }), null)
  assert.equal(providerIdOf(null), null)
})
