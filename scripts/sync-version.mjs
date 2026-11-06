#!/usr/bin/env node

import { readFileSync, writeFileSync, existsSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const root = resolve(here, '..')

function readWorkspaceVersion() {
  const cargoPath = resolve(root, 'Cargo.toml')
  const text = readFileSync(cargoPath, 'utf8')

  const workspaceMatch = text.match(/\[workspace\.package\]([\s\S]*?)(?=\n\[|$)/)
  if (!workspaceMatch) {
    throw new Error('Cargo.toml: [workspace.package] section not found')
  }

  const versionMatch = workspaceMatch[1].match(/^\s*version\s*=\s*"([^"]+)"/m)
  if (!versionMatch) {
    throw new Error('Cargo.toml: [workspace.package] version not found')
  }

  return versionMatch[1]
}

function writeJsonVersion(relativePath, version, label) {
  const full = resolve(root, relativePath)
  if (!existsSync(full)) {
    console.log(`  skip ${label} (not present)`)
    return
  }

  const raw = readFileSync(full, 'utf8')
  const data = JSON.parse(raw)
  if (data.version === version) {
    console.log(`  ok   ${label} already at ${version}`)
    return
  }

  data.version = version
  writeFileSync(full, JSON.stringify(data, null, 2) + '\n', 'utf8')
  console.log(`  set  ${label} -> ${version}`)
}

function writeTauriConfVersion(relativePath, version) {
  const full = resolve(root, relativePath)
  if (!existsSync(full)) {
    console.log(`  skip ${relativePath} (not present)`)
    return
  }

  const raw = readFileSync(full, 'utf8')
  const data = JSON.parse(raw)
  if (data.version === version) {
    console.log(`  ok   ${relativePath} already at ${version}`)
    return
  }

  data.version = version
  writeFileSync(full, JSON.stringify(data, null, 4) + '\n', 'utf8')
  console.log(`  set  ${relativePath} -> ${version}`)
}

const version = readWorkspaceVersion()
console.log(`workspace version: ${version}`)

writeJsonVersion('package.json', version, 'package.json (root)')
writeJsonVersion('frontend/package.json', version, 'frontend/package.json')
writeJsonVersion('frontend-web/package.json', version, 'frontend-web/package.json')
writeTauriConfVersion('crates/tauri-app/tauri.conf.json', version)