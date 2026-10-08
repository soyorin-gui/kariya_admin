import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync, unlinkSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { execFileSync } from 'node:child_process';

const root = process.cwd();
const oldDir = join(root, 'dist', 'npm-packages');
const baseDir = join(root, 'npm-offline');
const statePath = join(baseDir, 'state.json');
const pendingPath = join(baseDir, 'pending.json');
const command = process.argv[2] || '--pack';
const keyOf = (p) => `${p.name}@${p.version}`;
const save = (file, value) => writeFileSync(file, JSON.stringify(value, null, 2) + '\n', 'utf8');
const read = (file) => JSON.parse(readFileSync(file, 'utf8'));
const out = (s) => console.log(s);

function packageFromTgz(file) {
  // Regular npm archives use package/package.json, but @types archives
  // frequently use <type-name>/package.json (e.g. node/package.json).
  const listing = execFileSync('tar', ['-tzf', file], {
    encoding: 'utf8', maxBuffer: 8 * 1024 * 1024, windowsHide: true
  });
  const paths = listing.split(/\r?\n/).map(s => s.replace(/^\.\//, ''));
  const candidates = paths.filter(p => /^[^/]+\/package\.json$/.test(p));
  const manifestPath = candidates.includes('package/package.json')
    ? 'package/package.json' : (candidates.length === 1 ? candidates[0] : null);
  if (!manifestPath) {
    throw new Error(`压缩包中未发现唯一的顶层 package.json: ${file}`);
  }
  const content = execFileSync('tar', ['-xOzf', file, manifestPath], {
    encoding: 'utf8', maxBuffer: 4 * 1024 * 1024, windowsHide: true
  });
  const { name, version } = JSON.parse(content);
  if (!name || !version) throw new Error(`Missing name/version: ${file}`);
  return { name, version };
}

function currentPackages() {
  const lockPath = join(root, 'package-lock.json');
  if (!existsSync(lockPath)) throw new Error('项目根目录没有 package-lock.json');
  const lock = read(lockPath);
  if (!lock.packages) throw new Error('需要 npm package-lock v2/v3 的 packages 字段');
  const map = new Map();
  const skipped = [];
  for (const [path, p] of Object.entries(lock.packages)) {
    if (!path.includes('node_modules/') || p.link) continue;
    const pathName = path.slice(path.lastIndexOf('node_modules/') + 'node_modules/'.length);
    const name = p.name || pathName;
    const version = p.version;
    if (!version || (p.name && p.name !== pathName) ||
        !/^(?:@[\w.-]+\/)?[\w.-]+$/.test(name) ||
        !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?(?:\+[0-9A-Za-z.-]+)?$/.test(version) ||
        (p.resolved && !/^https?:\/\//i.test(p.resolved))) {
      skipped.push({ path, name, version, resolved: p.resolved || null, reason: '别名、非 registry 来源或非标准版本' });
      continue;
    }
    map.set(keyOf({name, version}), { name, version });
  }
  return { packages: [...map.values()].sort((a,b) => keyOf(a).localeCompare(keyOf(b))), skipped };
}

function npmPack(p, destination) {
  // On Windows npm is usually npm.cmd. Use cmd.exe explicitly and show errors.
  // Package spec is validated above and cannot contain shell metacharacters.
  const spec = `${p.name}@${p.version}`;
  const args = ['pack', spec, '--pack-destination', destination, '--ignore-scripts', '--json'];
  if (process.platform === 'win32') {
    // 包名/版本已通过严格校验，不能再用双引号包裹 spec：某些 Windows npm.cmd 会把引号视为包名字符。
    const commandLine = `npm.cmd pack ${spec} --pack-destination ${destination} --ignore-scripts --json`;
    const result = execFileSync('cmd.exe', ['/d', '/c', commandLine], {
      cwd: root, encoding: 'utf8', windowsHide: true, maxBuffer: 16 * 1024 * 1024
    });
    return JSON.parse(result);
  }
  return JSON.parse(execFileSync('npm', args, {
    cwd: root, encoding: 'utf8', maxBuffer: 16 * 1024 * 1024
  }));
}

function archiveFolder(folder, archive) {
  execFileSync('tar', ['-czf', archive, '-C', folder, '.'], {
    cwd: root, stdio: 'inherit', windowsHide: true
  });
}

try {
  mkdirSync(baseDir, { recursive: true });
  if (command === '--init') {
    if (existsSync(statePath)) throw new Error('state.json 已存在，不能再次初始化，以免覆盖上传历史');
    if (!existsSync(oldDir)) throw new Error(`找不到旧依赖目录: ${oldDir}`);
    const tgz = readdirSync(oldDir).filter(f => f.toLowerCase().endsWith('.tgz'));
    if (tgz.length === 0) throw new Error('旧目录没有 .tgz 文件');
    const found = new Map();
    const broken = [];
    for (const file of tgz) {
      try {
        const p = packageFromTgz(join(oldDir, file));
        found.set(keyOf(p), p);
      } catch (err) {
        broken.push({ file, error: err.message });
      }
    }
    if (broken.length) {
      save(join(baseDir, 'init-errors.json'), broken);
      throw new Error(`${broken.length} 个旧包无法读取，见 npm-offline/init-errors.json；本次未初始化`);
    }
    save(statePath, {
      schema: 1,
      note: '确认内网已有的 npm 包名及版本；初始化默认旧目录中的每个 tgz 都已上传内网',
      known: [...found.keys()].sort(), updatedAt: new Date().toISOString()
    });
    out(`初始化完成：扫描 ${tgz.length} 个旧 .tgz，记录 ${found.size} 个唯一包版本`);
    out(`历史记录：${statePath}`);
    out('注意：请确认旧目录中这些包均已存在于内网 npm 仓库。');
  } else if (command === '--pack') {
    if (!existsSync(statePath)) throw new Error('请先执行 node pack-incremental.mjs --init');
    if (existsSync(pendingPath)) throw new Error('上一批增量还未确认；请先上传后 --confirm，或查明原因再处理 pending.json');
    const state = read(statePath);
    const { packages, skipped } = currentPackages();
    const known = new Set(state.known);
    const delta = packages.filter(p => !known.has(keyOf(p)));
    out(`当前普通 npm 依赖版本数：${packages.length}`);
    out(`历史记录版本数：${known.size}`);
    out(`待增量打包：${delta.length}`);
    if (skipped.length) {
      save(join(baseDir, 'skipped-latest.json'), skipped);
      out(`警告：有 ${skipped.length} 条特殊依赖记录，见 npm-offline/skipped-latest.json`);
    }
    if (!delta.length) {
      out('没有新增依赖版本，本次不创建日期文件夹或 tar.gz。');
      process.exit(0);
    }
    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const name = `incremental-${stamp}`;
    const batch = join(baseDir, name);
    mkdirSync(batch, { recursive: true });
    const success = [];
    const failures = [];
    for (let i = 0; i < delta.length; i++) {
      const p = delta[i];
      out(`[${i+1}/${delta.length}] ${keyOf(p)}`);
      try {
        const manifest = npmPack(p, batch);
        const filename = manifest?.[0]?.filename;
        if (!filename || filename.includes('/') || filename.includes('\\')) throw new Error('npm pack 未返回合法 tgz 文件名');
        const outputFile = join(batch, filename);
        if (!existsSync(outputFile) || statSync(outputFile).size === 0) throw new Error('npm pack 未产生有效文件');
        const actual = packageFromTgz(outputFile);
        if (keyOf(actual) !== keyOf(p)) throw new Error(`包内容与锁文件不符: ${keyOf(actual)}`);
        success.push({ ...p, filename });
      } catch (err) {
        failures.push({ ...p, error: String(err.stderr || err.message || err) });
        console.error(`  FAILED: ${err.message}`);
        if (err.stderr) console.error(String(err.stderr).slice(0, 1500));
      }
    }
    save(join(batch, 'report.json'), { success, failures, skipped });
    if (failures.length) {
      throw new Error(`${failures.length} 个包打包失败：本次不生成归档，也不会更新历史记录。详见 ${batch}\\report.json`);
    }
    const archive = join(baseDir, `${name}.tar.gz`);
    archiveFolder(batch, archive);
    save(pendingPath, {
      batch: name, archive, packages: success.map(keyOf), createdAt: new Date().toISOString()
    });
    out(`成功生成 ${success.length} 个 .tgz`);
    out(`日期文件夹：${batch}`);
    out(`传输归档：${archive}`);
    out('内网全部上传并验证成功后，执行：node pack-incremental.mjs --confirm YES');
  } else if (command === '--confirm') {
    if (process.argv[3] !== 'YES') throw new Error('确认上传后须输入：--confirm YES');
    if (!existsSync(pendingPath)) throw new Error('没有待确认的增量批次');
    const pending = read(pendingPath);
    const state = read(statePath);
    const known = new Set(state.known);
    pending.packages.forEach(p => known.add(p));
    save(statePath, { ...state, known: [...known].sort(), updatedAt: new Date().toISOString() });
    unlinkSync(pendingPath);
    out(`已确认上传 ${pending.packages.length} 个依赖版本；历史总数 ${known.size}`);
  } else {
    out('用法：node pack-incremental.mjs --init | --pack | --confirm YES');
    process.exitCode = 1;
  }
} catch (err) {
  console.error(`错误：${err.message}`);
  process.exitCode = 1;
}
