#!/usr/bin/env node
/* 版本号唯一来源：android-app/VERSION（内容形如 1.2.0，不带 v 前缀）
 *
 * ── 版本号规则（2026-09 定）─────────────────────────────────
 *   三段式「主.次.修」，其中**次版本与修订号都只能是 0-9**：
 *     1.2.0 → 1.2.1 → … → 1.2.9 → 1.3.0 → … → 1.9.9 → 2.0.0
 *   也就是：1.2.x 系列最大是 1.2.9；1.x 系列最大是 1.9.9；之后进位到 2.0.0。
 *   没特殊说明时按此规则自动递增（node .github/scripts/version.js --next 看下一个，
 *   --bump 直接写进 VERSION）。
 *
 * ── 为什么用文件而不是 GITHUB_RUN_NUMBER ─────────────────────
 *   CI 运行编号不能表达「这是功能版还是修 bug 版」，而且每次 push 都会变，
 *   导致同一个版本对用户呈现出十几个不同版本号。这里改为显式维护。
 *
 * versionCode 由三段式换算（安卓要求它是单调递增的整数）：
 *   主版本 * 10000 + 次版本 * 100 + 修订号
 *   1.2.9 → 10209，1.9.9 → 10909，2.0.0 → 20000（严格递增，不会撞车）
 *
 * 用法（命令行）：
 *   node .github/scripts/version.js --name                     → 1.2.0
 *   node .github/scripts/version.js --code                     → 10200
 *   node .github/scripts/version.js --next                     → 1.2.1（只算不写）
 *   node .github/scripts/version.js --bump                     → 写入 VERSION 并打印新版本
 *   node .github/scripts/version.js --check                    → 校验格式
 *   node .github/scripts/version.js --write-json 路径 --notes "说明"
 *                                                              → 生成 Gitee 的 version.json
 *
 * 用法（代码里 require，供 .verify/test-release.js 直接调用，避免 spawn 子进程）：
 *   const { parseVersion, readVersion, nextVersion } = require('./.github/scripts/version.js');
 */
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const VERSION_FILE = path.join(ROOT, 'android-app', 'VERSION');

/* 正式通道的固定下载地址：APK 文件名恒定，链接永远不变 */
const APK_URL = 'https://gitee.com/dabai214109/ahjzu-schedule/raw/master/kcb.apk';

/* 纯函数：把 "1.2.0" 解析成 { name, code }，格式或范围不对直接抛错 */
function parseVersion(raw) {
  const text = String(raw == null ? '' : raw).trim();
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(text);
  if (!m) {
    throw new Error('android-app/VERSION 必须形如 1.2.0（三段数字、不带 v 前缀），当前是：' + JSON.stringify(text));
  }
  const major = +m[1], minor = +m[2], patch = +m[3];
  if (minor > 9 || patch > 9) {
    throw new Error('次版本和修订号只能是 0-9（1.2.x 最大 1.2.9；1.x 最大 1.9.9，之后进位到 2.0.0）：' + text);
  }
  return { name: major + '.' + minor + '.' + patch, code: major * 10000 + minor * 100 + patch };
}

function readVersion() {
  let raw;
  try {
    raw = fs.readFileSync(VERSION_FILE, 'utf8');
  } catch (e) {
    throw new Error('读不到 android-app/VERSION：' + e.message);
  }
  return parseVersion(raw);
}

/* 纯函数：按规则算下一个版本（修 +1；到 9 就进位） */
function nextVersion(name) {
  const cur = parseVersion(name == null ? readVersion().name : name);
  let major = Math.floor(cur.code / 10000);
  let minor = Math.floor((cur.code % 10000) / 100);
  let patch = cur.code % 100;
  if (patch < 9) patch += 1;
  else if (minor < 9) { patch = 0; minor += 1; }
  else { patch = 0; minor = 0; major += 1; }
  return parseVersion(major + '.' + minor + '.' + patch).name;
}

/* 生成 Gitee 上的 version.json（App 的「检查更新」就读它） */
function writeVersionJson(out, notes) {
  const v = readVersion();
  const data = {
    versionCode: v.code,
    versionName: v.name,
    url: APK_URL,
    notes: notes || ('我的课表 v' + v.name)
  };
  const target = path.resolve(out);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, JSON.stringify(data, null, 2) + '\n');
  return data;
}

module.exports = {
  parseVersion, readVersion, nextVersion, writeVersionJson,
  versionCodeOf: (n) => parseVersion(n).code,
  VERSION_FILE, APK_URL
};

/* ── 命令行入口 ───────────────────────────────────────────── */
if (require.main === module) {
  const arg = (name, fallback) => {
    const i = process.argv.indexOf(name);
    return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
  };
  try {
    const v = readVersion();
    if (process.argv.includes('--bump')) {
      const next = nextVersion(v.name);
      fs.writeFileSync(VERSION_FILE, next + '\n');
      console.log('版本号 v' + v.name + ' → v' + next + '（versionCode ' + parseVersion(next).code + '）');
    } else if (process.argv.includes('--next')) {
      console.log(nextVersion(v.name));
    } else if (process.argv.includes('--check')) {
      console.log('版本号 ' + v.name + '（versionCode ' + v.code + '）格式正确');
    } else if (process.argv.includes('--code')) {
      console.log(String(v.code));
    } else if (process.argv.includes('--write-json')) {
      const data = writeVersionJson(arg('--write-json'), arg('--notes', ''));
      console.log('已生成 version.json → v' + data.versionName + ' (code ' + data.versionCode + ')');
    } else {
      /* 默认输出版本名，方便 shell 直接 $(node version.js) 取用 */
      console.log(v.name);
    }
  } catch (e) {
    console.error(e.message);
    process.exit(1);
  }
}
