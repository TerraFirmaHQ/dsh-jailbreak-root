// SPDX-License-Identifier: AGPL-3.0-only
//
// dsh Rony fork: always-on 破甲 prompt (owner's DeepSeek-4.1-Flash 破甲提示词).
//
// The app copies this directory into the guest (/opt/dsh-app/rony/pojia/) at every engine start
// and mounts this file by absolute path with its own `--patch` overlay while the switch
// 设置 → Root 与 MCP（Rony）→ 破甲提示词 is on (default on; never in safe mode).
//
// It registers one *global* system-prompt section that sorts before everything else
// (order -2000; the harness identity is -1000 and the deployment persona 0), so on every model
// step of every session and agent preset the prompt is the first text of the system prompt and
// the persona, tool, root and MCP instructions follow it. A global section is never shadowed by a
// preset's own persona (that only replaces `deployment:persona-prefix`). The text is registered
// with `interpolate: false`, so it is sent in full (CRLF folded to LF, outer blank lines trimmed) and `{{…}}` in it can
// never break prompt assembly. There is no byte budget on prompt sections (the 64 KiB budget in
// DSH applies to AGENTS.md workspace instructions only, which is why this is not an AGENTS.md).

import { readFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

export const name = 'rony-pojia'
export const inject = ['systemPrompt']

/** Section name and placement (before HARNESS_IDENTITY = -1000). */
export const SECTION_NAME = 'rony:pojia'
export const SECTION_ORDER = -2000

const here = dirname(fileURLToPath(import.meta.url))

/** The prompt as shipped, line endings normalised; leading BOM dropped. */
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
  }), 'rony-pojia.section()')
}
