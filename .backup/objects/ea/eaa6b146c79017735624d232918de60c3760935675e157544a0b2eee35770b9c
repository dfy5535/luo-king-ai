"""
luo_king_server/agents/rule_agent.py
=====================================
规则Agent——与之前一致。
"""

from __future__ import annotations
from typing import List, Optional, Dict, Any
from dataclasses import dataclass, field
from ..core.models import BattleState, BattleAction, BattleActionType, StatusCondition

@dataclass
class Rule:
    name: str; check_func: callable
    def check(self, action: BattleAction, state: BattleState) -> bool: return self.check_func(action, state)

@dataclass
class ValidationResult:
    valid: bool; violations: List[str] = field(default_factory=list); suggestion: str = ""

class RuleAgent:
    def __init__(self):
        self.rules = [
            Rule("PP充足", self._check_pp),
            Rule("技能未封印", self._check_sealed),
            Rule("宠物存活", self._check_alive),
            Rule("换宠允许", self._check_swap_allowed),
            Rule("非睡眠状态可用技能", self._check_status_blocks_action),
        ]
    
    def validate(self, action: BattleAction, state: BattleState) -> ValidationResult:
        violations = [r.name for r in self.rules if not r.check(action, state)]
        if not violations: return ValidationResult(valid=True)
        return ValidationResult(valid=False, violations=violations, suggestion=self._suggest_fix(action, violations, state))
    
    def validate_all_actions(self, state: BattleState) -> List[BattleAction]:
        valid = []
        if state.my_active:
            for skill in state.my_active.skills:
                action = BattleAction(BattleActionType.技能, target=skill.name)
                if self.validate(action, state).valid: valid.append(action)
        for pet in state.my_pets:
            if pet != state.my_active and pet.is_alive:
                action = BattleAction(BattleActionType.换宠, target=pet.name)
                if self.validate(action, state).valid: valid.append(action)
        return valid
    
    def _check_pp(self, action, state):
        if action.action_type != BattleActionType.技能 or not state.my_active: return True
        return any(s.name==action.target and s.pp_current>0 for s in state.my_active.skills)
    
    def _check_sealed(self, action, state):
        if action.action_type != BattleActionType.技能 or not state.my_active: return True
        return any(s.name==action.target and not s.is_sealed for s in state.my_active.skills)
    
    def _check_alive(self, action, state):
        if action.action_type != BattleActionType.换宠: return True
        return any(p.name==action.target and p.is_alive for p in state.my_pets)
    
    def _check_swap_allowed(self, action, state):
        if action.action_type != BattleActionType.换宠: return True
        return not (state.my_last_action and state.my_last_action.action_type == BattleActionType.换宠)
    
    def _check_status_blocks_action(self, action, state):
        if not state.my_active: return True
        if state.my_active.status == StatusCondition.睡眠: return action.action_type == BattleActionType.等待
        if state.my_active.status == StatusCondition.冰冻: return action.action_type == BattleActionType.等待
        return True
    
    def _suggest_fix(self, action, violations, state):
        if "PP充足" in violations: return f"{action.target} PP不足"
        if "技能未封印" in violations: return f"{action.target} 被封印"
        if "宠物存活" in violations: return f"{action.target} 已阵亡"
        if "非睡眠状态可用技能" in violations: return f"{state.my_active.name} 处于{state.my_active.status.value}状态"
        return "动作不合法"