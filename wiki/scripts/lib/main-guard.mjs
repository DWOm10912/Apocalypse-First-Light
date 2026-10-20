// Only the Main workspace publishes: uploads to R2 and Cloudflare deploys run from the main checkout on master,
// never from a linked worktree (C, D, Codex) or another branch.
import path from 'node:path'
import { execFileSync } from 'node:child_process'

export function git(cwd, args) {
  return execFileSync('git', args, { cwd, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
}

/** Throws with a plain reason unless cwd is the Main checkout on master. */
export function requireMain(repo) {
  const gitDir = path.resolve(repo, git(repo, ['rev-parse', '--git-dir']))
  const common = path.resolve(repo, git(repo, ['rev-parse', '--git-common-dir']))
  if (gitDir !== common) throw new Error('这是一个关联工作树（C / D / Codex），只有 Main 的主检出可以上传图片或部署 Wiki')
  const branch = git(repo, ['rev-parse', '--abbrev-ref', 'HEAD'])
  if (branch !== 'master') throw new Error(`当前分支是 ${branch}，只有 master 可以上传图片或部署 Wiki`)
}
