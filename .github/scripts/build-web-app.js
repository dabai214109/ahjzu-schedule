/* Netlify 构建步骤：把安卓 App 的前端原样复制到 web/app/
   这样展示页就能内嵌「App 版」的真实界面做操作演示（?app=1&demo=1）。

   为什么用构建复制而不提交副本：
   android-app/www/index.html 是 App 的唯一源文件，仓库里不再维护第二份拷贝，
   避免改动时两边不同步。生成物 web/app/ 已写进 .gitignore，不进版本库。

   App 前端在普通浏览器里是安全的：只有 window.Capacitor 存在（真机）时才会调用
   原生插件，?app=1 只负责套上 APK 专属样式，不会伪造也不调用任何原生能力。
*/
'use strict';
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', '..');
const SRC = path.join(ROOT, 'android-app', 'www');
const DST = path.join(ROOT, 'web', 'app');
const FILES = ['index.html', 'app-override.css'];

fs.mkdirSync(DST, { recursive: true });

FILES.forEach(function (name) {
  const from = path.join(SRC, name);
  if (!fs.existsSync(from)) {
    console.error('缺少源文件：' + from);
    process.exit(1);
  }
  fs.copyFileSync(from, path.join(DST, name));
  console.log('  copied ' + name + '  ' + Math.round(fs.statSync(from).size / 1024) + ' KB');
});

console.log('web/app/ 已生成（' + FILES.length + ' 个文件）');
