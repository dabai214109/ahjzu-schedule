"""
开发预览服务器：无需安装 Flask，直接预览前端界面和示例课表。

用法:
    python preview_server.py [端口]     # 默认 8000

访问:
    http://127.0.0.1:8000/?demo=1      # 直接查看示例课表
    http://127.0.0.1:8000/             # 登录页（预览模式下登录不可用）
"""
import http.server
import socketserver
import os
import sys
import json
from urllib.parse import urlparse

ROOT = os.path.dirname(os.path.abspath(__file__))


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=ROOT, **kw)

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/":
            self.path = "/templates/index.html"
        elif path == "/api/config":
            return self._json_out({"ok": True, "term": "202701", "version": 2})
        elif path == "/api/schedule":
            return self._json_out({"ok": False, "msg": "预览模式：请使用 ?demo=1 查看示例课表"})
        return super().do_GET()

    def _json_out(self, obj):
        body = json.dumps(obj).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8000
    with socketserver.TCPServer(("", port), Handler) as httpd:
        print(f"预览服务已启动: http://127.0.0.1:{port}/?demo=1")
        print("按 Ctrl+C 停止")
        httpd.serve_forever()
