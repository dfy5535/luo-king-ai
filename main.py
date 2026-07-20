#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
luo_king_server - 视觉驱动的游戏智能体（云端版）
===================================================
设计者：Operator
实现者：KIRA

架构：
  ┌────────────── 云服务器 ──────────────┐
  │  SenseNova Vision → Memory →        │
  │  Battle Engine → Strategy → Rule    │
  │  WebSocket API + 监控后台            │
  └──────────────┬───────────────────────┘
                 │ WebSocket
        ┌────────┴────────┐
        │  Android APK    │
        │  截图→上传→点击 │
        └─────────────────┘

启动方式：
  python main.py server        # 启动云服务器（完整AI）
  python main.py test          # 启动测试模式（固定动作，不依赖AI）
  python main.py status        # 查看服务器状态
"""

import sys
import logging


def main():
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s [%(name)s] %(levelname)s: %(message)s',
        datefmt='%H:%M:%S'
    )
    
    if len(sys.argv) < 2:
        print("使用方式:")
        print("  python main.py server        # 启动WebSocket云服务器")
        print("  python main.py client        # 启动手机终端")
        print("  python main.py status        # 查看服务器状态")
        return
    
    cmd = sys.argv[1]
    
    if cmd == "server":
        from luo_king_server.server.gateway import run_server
        import asyncio
        asyncio.run(run_server())
    
    elif cmd == "test":
        from luo_king_server.server.test_gateway import run_test_server
        import asyncio
        asyncio.run(run_test_server())
    
    elif cmd == "status":
        import requests
        port = "8766"
        try:
            r = requests.get(f"http://localhost:{port}/status", timeout=5)
            print(r.json())
        except Exception as e:
            print(f"无法连接服务器: {e}")
    
    else:
        print(f"未知命令: {cmd}")


if __name__ == "__main__":
    main()
