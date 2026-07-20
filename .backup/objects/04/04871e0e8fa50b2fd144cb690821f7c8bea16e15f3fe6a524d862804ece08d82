"""
luo_king_server/agents/strategy_agent.py
=========================================
策略Agent——与之前一致，但多了一个 skill_index 映射。
"""

from __future__ import annotations
from typing import Optional, List, Dict, Any
import logging
import json
from ..core.models import BattleState, BattleAction, BattleActionType
from ..engine.battle_engine import BattleRecommendation

logger = logging.getLogger(__name__)

class StrategyAgent:
    def __init__(self, llm_client=None, model: str = "gemini-2.5-flash"):
        self.llm = llm_client
        self.model = model
        self.conversation_history: List[Dict] = []
        self.max_history = 10
    
    def decide(self, state: BattleState, recommendation: BattleRecommendation,
               memory_context: str = "") -> BattleAction:
        if recommendation.suggested_action:
            action = recommendation.suggested_action
            action.skill_index = self._skill_name_to_index(state, action.target)
            return action
        
        recommended = [s for s in recommendation.skill_scores if s.is_recommended]
        if len(recommended) == 1 and recommended[0].score >= 90:
            action = BattleAction(BattleActionType.技能, target=recommended[0].skill_name,
                confidence=0.9, reasoning=f"引擎唯一推荐: {recommended[0].reasoning}")
            action.skill_index = self._skill_name_to_index(state, recommended[0].skill_name)
            return action
        
        return self._llm_decide(state, recommendation, memory_context)
    
    def _llm_decide(self, state, rec, memory_context) -> BattleAction:
        prompt = self._build_prompt(state, rec, memory_context)
        if self.llm:
            try:
                response = self.llm.chat.completions.create(
                    model=self.model,
                    messages=[{"role":"system","content":self._system_prompt()},{"role":"user","content":prompt}],
                    temperature=0.3, max_tokens=500, response_format={"type":"json_object"},
                )
                result = json.loads(response.choices[0].message.content)
                action = self._parse_llm_response(result)
                action.skill_index = self._skill_name_to_index(state, action.target)
                return action
            except Exception as e:
                logger.error(f"LLM决策失败: {e}")
                return self._fallback(state, rec)
        return self._fallback(state, rec)
    
    def _system_prompt(self) -> str:
        return """你是洛克王国对战AI的博弈决策层。
你的任务：从引擎推荐的技能中，选择最优的博弈策略。
你不需要计算伤害（Battle Engine已经算好了），只需要考虑对方会怎么应对。
输出JSON格式：{"action_type":"skill"|"swap","target":"技能名","reasoning":"博弈分析","confidence":0.0-1.0}"""
    
    def _build_prompt(self, state, rec, memory_context) -> str:
        lines = ["=== 当前战斗状态 ===", state.to_prompt_context(), ""]
        if memory_context: lines += ["=== 战斗记忆 ===", memory_context, ""]
        lines.append(rec.to_prompt_context())
        lines.append("\n请从以上技能中选择最优方案，考虑对方可能的应对。")
        return "\n".join(lines)
    
    def _parse_llm_response(self, result: Dict) -> BattleAction:
        t = result.get("action_type", "skill")
        type_map = {"skill":BattleActionType.技能,"swap":BattleActionType.换宠,"item":BattleActionType.使用道具,"flee":BattleActionType.逃跑}
        return BattleAction(type_map.get(t, BattleActionType.技能), target=result.get("target",""),
            confidence=result.get("confidence",0.5), reasoning=result.get("reasoning",""))
    
    def _fallback(self, state, rec) -> BattleAction:
        if rec.skill_scores:
            b = rec.skill_scores[0]
            action = BattleAction(BattleActionType.技能, target=b.skill_name, confidence=b.score/100, reasoning=f"降级: {b.reasoning}")
            action.skill_index = self._skill_name_to_index(state, b.skill_name)
            return action
        return BattleAction(BattleActionType.等待, reasoning="无可用动作")
    
    def _skill_name_to_index(self, state: BattleState, skill_name: Optional[str]) -> Optional[int]:
        if not skill_name or not state.my_active: return None
        for i, s in enumerate(state.my_active.skills):
            if s.name == skill_name: return i + 1  # 1-based index
        return None