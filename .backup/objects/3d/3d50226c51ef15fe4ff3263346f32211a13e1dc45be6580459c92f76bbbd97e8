"""
luo_king_server/engine/battle_memory.py
========================================
战斗记忆——与之前一致。
"""

from __future__ import annotations
from typing import List, Dict, Optional, Any, Tuple
from dataclasses import dataclass, field, asdict
import json
import logging
import os

logger = logging.getLogger(__name__)

@dataclass
class SkillUsageRecord:
    pet_name: str; skill_name: str; turn_number: int
    target: str = ""; effectiveness: str = ""; damage: int = 0

@dataclass
class TurnMemory:
    turn_number: int; my_action: Optional[str] = None
    enemy_action: Optional[str] = None; my_hp_change: int = 0
    enemy_hp_change: int = 0; weather: str = "none"; notes: str = ""

@dataclass
class BattleMemory:
    battle_id: str = ""; turn_number: int = 0
    enemy_skill_history: List[SkillUsageRecord] = field(default_factory=list)
    enemy_pp_estimate: Dict[str, int] = field(default_factory=dict)
    enemy_pp_max: Dict[str, int] = field(default_factory=dict)
    enemy_switch_history: List[Tuple[int, str, str]] = field(default_factory=list)
    turn_history: List[TurnMemory] = field(default_factory=list)
    weather_history: List[Tuple[int, str]] = field(default_factory=list)
    status_history: Dict[str, List[Tuple[int, str]]] = field(default_factory=dict)
    boost_history: Dict[str, List[Tuple[int, str, int]]] = field(default_factory=dict)
    enemy_roster: List[str] = field(default_factory=list)
    enemy_current_pet: str = ""
    
    def record_enemy_skill(self, pet_name: str, skill_name: str, turn: int):
        self.enemy_skill_history.append(SkillUsageRecord(pet_name=pet_name, skill_name=skill_name, turn_number=turn))
        if skill_name in self.enemy_pp_estimate:
            self.enemy_pp_estimate[skill_name] = max(0, self.enemy_pp_estimate[skill_name] - 1)
        else:
            self.enemy_pp_estimate[skill_name] = 4
            self.enemy_pp_max[skill_name] = 10
    
    def record_enemy_switch(self, turn: int, from_pet: str, to_pet: str):
        self.enemy_switch_history.append((turn, from_pet, to_pet))
        self.enemy_current_pet = to_pet
        if to_pet not in self.enemy_roster: self.enemy_roster.append(to_pet)
    
    def get_enemy_pp_summary(self) -> str:
        if not self.enemy_pp_estimate: return ""
        parts = [f"{s} PP≈{p}/{self.enemy_pp_max.get(s,10)}" for s,p in sorted(self.enemy_pp_estimate.items(), key=lambda x: x[1])]
        return "敌方PP估算: " + ", ".join(parts)
    
    def get_enemy_history_summary(self) -> str:
        if not self.enemy_skill_history: return ""
        recent = self.enemy_skill_history[-5:]
        parts = [f"第{r.turn_number}回合: {r.pet_name} 使用了 {r.skill_name}" for r in recent]
        return "敌方最近行动:\n" + "\n".join(parts)
    
    def get_full_context(self) -> str:
        parts = [f"战斗进行到第 {self.turn_number} 回合"]
        pp = self.get_enemy_pp_summary()
        if pp: parts.append(pp)
        h = self.get_enemy_history_summary()
        if h: parts.append(h)
        if self.enemy_roster: parts.append(f"敌方阵容: {', '.join(self.enemy_roster)}")
        if self.weather_history: parts.append(f"天气变化: {', '.join(f'第{t}回合:{w}' for t,w in self.weather_history[-3:])}")
        return "\n".join(parts)

class BattleMemoryManager:
    def __init__(self, data_dir: str = "data/memory"):
        self.data_dir = data_dir
        self.sessions: Dict[str, BattleMemory] = {}
        os.makedirs(data_dir, exist_ok=True)
    
    def new_battle(self, session_id: str, battle_id: str) -> BattleMemory:
        mem = BattleMemory(battle_id=battle_id)
        self.sessions[session_id] = mem
        return mem
    
    def get(self, session_id: str) -> Optional[BattleMemory]:
        return self.sessions.get(session_id)
    
    def save(self, session_id: str):
        mem = self.sessions.get(session_id)
        if not mem: return
        path = f"{self.data_dir}/{mem.battle_id}.json"
        with open(path, "w") as f: json.dump(asdict(mem), f, ensure_ascii=False, indent=2)
    
    def remove(self, session_id: str):
        self.sessions.pop(session_id, None)