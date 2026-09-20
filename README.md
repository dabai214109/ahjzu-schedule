# 📅 我的课表 · 安徽建筑大学

安徽建筑大学研究生课表工具，两种形态：

| 形态 | 说明 | 适用 |
|------|------|------|
| 📱 **Android App**（推荐） | 手机独立安装，直连学校系统，无需电脑 | 日常使用 |
| 🌐 网页版（Flask） | 电脑开服务、浏览器访问 | 调试 / 备用 |

---

## 📱 Android App

### 下载安装

1. 打开仓库 **Releases** 页面：https://github.com/dabai214109/ahjzu-schedule/releases
2. 下载最新版 APK（文件名形如 `kcb-1.2.0.apk`）
3. 手机上安装（首次需允许「未知来源应用」），之后更新直接覆盖安装

> 🇨🇳 **国内下载（免登录、不限速、永远是最新版）**：<https://gitee.com/dabai214109/ahjzu-schedule/raw/master/kcb.apk>
> 同一仓库里还有 `version.json`（版本信息，供后续 App 内「检查更新」使用）。

### 使用（v1.1：原生网页同步课表）

App 打开就是课表页，底部两个 Tab：**课程表 / 设置**。首次同步课表：

1. 底部切到 **设置** → 点 **打开信息门户同步**（或课表页空状态的「登录信息门户并同步课表」）
2. 弹出学校**信息门户登录页**（`sso.webvpn.ahjzu.edu.cn`）：正常登录，学号 / 密码 / 验证码都在学校页面输入，App 不碰账号密码
3. 登录后点 **研究生系统** → **培养管理 → 学生课表查询**
4. 页面请求课表接口的瞬间，App 自动捕获 `py_kbcx_ew` 响应并映射到课表页，窗口顶部显示「✓ 已捕获课表」后自动关闭
5. 之后打开 App 直接看课表（本机缓存），左右滑动 / 方向键切换周次

网络窗口顶部一直显示**当前域名**，便于确认走到了哪一步。再次同步时 Cookie 还在，可直接用设置页的 **「已登录过？直接进研究生系统 →」** 省掉门户那一步。

电脑上抓到过课表 JSON？设置 → **手动导入课表数据** 粘贴即可，无需登录。

### 云端编译（本地零环境）

App 编译**完全在 GitHub Actions 上进行**，本地不需要装 Node/Java/Android SDK：

- 推送到 `main`（改动 `android-app/**` 或 `.github/**` 时）自动编译
- 手动编译：仓库 **Actions** → **Build Android APK** → **Run workflow**
- 编译产物自动发布到 **Releases**，并附在 Actions Artifacts 里

流程：CI 拉取代码 → `npm install` + `npx cap add android` 生成 Android 工程 → 替换图标/应用名/版本号 → 注入原生 WebView 插件 → `gradlew assembleRelease` → 用正式密钥签名 → 发布 Release。

### App 技术要点

- **Capacitor** 打包 `android-app/www/index.html`（单文件前端，与网页版同款界面）
- **原生 WebView 插件**（`.github/android-plugin/KcbWebviewPlugin.java`）：打开学校信息门户真实网页，注入 JS 拦截 `py_kbcx_ew` 的 XHR/fetch 响应，回传前端解析
  - 入口必须是**信息门户登录页** `https://sso.webvpn.ahjzu.edu.cn/sso/login?service=https://portal.ahjzu.edu.cn/user/simpleSSOLogin`；直接开 WebVPN 里的研究生系统直链登录框无反应（已实测）
  - 注入时机三重保障：`androidx.webkit` 文档开始注入（覆盖全部 frame）→ 加载 10% 兜底 → 页面完成；并改写 `target=_blank` / `window.open` 为本窗口跳转，避免点链接没反应
  - 登录、验证码、Cookie 全部由学校页面和系统 WebView 自己处理，App 不保存任何账号密码
- 课表数据解析（`z1~z7` / `jcid` / 一格多课 / 连堂合并）内置前端，真实抓包数据回归测试 38 项
- 离线可用：课表缓存在本机，断网也能看

### 签名密钥（重要）

- 正式签名密钥已存入仓库 **Secrets**（`KCB_KEYSTORE_BASE64` / `KCB_KEYSTORE_PASSWORD` / `KCB_KEY_ALIAS`）
- 本地备份：`.verify/kcb-release.pfx` + `.verify/keystore-info.txt`（含密码；此目录不入库）
- ⚠️ 密钥丢失 = 无法覆盖更新（只能卸载重装），请把 `.verify/` 目录另外备份到网盘/U盘

---

## 🌐 网页版（Flask）

### 运行

```bash
python -m venv venv
venv\Scripts\activate          # Windows
pip install -r requirements.txt
python app.py
```

浏览器访问 `http://127.0.0.1:5000`，或手机连同一局域网访问 `http://电脑IP:5000`。

免安装预览界面（不需要 Flask）：`python preview_server.py 8000` → `http://127.0.0.1:8000/?demo=1`

### 环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `SECRET_KEY` | 随机生成 | Flask 会话密钥，固定后重启不需重新登录 |
| `KCB_TERM` | `202701` | 默认学期代码（前端设置可覆盖） |
| `FLASK_DEBUG` | `0` | `1` 开启调试模式 |
| `PORT` | `5000` | 监听端口 |

---

## 使用说明（两种形态通用）

- **首次使用**：登录后进「设置」，核对「开始上课时间」（第 1 周第一天的日期），周次才能算准
- **每学期**：设置里修改「学期代码 termcode」（如 `202701`），然后点 ⟳ 刷新
- **单双周**：默认只显示本周课程；「显示非本周课程」开关可看全部（半透明）
- **作息时间**：设置 →「课表时间设置」可修改每节上下课时间（默认为近似值）
- **周次切换**：顶部 ‹ › 或点周数打开周选择器，课程按 `1-8` / `2` / `1-8,10-16` 周次真实过滤

## 课表数据格式（安建大研究生系统）

- 请求：`POST /gmis5/(S(...))/student/pygl/py_kbcx_ew`，参数 `kblx=xs&termcode=202701`
- 返回 `rows[]`：`jcid`（节次）、`sjbz`（上午/下午/晚上）、`mc`（节次名）、`z1~z7`（周一~周日）
- 课程单元：`课程名班级[周次] 教师[教室]`，例：`新时代中国特色社会主义理论与实践4班[1-8周] 周叶君[5102]`

## 目录结构

```
├── android-app/              # 📱 App 版（GitHub Actions 云端编译）
│   ├── www/index.html        #   App 前端（内置登录+课表获取，无服务器）
│   ├── icons/                #   各密度启动图标
│   ├── package.json          #   Capacitor 依赖（CI 中安装）
│   └── capacitor.config.json #   App 配置（启用 CapacitorHttp 绕过 CORS）
├── .github/
│   ├── workflows/build-android.yml   # 云端编译工作流
│   └── scripts/patch-android.sh      # CI 中定制 Android 工程
├── app.py                    # 🌐 网页版 Flask 后端
├── preview_server.py         # 免依赖预览服务器
├── requirements.txt
├── templates/index.html      # 网页版前端
└── static/                   # 网页版 PWA 资源（manifest/sw/图标）
```

## ⚠️ 已知事项（待真机联调）

1. **SSO 真实登录尚未验证**：登录流程基于抓包分析编写，需实际账号联调；如遇验证码需增加处理
2. **termcode 需要每学期核实**：默认 `202701` 来自抓包
3. **节次时间为近似值**：可在设置中修正

## License

本项目采用 **[PolyForm Noncommercial License 1.0.0](LICENSE)**（原文见 `LICENSE`）：

- ✅ 允许**个人**使用、学习、修改、分享；非营利组织、教育机构、公益与政府机构的使用也属于许可范围
- ❌ **禁止任何商业用途**，包括但不限于：二次打包后上架应用商店、内置广告或收费、作为付费服务的一部分销售

> 严格来说这**不是** OSI 定义的开源许可证（它限制了使用领域），准确的表述是**源码公开**。宣传时请用「源码公开」，不要写「开源」。

## ⚠️ 关于盗版

- 本 App **未在任何应用商店上架**，也没有任何付费版本
- **唯一官方下载地址**：
  - 国内（Gitee，免登录、不限速）：<https://gitee.com/dabai214109/ahjzu-schedule/raw/master/kcb.apk>
  - GitHub Releases：<https://github.com/dabai214109/ahjzu-schedule/releases>
- 如果你在某应用商店里**付费**买到本 App，那是假冒版本，请及时申请退款
- 转载或二次分发时，必须保留 `LICENSE` 开头 `Required Notice:` 的署名行（许可证的强制要求）
