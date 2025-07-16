const { promisify } = require('util');
const childProcess = require('child_process');
const exec = promisify(childProcess.execFile);
const os = require('os');

const LINUX_VERSION = '1.11.1.1155';

async function run(command, args) {
  console.log(`$ ${command} ${args.join(' ')}`);
  const proc = childProcess.spawn(command, args, {
    stdio: 'inherit',
  });
  return new Promise((resolve, reject) => {
    proc.once('close', (code) => {
      if (code === 0) {
        resolve();
      } else {
        reject(new Error(`${command} exit code: ${code}`));
      }
    });
  });
}

async function which(name) {
  try {
    const { stdout } = await exec('which', [name]);
    return stdout.trimEnd();
  } catch {
    return false;
  }
}

async function main() {
  const isClojureInstalled = await which('clojure');
  if (isClojureInstalled) {
    return; // Easy
  }

  console.log('Installing clojure...');
  if (os.platform === 'darwin' || await which('brew')) {
    await run('brew', ['install', 'clojure/tools/clojure']);
  } else if (os.platform !== 'win32') {
    await run('curl', ['-O', `https://download.clojure.org/install/linux-install-${LINUX_VERSION}.sh`]);
    await run('chmod', ['+x', `linux-install-${LINUX_VERSION}.sh`]);
    await run('sudo', [`./linux-install-${LINUX_VERSION}.sh`]);
  }
}

main();
