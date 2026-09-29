import { spawn } from 'node:child_process'

const url = 'http://127.0.0.1:8081/java-web-classwork/'
const api = `${url}api/messages?after=0`
const maven = spawn(process.platform === 'win32' ? 'mvn.cmd' : 'mvn',
  ['-Dmaven.test.skip=true', 'package', 'cargo:run'], { stdio: 'inherit' })
let finished = false
let ready = false

async function isReady(address) {
  try {
    const response = await fetch(address, { signal: AbortSignal.timeout(1500) })
    return response.ok
  } catch { return false }
}

async function watchReady() {
  while (!finished && !ready) {
    if (await isReady(url) && await isReady(api)) {
      ready = true
      console.log(`\nJava Web 已就绪：${url}\n`)
      return
    }
    await new Promise(resolve => setTimeout(resolve, 1000))
  }
}

maven.on('error', error => {
  finished = true
  console.error(`无法启动 Maven：${error.message}`)
  process.exitCode = 1
})
maven.on('exit', (code, signal) => {
  finished = true
  process.exitCode = code ?? (signal ? 1 : 0)
})
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => maven.kill(signal))
}
void watchReady()
