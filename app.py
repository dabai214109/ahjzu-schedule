"""
课表自动获取工具 - 安徽建筑大学研究生系统
Flask 后端：SSO 登录 + 课表 API + 手机网页前端
"""
import os
import re
import json
import secrets
import logging
from flask import Flask, render_template, request, jsonify, session, redirect, url_for
import requests
from bs4 import BeautifulSoup

app = Flask(__name__)
# 密钥：优先从环境变量 SECRET_KEY 读取；否则每次启动随机生成（重启后需重新登录）
app.secret_key = os.environ.get("SECRET_KEY") or secrets.token_hex(32)

logging.basicConfig(level=logging.INFO)
log = logging.getLogger(__name__)

# ── 配置 ──────────────────────────────────────────────
SSO_LOGIN_URL = "https://sso.webvpn.ahjzu.edu.cn/sso/login"
PORTAL_BASE = "https://portal.ahjzu.edu.cn"
WEBVPN_BASE = "https://219-231-15-133.webvpn.ahjzu.edu.cn"
GRADUATE_APP = "/gmis5"

# 课表 API 路径（已确认）
COURSE_API_PATH = "/student/pygl/py_kbcx_ew"

# 学期代码（可通过环境变量 KCB_TERM 覆盖，或前端设置页修改后随请求传入）
DEFAULT_TERM = os.environ.get("KCB_TERM", "202701")

WEEK_MAP = {1: "周一", 2: "周二", 3: "周三", 4: "周四",
            5: "周五", 6: "周六", 7: "周日"}


# ── SSO 登录 ──────────────────────────────────────────
class AhjzuClient:
    """封装安建大 SSO + WebVPN + 研究生系统的请求"""

    def __init__(self):
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                          "AppleWebKit/537.36 (KHTML, like Gecko) "
                          "Chrome/120.0.0.0 Safari/537.36",
        })
        self.logged_in = False
        self.session_token = None  # webvpn S(...) token

    def login(self, username: str, password: str) -> dict:
        """
        执行 SSO 登录流程。
        返回 {"ok": True/False, "msg": "..."}
        """
        try:
            # Step 1: GET 登录页面，获取 cookies 和隐藏字段
            login_page_url = (
                f"{SSO_LOGIN_URL}?service="
                f"{PORTAL_BASE}/user/simpleSSOLogin"
            )
            resp = self.session.get(login_page_url, timeout=15)
            resp.raise_for_status()

            soup = BeautifulSoup(resp.text, "html.parser")

            # 提取隐藏表单字段（CAS 常见的 lt, execution, _eventId）
            form_data = {}
            for inp in soup.find_all("input", attrs={"type": "hidden"}):
                name = inp.get("name")
                val = inp.get("value", "")
                if name:
                    form_data[name] = val

            # 添加凭证
            form_data["username"] = username
            form_data["password"] = password

            # Step 2: POST 登录
            resp = self.session.post(
                login_page_url, data=form_data,
                timeout=15, allow_redirects=True
            )

            # 检查是否登录成功
            if "portal.ahjzu.edu.cn" in resp.url or resp.status_code == 200:
                if "密码错误" in resp.text or "用户名不存在" in resp.text:
                    return {"ok": False, "msg": "用户名或密码错误"}
                self.logged_in = True
                log.info("SSO 登录成功，当前 URL: %s", resp.url)
                return {"ok": True, "msg": "登录成功"}
            else:
                return {"ok": False, "msg": f"登录失败，跳转到: {resp.url}"}

        except requests.RequestException as e:
            log.error("登录请求失败: %s", e)
            return {"ok": False, "msg": f"网络错误: {e}"}

    def _ensure_webvpn_session(self):
        """访问研究生系统首页，获取 webvpn session token"""
        if self.session_token:
            return

        # 访问研究生系统入口页（课表查询页面）
        ref = f"{WEBVPN_BASE}{GRADUATE_APP}/student/pygl/xskbcx"
        resp = self.session.get(ref, timeout=15, allow_redirects=True)
        log.info("WebVPN 入口页: %s", resp.url)

        # 从 URL 中提取 S(...) token
        match = re.search(r'\(S\(([^)]+)\)\)', resp.url)
        if match:
            self.session_token = match.group(1)
            log.info("获取到 webvpn session: %s", self.session_token)
        else:
            match = re.search(r'\(S\(([^)]+)\)\)', resp.text)
            if match:
                self.session_token = match.group(1)
                log.info("从页面提取到 webvpn session: %s", self.session_token)

    def _webvpn_url(self, path: str) -> str:
        """构建带 session token 的 webvpn URL"""
        if self.session_token:
            return f"{WEBVPN_BASE}{GRADUATE_APP}/(S({self.session_token})){path}"
        return f"{WEBVPN_BASE}{GRADUATE_APP}{path}"

    def fetch_schedule(self, term_code: str = None) -> dict:
        """
        获取课表数据。
        返回 {"ok": True, "data": [...]} 或 {"ok": False, "msg": "..."}
        """
        if not self.logged_in:
            return {"ok": False, "msg": "未登录"}

        if not term_code:
            term_code = DEFAULT_TERM

        try:
            self._ensure_webvpn_session()

            if not self.session_token:
                return {"ok": False, "msg": "无法获取 WebVPN 会话，请检查网络"}

            # 构建课表 API URL
            api_url = self._webvpn_url(COURSE_API_PATH)
            log.info("请求课表 API: %s", api_url)

            # POST 请求，模拟浏览器 AJAX 调用
            headers = {
                "X-Requested-With": "XMLHttpRequest",
                "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8",
                "Accept": "application/json, text/javascript, */*; q=0.01",
                "Referer": self._webvpn_url("/student/pygl/xskbcx"),
            }
            body = f"kblx=xs&termcode={term_code}"

            resp = self.session.post(
                api_url, data=body, headers=headers, timeout=15
            )

            if resp.status_code == 200:
                try:
                    data = resp.json()
                    if "rows" in data:
                        return {"ok": True, "data": data}
                    else:
                        return {"ok": False, "msg": f"API 返回异常: {list(data.keys())}"}
                except json.JSONDecodeError:
                    log.warning("课表 API 返回非 JSON，前 200 字: %s", resp.text[:200])
                    return {"ok": False, "msg": "API 返回格式异常"}
            else:
                return {"ok": False, "msg": f"HTTP {resp.status_code}"}

        except requests.RequestException as e:
            log.error("获取课表失败: %s", e)
            return {"ok": False, "msg": f"网络错误: {e}"}


# ── 课表数据解析 ─────────────────────────────────────
def parse_courses(api_data: dict) -> list:
    """
    将 API 返回的 rows 解析为课程列表。
    输入: {"rows": [...], "week": "Tuesday"}
    输出: [{"name": "...", "teacher": "...", "room": "...",
            "day": 1, "slot": 1, "weeks": "1-8", "period": "上午"}, ...]
    """
    courses = []
    rows = api_data.get("rows", [])

    for row in rows:
        period = row.get("sjbz", "")  # 上午/下午/晚上
        slot_name = row.get("mc", "").strip()  # 如"上午1"（可能带尾随空格）
        jcid = row.get("jcid", 0)

        # z1~z7 对应周一~周日
        for day_idx in range(1, 8):
            cell = row.get(f"z{day_idx}", "")
            if not cell or not cell.strip():
                continue

            # 解析课程信息
            # 格式: \n课程名班级[周次] 教师[教室]\n
            # 可能有多门课（换行分隔）
            cell_text = cell.replace("<br/>", "\n").replace("<br>", "\n").strip()
            lines = [l.strip() for l in cell_text.split("\n") if l.strip()]

            for line in lines:
                course = parse_course_line(line)
                if course:
                    course["day"] = day_idx
                    course["day_name"] = WEEK_MAP.get(day_idx, "")
                    course["slot"] = jcid
                    course["slot_name"] = slot_name
                    course["period"] = period
                    courses.append(course)

    return courses


def parse_course_line(line: str) -> dict:
    """
    解析单行课程信息（安建大研究生系统格式）。
    标准格式: 课程名班级[周次] 教师[教室]
    示例:     新时代中国特色社会主义理论与实践4班[1-8周] 周叶君[5102]
    周次支持: 1-8 / 2 / 1-8,10-16（逗号分隔多段）
    """
    line = line.strip()
    weeks_re = r'(\d+(?:-\d+)?(?:[,，]\s*\d+(?:-\d+)?)*)'

    # 格式1: 课程名[周次] 教师[教室]
    m = re.match(r'^(.+?)\[' + weeks_re + r'周?\]\s*(.*?)\s*\[([^\[\]]+)\]$', line)
    if m:
        return {
            "name": m.group(1).strip(),
            "weeks": m.group(2).replace("，", ",").replace(" ", ""),
            "teacher": m.group(3).strip(),
            "room": m.group(4).strip(),
        }

    # 格式2: 课程名[周次] 教师（无教室）
    m = re.match(r'^(.+?)\[' + weeks_re + r'周?\]\s*(\S.*)$', line)
    if m:
        return {
            "name": m.group(1).strip(),
            "weeks": m.group(2).replace("，", ",").replace(" ", ""),
            "teacher": m.group(3).strip(),
            "room": "",
        }

    # 格式3: 课程名[周次]
    m = re.match(r'^(.+?)\[' + weeks_re + r'周?\]$', line)
    if m:
        return {
            "name": m.group(1).strip(),
            "weeks": m.group(2).replace("，", ",").replace(" ", ""),
            "teacher": "",
            "room": "",
        }

    return None


# ── Flask 路由 ───────────────────────────────────────
@app.route("/")
def index():
    return render_template("index.html")


@app.route("/api/login", methods=["POST"])
def api_login():
    data = request.get_json()
    username = data.get("username", "").strip()
    password = data.get("password", "").strip()

    if not username or not password:
        return jsonify({"ok": False, "msg": "请输入用户名和密码"})

    client = AhjzuClient()
    result = client.login(username, password)

    if result["ok"]:
        # 存储 client 到 session（生产环境应用 Redis）
        session["client"] = {
            "cookies": dict(client.session.cookies),
            "logged_in": True,
            "session_token": client.session_token,
        }
        session["username"] = username

    return jsonify(result)


@app.route("/api/schedule", methods=["GET"])
def api_schedule():
    client_data = session.get("client")
    if not client_data or not client_data.get("logged_in"):
        return jsonify({"ok": False, "msg": "请先登录"})

    # 学期代码：请求参数 > 环境变量 > 默认值
    term_code = request.args.get("term") or DEFAULT_TERM

    # 重建 client
    client = AhjzuClient()
    client.session.cookies.update(client_data.get("cookies", {}))
    client.logged_in = True
    client.session_token = client_data.get("session_token")

    result = client.fetch_schedule(term_code)

    if result["ok"]:
        courses = parse_courses(result["data"])
        # 节次结构（jcid/mc/sjbz），供前端动态生成时间轴
        slots = [
            {"jcid": r.get("jcid"), "mc": r.get("mc", "").strip(), "sjbz": r.get("sjbz", "")}
            for r in result["data"].get("rows", [])
        ]
        return jsonify({"ok": True, "courses": courses, "slots": slots,
                        "week": result["data"].get("week", ""), "term": term_code})

    return jsonify(result)


@app.route("/api/schedule/raw", methods=["GET"])
def api_schedule_raw():
    """直接返回原始 JSON（调试用）"""
    client_data = session.get("client")
    if not client_data or not client_data.get("logged_in"):
        return jsonify({"ok": False, "msg": "请先登录"})

    term_code = request.args.get("term") or DEFAULT_TERM

    client = AhjzuClient()
    client.session.cookies.update(client_data.get("cookies", {}))
    client.logged_in = True
    client.session_token = client_data.get("session_token")

    return jsonify(client.fetch_schedule(term_code))


@app.route("/api/logout", methods=["POST"])
def api_logout():
    """退出登录，清除会话"""
    session.clear()
    return jsonify({"ok": True, "msg": "已退出登录"})


@app.route("/api/config", methods=["GET"])
def api_config():
    """前端配置信息"""
    return jsonify({"ok": True, "term": DEFAULT_TERM, "version": 2})


if __name__ == "__main__":
    debug = os.environ.get("FLASK_DEBUG", "0") == "1"
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "5000")), debug=debug)
