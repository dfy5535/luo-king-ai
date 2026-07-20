"""
数据模型 — 唯一状态描述
"""
from dataclasses import dataclass, field
from typing import Optional

# ─── 精灵属性 ───

ELEMENT_TYPES = {
    "火", "水", "草", "电", "冰", "地", "翼", "萌",
    "虫", "幽", "武", "毒", "恶", "光", "龙", "机械",
    "普通", "神", "幻"
}

# 属性克制表（攻击方 → 防御方 → 倍率）
EFFECTIVENESS: dict[str, dict[str, float]] = {
    "火":    {"草": 2.0, "冰": 2.0, "虫": 2.0, "水": 0.5, "地": 0.5, "龙": 0.5},
    "水":    {"火": 2.0, "地": 2.0, "草": 0.5, "电": 0.5, "龙": 0.5},
    "草":    {"水": 2.0, "地": 2.0, "火": 0.5, "草": 0.5, "毒": 0.5, "虫": 0.5, "龙": 0.5, "冰": 0.5},
    "电":    {"水": 2.0, "翼": 2.0, "草": 0.5, "电": 0.5, "地": 0.0},
    "冰":    {"草": 2.0, "地": 2.0, "翼": 2.0, "龙": 2.0, "火": 0.5, "水": 0.5, "冰": 0.5},
    "地":    {"火": 2.0, "电": 2.0, "毒": 2.0, "机械": 2.0, "草": 0.5, "虫": 0.5, "翼": 0.0},
    "翼":    {"草": 2.0, "虫": 2.0, "电": 0.5, "冰": 0.5, "机械": 0.5},
    "萌":    {"武": 2.0, "毒": 2.0, "恶": 0.0, "机械": 0.5},
    "虫":    {"草": 2.0, "萌": 2.0, "火": 0.5, "毒": 0.5, "翼": 0.5, "冰": 0.5},
    "幽":    {"幽": 2.0, "萌": 2.0, "恶": 0.5, "普通": 0.0},
    "武":    {"普通": 2.0, "冰": 2.0, "地": 2.0, "恶": 2.0, "机械": 2.0, "毒": 0.5, "翼": 0.5, "萌": 0.5, "虫": 0.5, "幽": 0.5},
    "毒":    {"草": 2.0, "萌": 2.0, "毒": 0.5, "地": 0.5, "恶": 0.5, "机械": 0.5},
    "恶":    {"幽": 2.0, "萌": 2.0, "武": 0.5, "恶": 0.5, "光": 0.5},
    "光":    {"幽": 2.0, "恶": 2.0, "光": 0.5, "火": 0.5},
    "龙":    {"龙": 2.0, "光": 0.5, "萌": 0.5, "冰": 0.5},
    "机械":  {"冰": 2.0, "机械": 0.5, "火": 0.5, "水": 0.5},
    "普通":  {"武": 0.5, "幽": 0.0, "机械": 0.5},
    "神":    {},
    "幻":    {},
}


@dataclass
class Skill:
    """技能"""
    name: str
    element: str
    power: int
    pp: int
    max_pp: int
    accuracy: int = 100
    is_physical: bool = True  # True=物理, False=魔法


@dataclass
class Pet:
    """精灵"""
    name: str
    element: str
    level: int
    hp: int
    max_hp: int
    skills: list[Skill] = field(default_factory=list)
    attack: int = 100
    defense: int = 100
    magic_attack: int = 100
    magic_defense: int = 100
    speed: int = 100
    is_alive: bool = True
    status: str = ""  # 睡眠/冰冻/中毒/烧伤/麻痹


@dataclass
class BattleState:
    """完整战斗状态——由 Vision 解析产生，输入 Battle Engine"""
    player_pets: list[Pet] = field(default_factory=list)
    enemy_pets: list[Pet] = field(default_factory=list)
    active_player_idx: int = 0
    active_enemy_idx: int = 0
    weather: str = ""
    terrain: str = ""
    turn_count: int = 0
    is_player_turn: bool = True
    battle_id: str = ""


@dataclass
class Action:
    """动作指令——从服务器发给 APK"""
    action_type: str  # "tap" | "skill_1" | "skill_2" | "skill_3" | "skill_4" | "swap_2" | "swap_3" | ...
    coordinate: tuple[int, int] = (0, 0)
    delay_ms: int = 500
    session_id: str = ""


@dataclass
class ActionResult:
    """动作执行结果——从 APK 发回服务器"""
    success: bool
    device_id: str = ""
    session_id: str = ""
    error: str = ""