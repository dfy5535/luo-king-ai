"""
BattlePipeline — 可插拔战斗流水线
使用 BasePlugin 基类 + 依赖注入
所有插件操作统一的 BattleContext
"""
import logging
import time
from typing import Optional

from luo_king_server.core.models import BasePlugin, BattleContext

log = logging.getLogger("battle_pipeline")


class BattlePipeline:
    """
    战斗流水线
    通过 add() 注册插件，依赖注入 VisionService/MemoryService 等
    每个插件处理 BattleContext，返回 BattleContext
    """
    def __init__(self):
        self._plugins: list[BasePlugin] = []
        self.test_mode: bool = True

    def add(self, plugin: BasePlugin):
        """添加插件到流水线"""
        self._plugins.append(plugin)
        log.info(f"[add] plugin={plugin.name} (total={len(self._plugins)})")
        if self.test_mode:
            self.test_mode = False

    def remove(self, name: str):
        """移除插件"""
        self._plugins = [p for p in self._plugins if p.name != name]

    async def execute(self, ctx: BattleContext) -> BattleContext:
        """执行流水线——每个插件依次处理 context"""
        if self.test_mode or not self._plugins:
            return await self._fallback(ctx)

        for plugin in self._plugins:
            start = time.time()
            try:
                ctx = await plugin.process(ctx)
                ctx.metrics[f"{plugin.name}_ms"] = (time.time() - start) * 1000
            except Exception as e:
                log.error(f"[execute] plugin={plugin.name}: {e}")
                ctx.errors.append(f"{plugin.name}: {e}")
                break

        return ctx

    async def _fallback(self, ctx: BattleContext) -> BattleContext:
        """测试模式：返回结构化决策（think + decision + execute）"""
        log.info(f"[pipeline/test] device={ctx.device_id} session={ctx.session_id[:8] if ctx.session_id else '?'}")
        ctx.final_action = {
            "type": "decision",
            "think": {
                "stage": "观察战场",
                "reason": "敌方冰龙王在场，我方烈火战神被克制",
                "confidence": 0.87
            },
            "decision": {
                "action": "swap",
                "target": "圣光迪莫",
                "reason": "光系克制龙系，预计造成2倍伤害"
            },
            "execute": {
                "type": "tap",
                "coordinate": [540, 960],
                "delay_ms": 800,
                "skill_index": -1,
                "swap_index": 2,
                "session_id": ctx.session_id
            }
        }
        return ctx

    def plugin_count(self) -> int:
        return len(self._plugins)

    def plugin_names(self) -> list[str]:
        return [p.name for p in self._plugins]


# 全局单例
pipeline = BattlePipeline()