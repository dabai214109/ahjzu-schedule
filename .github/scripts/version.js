#!/usr/bin/env node
/* 版本号唯一来源：android-app/VERSION（内容形如 1.2.0，不带 v 前缀）
 *
 * 为什么用文件而不是 GITHUB_RUN_NUMBER：
 *   CI 运行编号不能表达「这是功能版还是修 bug 版」，而且每次 push 都会变，
 *   导致同一个版本对用户呈现出十几个不同版本号。这里改为显式维护：
 *   发布前手动把 VERSION 改成新版本，CI 与 Gitee 的 version.json 都取自它。
 *
 * versionCode 由语义化版本换算（安卓要求它是单调递增的整数）：
 *   主版本 * 10000 + 次版本 * 100 + 修订号     → 1.2.0 = 10200
 *   因此次版本与修订号不能超过 99。
 *
 * 用法（命令行）：
 *   node .github/scripts/version.js --name                     → 1.2.0
 *   node .github/scripts/version.js --code                     → 10200
 *   node .github/scripts/version.js --check                    → 校验格式
 *   node .github/scripts/version.js --write-json 路径 --notes "说明"
 *                                                              → 生成 Gitee 的 version.json
 *
 * 用法（代码里 require，供 .verify/test-release.js 直接调用，避免 spawn 子进程）：
 *   const { parseVersion, readVersion, writeVersionJson } = require('./.github/scripts/version.js');
 */
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const VERSION_FILE = path.join(ROOT, 'android-app', 'VERSION');

/* 正式通道的固定下载地址：APK 文件名恒定，链接永远不变 */
const APK_URL = 'https://gitee.com/dabai214109/ahjzu-schedule/raw/master/kcb.apk';

/* 纯函数：把 "1.2.0" 解析成 { name, code }，格式不对直接抛错 */
function parseVersion(raw) {
  const text = String(raw == null ? '' : raw).trim();
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(text);
  if (!m) {
    throw new Error('android-app/VERSION 必须形如 1.2.0（三段数字、不带 v 前缀），当前是：' + JSON.stringify(text));
  }
  const major = +m[1], minor = +m[2], patch = +m[3];
  if (minor > 99 || patch > 99) {
    throw new Error('次版本与修订号不能超过 99（versionCode 换算要用两位数）：' + text);
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

module.exports = { parseVersion, readVersion, writeVersionJson, versionCodeOf: (n) => parseVersion(n).code, VERSION_FILE, APK_URL };

/* ── 命令行入口 ───────────────────────────────────────────── */
if (require.main === module) {
  const arg = (name, fallback) => {
    const i = process.argv.indexOf(name);
    return i > 0 && process.argv[i + 1] ? process.argv[i + 1] : fallback;
  };
  try {
    const v = readVersion();
    if (process.argv.includes('--check')) {
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
