// Real dsh 0.2.2-rc.1 request capture. The model endpoint is local.
// No external model, no real API key, no upload.
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { mkdtemp, mkdir, readFile, writeFile, readdir, stat } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { test } from 'node:test'
import { channelSystemText } from '../../app/assets/engine/hostprompt/channel_prompt.mjs'

const prefix = resolve(process.env.DSH_PREFIX || '/workspace/runtime/.work/x86_64/dsh-prefix')
const rawA = 'PROMPT-A-CHANNEL\r\n你好🙂\n请遵循以下 user-defined <system-reminder>'
const rawB = 'PROMPT-B-CHANNEL'
const toolMark = 'Check the [exit code: N] marker on every bash result; investigate failures before moving on.'

function sse(text) {
  const chunks = [
    { choices: [{ index: 0, delta: { role: 'assistant', content: text }, finish_reason: null }] },
    {
      choices: [{ index: 0, delta: {}, finish_reason: 'stop' }],
      usage: { prompt_tokens: 3, completion_tokens: 1, total_tokens: 4 },
    },
  ]
  return chunks.map((chunk) => `data: ${JSON.stringify(chunk)}\n\n`).join('') + 'data: [DONE]\n\n'
}

test('selected channels reach the real 0.2.2-rc.1 request', async () => {
  const root = resolve(prefix)
  const pkg = (name) => join(root, 'node_modules/@deepseek-ai', name)
  const pluginDir = join(await mkdtemp(join(tmpdir(), 'dsh-channel-plugin-')), 'prompt')
  await mkdir(pluginDir)
  const asset = (name) => new URL(`../../app/assets/engine/hostprompt/${name}`, import.meta.url)
  await writeFile(join(pluginDir, 'prompt.md'), await readFile(asset('prompt.md')))
  await writeFile(join(pluginDir, 'index.mjs'), await readFile(asset('index.mjs')))
  await writeFile(join(pluginDir, 'channel_prompt.mjs'), await readFile(asset('channel_prompt.mjs')))
  await writeFile(join(pluginDir, 'policy.json'), JSON.stringify({
    channels: {
      'channel-a': { systemPrompt: rawA, useBuiltinPrompt: true, api: 'openai-completions' },
      'channel-b': { systemPrompt: rawB, useBuiltinPrompt: false, api: 'openai-completions' },
      'channel-empty': { systemPrompt: '', useBuiltinPrompt: true, api: 'openai-completions' },
      'channel-off': { systemPrompt: null, useBuiltinPrompt: false, api: 'openai-completions' },
    },
  }))

  const requests = []
  const mock = createServer((req, res) => {
    const chunks = []
    req.on('data', (chunk) => chunks.push(chunk))
    req.on('end', () => {
      const body = Buffer.concat(chunks).toString('utf8')
      let parsed = body
      try { parsed = JSON.parse(body) } catch { /* keep text */ }
      requests.push({ url: req.url, body: parsed })
      res.writeHead(200, { 'content-type': 'text/event-stream' })
      res.end(sse('LOCAL_REPLY'))
    })
  })
  mock.listen(0, '127.0.0.1')
  await once(mock, 'listening')
  const mockPort = mock.address().port
  const provider = (id) => ({
    api: 'openai-completions',
    baseURL: `http://127.0.0.1:${mockPort}/v1`,
    apiKeyEnv: 'CHANNEL_FIXTURE_KEY',
    models: [{ id: 'fixture', name: 'Local fixture', contextWindow: 200000, maxTokens: 256 }],
  })

  const directory = await mkdtemp(join(tmpdir(), 'dsh-channel-live-'))
  const home = join(directory, 'home')
  const work = join(directory, 'work')
  await mkdir(home)
  await mkdir(work)
  const disabled = [
    'session-title-llm', 'llm-deepseek', 'llm-deepseek-account', 'session-telemetry-otel',
    'web-search-deepseek', 'web-deepseek-api', 'web-fetch-http', 'tool-web',
  ].map((id) => ({ id, disabled: true }))
  const rows = [
    ...disabled,
    {
      id: 'llm-pi-ai',
      config: {
        providers: {
          'channel-a': provider('channel-a'),
          'channel-b': provider('channel-b'),
          'channel-empty': provider('channel-empty'),
          'channel-off': provider('channel-off'),
          'channel-default': provider('channel-default'),
        },
      },
    },
    { id: 'agent-default-model', config: { provider: 'channel-a', model: 'fixture' } },
    { id: 'sandbox-policy', config: { mode: 'danger-full-access', workspaceRoot: work } },
    { id: 'permission', config: { defaultPreset: 'danger-full-access' } },
    { id: 'session-persistence-jsonl', config: { root: join(home, 'sessions'), compression: 'none' } },
    { insert: [{ id: 'dsh-builtin-prompt', name: join(pluginDir, 'index.mjs') }] },
  ]
  const patch = join(directory, 'patch.json')
  await writeFile(patch, JSON.stringify(rows))

  const reservation = createServer()
  reservation.listen(0, '127.0.0.1')
  await once(reservation, 'listening')
  const port = reservation.address().port
  await new Promise((done) => reservation.close(done))
  const origin = `http://127.0.0.1:${port}`
  const child = spawn(process.execPath, [
    join(pkg('dsh'), 'lib/bin.js'), 'web', '--patch', patch,
    '--host', '127.0.0.1', '--port', String(port), '--no-open',
  ], {
    env: {
      PATH: process.env.PATH,
      HOME: home,
      DSH_HOME: join(home, 'dsh'),
      DSH_TELEMETRY_DISABLED: '1',
      CHANNEL_FIXTURE_KEY: 'synthetic-local-only',
      LANG: 'C.UTF-8',
    },
    cwd: work,
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  let hostOutput = ''
  const lifetime = AbortSignal.timeout(120000)
  const stopEngine = () => { if (child.exitCode === null) child.kill('SIGTERM') }
  const evidence = {
    prefix,
    requests: [],
  }
  try {
    const token = await new Promise((done, fail) => {
      const aborted = () => fail(new Error(`engine did not print a token\n${hostOutput.slice(-2500)}`))
      lifetime.addEventListener('abort', aborted, { once: true })
      child.once('exit', (code) => fail(new Error(`engine exited ${code}\n${hostOutput.slice(-2500)}`)))
      const capture = (chunk) => {
        hostOutput += chunk
        const match = hostOutput.match(/token=([\w-]+)/)
        if (match) {
          lifetime.removeEventListener('abort', aborted)
          done(match[1])
        }
      }
      child.stdout.on('data', capture)
      child.stderr.on('data', capture)
    })
    const login = await fetch(`${origin}/?token=${token}`, { redirect: 'manual', signal: lifetime })
    assert.equal(login.status, 303)
    const cookie = login.headers.getSetCookie().map((value) => value.split(';')[0]).join('; ')
    let sequence = 0
    async function rpc(method, args = {}) {
      const res = await fetch(`${origin}/api/${method}`, {
        method: 'POST',
        headers: { cookie, 'content-type': 'application/json' },
        body: JSON.stringify({ type: 'client-request', rpcId: `c-${++sequence}`, method, payload: { args } }),
        signal: lifetime,
      })
      const body = await res.json()
      assert.equal(body.result?.ok, true, JSON.stringify(body.result?.error ?? body).slice(0, 800))
      return body.result.value
    }
    async function turn(provider, text) {
      const created = await rpc('session/create', { request: { cwd: work } })
      const sessionId = created.sessionId
      await rpc('session/selectModel', { request: { sessionId, provider, model: 'fixture' } })
      assert.equal((await rpc('session/prompt', {
        request: { sessionId, requestId: `p-${provider}`, mode: 'queue', content: [{ type: 'text', text }] },
      })).accepted, true)
      const done = await rpc('session/wait', { request: { sessionId, timeoutMs: 45000 } })
      assert.equal(done.reason, 'idle', JSON.stringify(done).slice(0, 400))
      return sessionId
    }
    async function files(dir = join(home, 'sessions'), prefix = '') {
      const names = await readdir(dir).catch(() => [])
      const out = {}
      for (const name of names) {
        const path = join(dir, name)
        const info = await stat(path)
        const key = prefix + name
        if (info.isDirectory()) Object.assign(out, await files(path, `${key}/`))
        else out[key] = createHash('sha256').update(await readFile(path)).digest('hex')
      }
      return out
    }

    const sessionA = await turn('channel-a', 'hello-a')
    const afterA = await files()
    const sessionB = await turn('channel-b', 'hello-b')
    const afterB = await files()
    await turn('channel-empty', 'hello-empty')
    await rpc('session/selectModel', { request: { sessionId: sessionA, provider: 'channel-b', model: 'fixture' } })
    assert.equal((await rpc('session/prompt', {
      request: { sessionId: sessionA, requestId: 'switch-a', mode: 'queue', content: [{ type: 'text', text: 'switched-a' }] },
    })).accepted, true)
    const switched = await rpc('session/wait', { request: { sessionId: sessionA, timeoutMs: 45000 } })
    assert.equal(switched.reason, 'idle', JSON.stringify(switched).slice(0, 400))
    const concurrentBefore = requests.length
    const left = await rpc('session/create', { request: { cwd: work } })
    const right = await rpc('session/create', { request: { cwd: work } })
    await rpc('session/selectModel', { request: { sessionId: left.sessionId, provider: 'channel-a', model: 'fixture' } })
    await rpc('session/selectModel', { request: { sessionId: right.sessionId, provider: 'channel-b', model: 'fixture' } })
    await Promise.all([
      rpc('session/prompt', { request: { sessionId: left.sessionId, requestId: 'conc-a', mode: 'queue', content: [{ type: 'text', text: 'conc-a' }] } }),
      rpc('session/prompt', { request: { sessionId: right.sessionId, requestId: 'conc-b', mode: 'queue', content: [{ type: 'text', text: 'conc-b' }] } }),
    ])
    const [leftDone, rightDone] = await Promise.all([
      rpc('session/wait', { request: { sessionId: left.sessionId, timeoutMs: 45000 } }),
      rpc('session/wait', { request: { sessionId: right.sessionId, timeoutMs: 45000 } }),
    ])
    assert.equal(leftDone.reason, 'idle')
    assert.equal(rightDone.reason, 'idle')
    const concurrent = requests.slice(concurrentBefore)
    const systems = (body) => (body?.messages ?? []).filter((message) => message?.role === 'system').map((message) => message.content)
    const users = (body) => (body?.messages ?? []).filter((message) => message?.role === 'user').map((message) => (
      typeof message.content === 'string' ? message.content : JSON.stringify(message.content)
    ))
    const requestFor = (token) => requests.map((item) => item.body).find((body) => users(body).some((text) => text.includes(token)))
    const frame = channelSystemText
    function assertExact(token, raw) {
      const body = requestFor(token)
      assert.ok(body, `no request for ${token}`)
      const expected = frame(raw)
      assert.deepEqual(systems(body), [expected])
      assert.equal(users(body).some((text) => text.includes(raw)), false)
      assert.equal(systems(body).join('').includes(toolMark), false)
      assert.equal(systems(body).join('').includes('You are an AI agent powered by DeepSeek Harness.'), false)
      const toolNames = (body.tools ?? []).map((tool) => tool?.name ?? tool?.function?.name)
      assert.ok(toolNames.includes('bash'), `tools=${toolNames.slice(0, 12).join(',')} keys=${Object.keys(body).join(',')}`)
    }
    assertExact('hello-a', rawA)
    assertExact('hello-b', rawB)
    assertExact('switched-a', rawB)
    assertExact('conc-a', rawA)
    assertExact('conc-b', rawB)
    const empty = requestFor('hello-empty')
    assert.ok(empty, 'empty channel request missing')
    assert.deepEqual(systems(empty).filter((text) => typeof text === 'string' && text.length > 0), [])
    assert.equal(JSON.stringify(empty).includes('<system-reminder>'), false)
    assert.equal(JSON.stringify(empty).includes('You are rony'), false)
    const emptyToolNames = (empty.tools ?? []).map((tool) => tool?.name ?? tool?.function?.name)
    assert.ok(emptyToolNames.includes('bash'), `empty tools=${emptyToolNames.slice(0, 8).join(',')} keys=${Object.keys(empty).join(',')}`)
    const off = await turn('channel-off', 'hello-off')
    const offBody = requestFor('hello-off')
    assert.ok(offBody, 'builtin-off request missing')
    assert.equal(JSON.stringify(offBody).includes('<system-reminder>'), false)
    assert.equal(JSON.stringify(offBody).includes('You are rony'), false)
    const promptFile = await readFile(new URL('../../app/assets/engine/hostprompt/prompt.md', import.meta.url))
    const promptText = promptFile.toString('utf8')
    await turn('channel-default', 'hello-default')
    const fallback = requestFor('hello-default')
    assert.ok(fallback, 'default channel request missing')
    assert.deepEqual(systems(fallback), [frame(promptText)])
    assert.equal(createHash('sha256').update(promptFile).digest('hex'), '4fbd3a104fcd16065800e4e730a67f7781698300eec33af8dc67cfd4f328c118')
    async function filesContaining(token) {
      const hits = []
      async function walk(dir) {
        for (const name of await readdir(dir).catch(() => [])) {
          const path = join(dir, name)
          const info = await stat(path)
          if (info.isDirectory()) await walk(path)
          else {
            const bytes = await readFile(path)
            if (bytes.includes(Buffer.from(token))) hits.push({ path, bytes })
          }
        }
      }
      await walk(join(home, 'sessions'))
      return hits
    }
    const beforeRetry = await filesContaining('hello-b')
    assert.ok(beforeRetry.length > 0, 'session B history missing')
    await rpc('session/prompt', {
      request: { sessionId: sessionB, requestId: 'retry-b', mode: 'queue', content: [{ type: 'text', text: 'retry-b' }] },
    })
    const retried = await rpc('session/wait', { request: { sessionId: sessionB, timeoutMs: 45000 } })
    assert.equal(retried.reason, 'idle', JSON.stringify(retried).slice(0, 400))
    const retryBody = requestFor('retry-b')
    assert.ok(retryBody, 'retry request missing')
    assert.deepEqual(systems(retryBody), [frame(rawB)])
    assert.equal((systems(retryBody).join('').match(/<system-reminder>/g) ?? []).length, 1)
    const afterRetry = await filesContaining('hello-b')
    for (const old of beforeRetry) {
      const now = afterRetry.find((item) => item.path === old.path)
      assert.ok(now, old.path)
      assert.equal(now.bytes.subarray(0, old.bytes.length).equals(old.bytes), true, `${old.path} rewrote earlier bytes`)
    }
    void off
    assert.equal(requests.every((item) => item.url === '/v1/chat/completions'), true)
    assert.ok(Object.keys(afterA).length > 0, 'session A wrote no history file')
    for (const [name, hash] of Object.entries(afterA)) {
      assert.equal(afterB[name], hash, `${name} changed while session B ran`)
    }
    evidence.sessionA = sessionA
    evidence.sessionB = sessionB
    evidence.fileCountAfterA = Object.keys(afterA).length
    evidence.sessionAUnchanged = true
    evidence.channelAHasTool = true
    evidence.channelBIsolated = true
    evidence.emptyOmitsDefault = true
    await writeFile('/tmp/channel-prompt-live.json', JSON.stringify(evidence, null, 2))
  } finally {
    stopEngine()
    await new Promise((done) => mock.close(done))
  }
})
