# 📅 课表自动获取工具

安徽建筑大学研究生系统课表自动获取工具，手机浏览器访问即可查看。

## 功能

- 🔐 SSO 统一认证登录（自动处理 WebVPN session）
- 📋 自动获取研究生系统课表数据
- 📱 手机端适配的课表展示界面
- 🎨 课程颜色区分，点击查看详情
- ⏰ 显示当天高亮、节次时间

## 技术栈

- **后端**: Python Flask
- **前端**: 原生 HTML/CSS/JS（移动端适配）
- **认证**: CAS SSO + WebVPN session 管理

## 使用方法

### 1. 安装依赖

```bash
pip install -r requirements.txt
```

### 2. 启动服务

```bash
python app.py
```

### 3. 手机访问

确保手机和电脑在同一局域网，浏览器打开：

```
http://<电脑IP>:5000
```

> 💡 Windows 查看 IP：`ipconfig` → 找到局域网 IPv4 地址

### 4. 登录

输入学号和密码，点击登录即可查看课表。

## API 说明

| 端点 | 方法 | 说明 |
|------|------|------|
| `/` | GET | 手机端课表页面 |
| `/api/login` | POST | SSO 登录 `{username, password}` |
| `/api/schedule` | GET | 获取课表（需登录） |
| `/api/schedule/raw` | GET | 获取原始 API 数据（调试用） |

## 配置

`app.py` 顶部可修改：

- `DEFAULT_TERM` — 学期代码（如 `202701`）
- `SSO_LOGIN_URL` — SSO 登录地址
- `WEBVPN_BASE` — WebVPN 基础地址

## 课表数据格式

API 返回 JSON：

```json
{
  "rows": [
    {
      "jcid": 41,
      "sjbz": "上午",
      "mc": "上午1",
      "z1": null,
      "z2": "<br/>课程名[1-8周] 教师[教室]",
      ...
    }
  ],
  "week": "Tuesday"
}
```

- `z1`~`z7` = 周一~周日
- 课程格式：`课程名班级[周次] 教师[教室]`

## License

MIT
