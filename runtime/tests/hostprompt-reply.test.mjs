// Locked prompt is the first system text, a local mock model replies, and a
// second prompt still completes after cancel. Skips without DSH_PREFIX.
// This is the x86_64 engine on the build host, not a phone and not ARM64.
import assert from 'node:assert/strict'
import { createHash } from 'node:crypto'
import { createServer } from 'node:http'
import { spawn } from 'node:child_process'
import { once } from 'node:events'
import { mkdtemp, mkdir, readFile, writeFile, symlink } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { test } from 'node:test'

const prefix = process.env.DSH_PREFIX
const promptSha = '4fbd3a104fcd16065800e4e730a67f7781698300eec33af8dc67cfd4f328c118'

function sse(text) {
  const events = [
    { type: 'message_start', message: { usage: { input_tokens: 3, output_tokens: 1 } } },
    { type: 'content_block_start', index: 0, content_block: { type: 'text', text: '' } },
    { type: 'content_block_delta', index: 0, delta: { type: 'text_delta', text } },
    { type: 'content_block_stop', index: 0 },
    { type: 'message_delta', delta: { stop_reason: 'end_turn' }, usage: { output_tokens: 2 } },
    { type: 'message_stop' },
  ]
  return events.map((event) => `event: ${event.type}\ndata: ${JSON.stringify(event)}\n\n`).join('')
}

test('locked prompt then a reply, then another after cancel', { skip: !prefix }, async () => {
  const root = resolve(prefix)
  const pkg = (name) => join(root, 'node_modules/@deepseek-ai', name)
  const promptFile = new URL('../../app/assets/engine/hostprompt/prompt.md', import.meta.url)
  const pluginFile = new URL('../../app/assets/engine/hostprompt/index.mjs', import.meta.url)
  const logicFile = new URL('../../app/assets/engine/hostprompt/channel_prompt.mjs', import.meta.url)
  const raw = await readFile(promptFile)
  const prompt = raw.toString('utf8')
  assert.equal(raw.length, 21403)
  assert.equal(createHash('sha256').update(raw).digest('hex'), promptSha)

  const requests = []
  const mock = createServer((req, res) => {
    const chunks = []
    req.on('data', (chunk) => chunks.push(chunk))
    req.on('end', () => {
      const body = Buffer.concat(chunks).toString('utf8')
      requests.push({ url: req.url, body })
      const text = requests.length === 1 ? 'SYNTHETIC_REPLY_ONE' : 'SYNTHETIC_REPLY_TWO'
      res.writeHead(200, { 'content-type': 'text/event-stream' })
      res.end(sse(text))
    })
  })
  mock.listen(0, '127.0.0.1')
  await once(mock, 'listening')
  const mockPort = mock.address().port

  const directory = await mkdtemp(join(tmpdir(), 'dsh-prompt-reply-'))
  const home = join(directory, 'home')
  const work = join(directory, 'work')
  const pluginDir = join(directory, 'prompt')
  await mkdir(home, { recursive: true })
  await mkdir(work)
  await mkdir(pluginDir)
  await writeFile(join(pluginDir, 'prompt.md'), raw)
  await writeFile(join(pluginDir, 'index.mjs'), await readFile(pluginFile))
  await writeFile(join(pluginDir, 'channel_prompt.mjs'), await readFile(logicFile))
  await symlink(join(root, 'node_modules'), join(directory, 'node_modules'), 'dir')

  const rows = [
    'session-title-llm', 'llm-pi-ai', 'llm-deepseek-account', 'session-telemetry-otel',
    'web-search-deepseek', 'web-deepseek-api', 'web-fetch-http', 'tool-web',
  ].map((id) => ({ id, disabled: true }))
  rows.push(
    {
      id: 'llm-deepseek',
      config: {
        baseURL: `http://127.0.0.1:${mockPort}/v1`,
        apiKeyEnv: 'DSH_WEB_TEST_KEY',
        thinking: 'disabled',
        maxTokens: 256,
      },
    },
    { id: 'sandbox-policy', config: { mode: 'danger-full-access', workspaceRoot: work } },
    { id: 'agent-default-model', config: { provider: 'deepseek-official', model: 'deepseek-flash' } },
    { id: 'session-persistence-jsonl', config: { root: join(home, 'sessions'), compression: 'none' } },
    { insert: [{ id: 'dsh-builtin-prompt', name: join(pluginDir, 'index.mjs') }] },
  )
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
      DSH_WEB_TEST_KEY: 'SYNTHETIC_PROMPT_REPLY_KEY',
      LANG: 'C.UTF-8',
    },
    cwd: work,
    stdio: ['ignore', 'pipe', 'pipe'],
  })
  let hostOutput = ''
  const lifetime = AbortSignal.timeout(90000)
  const stopEngine = () => {
    if (child.exitCode === null) child.kill('SIGTERM')
  }
  try {
    const token = await new Promise((done, fail) => {
      const aborted = () => fail(new Error(`engine did not print a token\n${hostOutput.slice(-2000)}`))
      lifetime.addEventListener('abort', aborted, { once: true })
      child.once('exit', (code) => fail(new Error(`engine exited ${code}\n${hostOutput.slice(-2000)}`)))
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
    const cookie = login.headers.getSetCookie().map((v) => v.split(';')[0]).join('; ')
    let sequence = 0
    async function rpc(method, args = {}) {
      const res = await fetch(`${origin}/api/${method}`, {
        method: 'POST',
        headers: { cookie, 'content-type': 'application/json' },
        body: JSON.stringify({ type: 'client-request', rpcId: `p-${++sequence}`, method, payload: { args } }),
        signal: lifetime,
      })
      const body = await res.json()
      assert.equal(body.result?.ok, true, JSON.stringify(body.result?.error ?? body).slice(0, 500))
      return body.result.value
    }
    const created = await rpc('session/create', { request: { cwd: work } })
    const sessionId = created.sessionId
    assert.equal((await rpc('session/prompt', {
      request: { sessionId, requestId: 'reply-one', mode: 'queue', content: [{ type: 'text', text: '1' }] },
    })).accepted, true)
    const done = await rpc('session/wait', { request: { sessionId, timeoutMs: 30000 } })
    assert.equal(done.reason, 'idle', JSON.stringify(done).slice(0, 300))
    const sent = requests[0]?.body ?? ''
    const strings = []
    const walk = (value) => {
      if (typeof value === 'string') strings.push(value)
      else if (Array.isArray(value)) value.forEach(walk)
      else if (value && typeof value === 'object') Object.values(value).forEach(walk)
    }
    walk(JSON.parse(sent))
    const at = strings.findIndex((text) => text.includes(prompt))
    if (at < 0) {
      const longs = strings.filter((text) => text.length > 500).map((text) => `${text.length}:${createHash('sha256').update(text).digest('hex').slice(0, 12)}`)
      const errors = hostOutput.split('\n').filter((line) => /error|Error|prompt/i.test(line)).slice(-12)
      throw new Error(`locked prompt missing; long strings ${longs.join(',')} ; log ${errors.join(' | ').slice(0, 800)}`)
    }
    const ident = strings.findIndex((text) => text.includes('You are an AI agent powered by DeepSeek Harness.'))
    if (ident >= 0) assert.ok(at <= ident, 'locked prompt is not the first system text')
    if (!JSON.stringify(done).includes('SYNTHETIC_REPLY_ONE')) throw new Error('first reply missing')
    await rpc('session/cancel', { request: { sessionId } })
    assert.equal((await rpc('session/prompt', {
      request: { sessionId, requestId: 'reply-two', mode: 'queue', content: [{ type: 'text', text: '2' }] },
    })).accepted, true)
    const again = await rpc('session/wait', { request: { sessionId, timeoutMs: 30000 } })
    assert.equal(again.reason, 'idle')
    assert.ok(requests.length >= 2, 'second message did not reach the model')
    if (!JSON.stringify(again).includes('SYNTHETIC_REPLY_TWO')) throw new Error('second reply missing')
    assert.ok(!hostOutput.includes('TypeError'), 'engine log has a TypeError')
  } finally {
    stopEngine()
    await new Promise((done) => mock.close(done))
  }
})
