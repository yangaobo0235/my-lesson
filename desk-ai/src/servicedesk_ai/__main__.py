"""以统一配置启动AI HTTP进程，默认端口28105。"""

import uvicorn

from servicedesk_ai.bootstrap.settings import get_settings


def main() -> None:
    """在显式进程入口启动监听，不在模块导入阶段运行服务。"""
    settings = get_settings()
    uvicorn.run("servicedesk_ai.main:app", host=settings.http_host, port=settings.http_port)


if __name__ == "__main__":
    main()
