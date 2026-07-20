"""
BattlePipeline — 战斗流水线
upload → Vision → BattleState → Memory → Battle Engine → Strategy → Rule → Action

分层设计，每层可单独测试、替换、升级。
当前为测试模式：收到 upload 直接返回固定 tap 动作。
"""
import json
import logging
from typing import Optional

log = logging.getLogger("battle_pipeline")


class BattlePipeline:
    """
    战斗流水线
    FastAPI 只负责协议，不负责 AI。
    BattlePipeline 是 AI 的入口和出口。
    """

    # ─── 测试模式标志 ───
    test_mode: bool = True

    async def process(self, device_id: str, session_id: str, image_base64: str) -> dict:
        """
        处理 upload 消息
        返回 action 字典
        """
        if self.test_mode:
            return await self._test_mode(device_id, session_id, image_base64)

        # ─── 正式模式（尚未实现，按层逐步接入） ───
        # battle_state = await self._vision.parse(image_base64)
        # self._memory.remember(battle_state)
        # action = await self._engine.decide(battle_state)
        # action = await self._strategy.refine(action, battle_state)
        # action = await self._rule.validate(action, battle_state)
        return await self._test_mode(device_id, session_id, image_base64)

    async def _test_mode(self, device_id: str, session_id: str, image_base64: str) -> dict:
        """测试模式：固定返回 tap 动作"""
        log.info(f"[pipeline/test] device={device_id} session={session_id} image_size={len(image_base64)}B")
        return {
            "type": "action",
            "action_type": "tap",
            "coordinate": [540, 960],
            "delay_ms": 500,
            "session_id": session_id
        }


# 全局单例
pipeline = BattlePipeline()