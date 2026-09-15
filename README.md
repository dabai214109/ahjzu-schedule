# 📅 我的课表 · 安徽建筑大学

安徽建筑大学研究生课表自动获取工具，手机浏览器访问即可查看，可"添加到主屏幕"当 App 用（PWA）。

## 功能

- 🔐 SSO 统一认证登录（自动处理 WebVPN session）
- 📋 自动获取研究生系统课表（`z1~z7` 周一到周日，自动解析课程/教师/教室/周次）
- 📱 小爱课程表风格的周视图界面：连堂合并、午休/晚休分隔、当天高亮
- 🔄 **周次切换**：按学期开始日期自动计算当前周，课程按周次真实过滤（支持单双周）
- 🎨 课程颜色区分，点击查看详情（教师/教室/时间/周次）
- ⚙️ **设置页**：学期开始时间、总周数、当前周数、显示周末、显示非本周课程、termcode、每节上下课时间
- 💾 **离线缓存**：课表数据本地保存，断网也能看（Service Worker + localStorage）
- 📲 **PWA**：添加到主屏幕后全屏运行、有应用图标
- 👀 示例模式：不登录也能预览界面（虚拟数据）

## 技术栈

- **后端**: Python Flask
- **前端**: 原生 HTML/CSS/JS（单文件，移动端适配）
- **认证**: CAS SSO + WebVPN session 管理

## 快速开始

### 方式一：完整运行（Flask）

```bash
python -m venv venv
venv\Scripts\activate          # Windows
pip install -r requirements.txt
python app.py
```

浏览器访问 `http://127.0.0.1:5000`，或手机连同一局域网访问 `http://电脑IP:5000`。

> Windows 查看 IP：`ipconfig` → 局域网 IPv4 地址；如无法访问检查防火墙放行 Python。

### 方式二：免安装预览（不需要 Flask）

```bash
python preview_server.py 8000
```

访问 `http://127.0.0.1:8000/?demo=1` 直接查看示例课表界面。

## 手机上当 App 用（PWA）

1. 电脑运行服务，手机浏览器打开页面
2. **Android**：菜单 →「添加到主屏幕」/「安装应用」
3. **iOS Safari**：分享 →「添加到主屏幕」
4. 之后从桌面图标进入，全屏运行，有独立图标

> 局域网 HTTP 下离线缓存（Service Worker）不可用，属正常现象；课表仍会缓存在 localStorage，打开很快。部署到 HTTPS 后离线能力完整生效。

## 使用说明

- **首次使用**：登录后进「设置」，核对「开始上课时间」（第 1 周第一天的日期），周次才能算准
- **每学期**：在设置里修改「学期代码 termcode」（如 `202701`），然后点顶部 ⟳ 刷新
- **单双周**：默认只显示本周课程；设置里打开「显示非本周课程」可看到全部（半透明）
- **作息时间**：设置 →「课表时间设置」可修改每节上下课时间（默认为近似值）

## API 说明

| 端点 | 方法 | 说明 |
|------|------|------|
| `/` | GET | 手机端课表页面 |
| `/api/login` | POST | SSO 登录 `{username, password}` |
| `/api/schedule` | GET | 获取课表（需登录），可选 `?term=202701` |
| `/api/schedule/raw` | GET | 原始接口数据（调试用） |
| `/api/logout` | POST | 退出登录 |
| `/api/config` | GET | 服务端配置（默认 termcode 等） |

`/api/schedule` 返回：

```json
{
  "ok": true,
  "courses": [
    {"name": "新时代中国特色社会主义理论与实践4班", "weeks": "1-8",
     "teacher": "周叶君", "room": "5102",
     "day": 1, "day_name": "周一", "slot": 41, "slot_name": "上午1", "period": "上午"}
  ],
  "slots": [{"jcid": 41, "mc": "上午1", "sjbz": "上午"}],
  "week": "Tuesday", "term": "202701"
}
```

- `day`: 1~7 = 周一~周日；`slot`: 研究生系统 jcid
- `weeks`: `1-8` / `2` / `1-8,10-16`（多段逗号分隔）

## 环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `SECRET_KEY` | 随机生成 | Flask 会话密钥，固定后重启不需重新登录 |
| `KCB_TERM` | `202701` | 默认学期代码（前端设置可覆盖） |
| `FLASK_DEBUG` | `0` | `1` 开启调试模式 |
| `PORT` | `5000` | 监听端口 |

```powershell
# 生产/长期使用示例
$env:SECRET_KEY = "换成随机长字符串"
python app.py
```

## 目录结构

```
├── app.py               # Flask 后端：SSO 登录 + 课表 API
├── preview_server.py    # 免依赖预览服务器
├── requirements.txt     # Python 依赖
├── templates/
│   └── index.html       # 前端（单文件：登录 + 课表 + 设置 + PWA）
└── static/
    ├── manifest.json    # PWA 清单
    ├── sw.js            # Service Worker（离线缓存）
    └── icon-*.png       # 应用图标
```

## ⚠️ 已知事项（待真机联调）

1. **SSO 真实登录尚未验证**：登录流程基于抓包分析编写，需实际账号联调；如遇验证码需增加处理
2. **termcode 需要每学期核实**：默认 `202701` 来自抓包
3. **节次时间为近似值**：可在设置中修正
4. 会话 cookies 存在 Flask 客户端 session 中，适合个人局域网使用；公网部署需改服务端会话存储 + HTTPS

## License

MIT
