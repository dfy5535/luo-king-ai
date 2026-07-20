"""
luo_king_server/engine/battle_engine.py
========================================
Battle Engine——与之前一致。
"""

from __future__ import annotations
from typing import List, Dict, Optional, Tuple, Any
from dataclasses import dataclass, field
import logging
from ..core.models import (BattleState, PetState, SkillState, BattleAction, BattleActionType,
    PetType, BuffType, WeatherType, StatusCondition, get_type_effectiveness)

logger = logging.getLogger(__name__)

@dataclass
class SkillScore:
    skill_name: str; score: float; damage_estimate: float
    type_effectiveness: float; is_recommended: bool; reasoning: str = ""

@dataclass
class BattleRecommendation:
    skill_scores: List[SkillScore] = field(default_factory=list)
    swap_recommendations: List[Tuple[str, float]] = field(default_factory=list)
    advantage: float = 0.0; urgency: str = "normal"
    predicted_enemy_action: Optional[str] = None; risk_warning: Optional[str] = None
    suggested_action: Optional[BattleAction] = None
    
    def to_prompt_context(self) -> str:
        lines = ["【战斗引擎分析】", f"局势评估: {self.advantage:.2f} ({self.urgency})"]
        if self.skill_scores:
            lines.append("\n技能评分:")
            for s in sorted(self.skill_scores, key=lambda x: x.score, reverse=True):
                rec = " ★推荐" if s.is_recommended else ""
                lines.append(f"  {s.skill_name}: {s.score:.0f}/100 (伤害:{s.damage_estimate:.0f} 克制:{s.type_effectiveness:.1f}x){rec}")
                if s.reasoning: lines.append(f"    理由: {s.reasoning}")
        if self.swap_recommendations:
            lines.append("\n换宠建议:")
            for n, sc in self.swap_recommendations: lines.append(f"  {n}: {sc:.0f}/100")
        if self.predicted_enemy_action: lines.append(f"\n敌方预测: {self.predicted_enemy_action}")
        if self.risk_warning: lines.append(f"\n⚠️ 风险提示: {self.risk_warning}")
        return "\n".join(lines)

class BattleEngine:
    BUFF_MULTIPLIERS = {-6:0.25,-5:0.29,-4:0.33,-3:0.40,-2:0.50,-1:0.67,0:1.0,1:1.5,2:2.0,3:2.5,4:3.0,5:3.5,6:4.0}
    
    def __init__(self):
        self.state: Optional[BattleState] = None
        self.last_recommendation: Optional[BattleRecommendation] = None
    
    def update_state(self, state: BattleState):
        self.state = state
    
    def analyze(self) -> BattleRecommendation:
        if not self.state or not self.state.is_my_turn: return BattleRecommendation()
        rec = BattleRecommendation()
        rec.skill_scores = self._score_skills()
        rec.swap_recommendations = self._evaluate_swaps()
        rec.advantage, rec.urgency = self._evaluate_situation()
        rec.predicted_enemy_action = self._predict_enemy_action()
        rec.risk_warning = self._detect_risks()
        if self._can_decide_without_llm(rec):
            rec.suggested_action = self._deterministic_decision(rec)
        self.last_recommendation = rec
        return rec
    
    def _score_skills(self) -> List[SkillScore]:
        if not self.state or not self.state.my_active or not self.state.enemy_pet: return []
        my = self.state.my_active; enemy = self.state.enemy_pet; scores = []
        for skill in my.skills:
            if skill.pp_current <= 0 or skill.is_sealed: continue
            score = 0.0; reasons = []
            eff = get_type_effectiveness(skill.skill_type.value, [t.value for t in enemy.types])
            ts = eff * 50
            if eff == 0: ts = -100; reasons.append(f"对{enemy.name}无效!")
            elif eff >= 2: reasons.append(f"属性克制!({eff}x)")
            elif eff <= 0.5: reasons.append(f"被抵抗({eff}x)")
            score += ts
            if skill.power > 0: score += min(skill.power/1.5, 40)
            else: score += 15
            pr = skill.pp_current/skill.pp_max if skill.pp_max>0 else 0
            if pr <= 0.25: score -= 20; reasons.append(f"PP不足({skill.pp_current}/{skill.pp_max})")
            elif pr >= 0.75: score += 5
            if skill.priority > 0: score += 10; reasons.append(f"先手(priority {skill.priority})")
            dmg = self._estimate_damage(skill, my, enemy, eff)
            if dmg >= enemy.hp_current: score += 30; reasons.append(f"斩杀线!({dmg:.0f}≥{enemy.hp_current})")
            scores.append(SkillScore(skill_name=skill.name, score=max(0,min(100,score)),
                damage_estimate=dmg, type_effectiveness=eff, is_recommended=score>=70,
                reasoning="; ".join(reasons)))
        return sorted(scores, key=lambda x: x.score, reverse=True)
    
    def _estimate_damage(self, skill, attacker, defender, eff) -> float:
        if skill.power == 0: return 0.0
        atk_b = attacker.buffs.get(BuffType.攻击,0) or attacker.buffs.get(BuffType.魔攻,0)
        def_b = defender.buffs.get(BuffType.防御,0) or defender.buffs.get(BuffType.魔抗,0)
        atk_m = self.BUFF_MULTIPLIERS.get(atk_b, 1.0); def_m = self.BUFF_MULTIPLIERS.get(def_b, 1.0)
        wm = 1.0
        if self.state:
            if self.state.weather==WeatherType.晴天 and skill.skill_type==PetType.火: wm=1.5
            elif self.state.weather==WeatherType.雨天 and skill.skill_type==PetType.水: wm=1.5
            elif self.state.weather==WeatherType.雨天 and skill.skill_type==PetType.火: wm=0.5
        return max(0, skill.power * 100 * atk_m / (100 * def_m) * eff * wm * 0.925)
    
    def _evaluate_swaps(self) -> List[Tuple[str, float]]:
        if not self.state or not self.state.enemy_pet: return []
        enemy = self.state.enemy_pet; swaps = []
        for pet in self.state.my_pets:
            if pet==self.state.my_active or not pet.is_alive: continue
            sc = 50.0
            for t in pet.types:
                for et in enemy.types:
                    eff = get_type_effectiveness(et.value, [t.value])
                    if eff <= 0.5: sc += 15
                    elif eff >= 2: sc -= 20
            sc += pet.hp_percent * 15
            swaps.append((pet.name, max(0,min(100,sc))))
        return sorted(swaps, key=lambda x: x[1], reverse=True)
    
    def _evaluate_situation(self) -> Tuple[float, str]:
        if not self.state or not self.state.my_active or not self.state.enemy_pet: return 0.0, "normal"
        my = self.state.my_active; enemy = self.state.enemy_pet
        ha = my.hp_percent - enemy.hp_percent
        ma = sum(1 for p in self.state.my_pets if p.is_alive)
        ea = len(self.state.enemy_pets_known) if self.state.enemy_pets_known else 1
        ta = 0.0
        for t in my.types:
            eff = get_type_effectiveness(t.value, [e.value for e in enemy.types])
            if eff >= 2: ta += 0.2
            elif eff <= 0.5: ta -= 0.2
        adv = ha * 0.5 + (ma - ea) * 0.15 + ta
        if my.hp_percent <= 0.2: urg = "critical"
        elif my.hp_percent <= 0.4: urg = "dangerous"
        elif my.hp_percent >= 0.8 and enemy.hp_percent <= 0.3: urg = "safe"
        else: urg = "normal"
        return max(-1.0, min(1.0, adv)), urg
    
    def _predict_enemy_action(self) -> Optional[str]:
        if not self.state or not self.state.enemy_pet: return None
        e = self.state.enemy_pet
        if e.hp_percent <= 0.3 and len(self.state.enemy_pets_known) > 1: return "可能换宠治疗"
        if e.status != StatusCondition.无: return "可能尝试解除异常状态"
        return None
    
    def _detect_risks(self) -> Optional[str]:
        if not self.state or not self.state.my_active: return None
        my = self.state.my_active; risks = []
        if my.hp_percent <= 0.25: risks.append(f"HP过低({my.hp_percent:.0%})")
        if my.status != StatusCondition.无: risks.append(f"处于{my.status.value}状态")
        if len([s for s in my.skills if s.pp_current>0 and not s.is_sealed]) <= 1: risks.append("PP即将耗尽")
        return "; ".join(risks) if risks else None
    
    def _can_decide_without_llm(self, rec: BattleRecommendation) -> bool:
        usable = [s for s in rec.skill_scores if s.score > 0]
        if len(usable) == 1 and usable[0].score >= 80: return True
        best = rec.skill_scores[0] if rec.skill_scores else None
        if best and best.type_effectiveness >= 2 and best.damage_estimate > 0: return True
        return False
    
    def _deterministic_decision(self, rec: BattleRecommendation) -> BattleAction:
        if rec.skill_scores:
            b = rec.skill_scores[0]
            return BattleAction(BattleActionType.技能, target=b.skill_name, confidence=0.95, reasoning=f"引擎确定性决策: {b.reasoning}")
        return BattleAction(BattleActionType.等待, reasoning="无可行动作")