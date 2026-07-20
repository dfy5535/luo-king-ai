"""
luo_king_server/core/models.py
================================
世界模型——与之前一致，但去掉所有"本地执行"相关的字段。
这是服务器端的数据契约，不依赖任何客户端实现。
"""

from __future__ import annotations
from dataclasses import dataclass, field, asdict
from typing import Optional, List, Dict, Any, Tuple
from enum import Enum


# ──────────────────────────────────────────────────────────────────────
# 枚举定义
# ──────────────────────────────────────────────────────────────────────

class PetType(Enum):
    普通 = "normal"; 火 = "fire"; 水 = "water"; 草 = "grass"
    电 = "electric"; 冰 = "ice"; 格斗 = "fighting"; 毒 = "poison"
    地面 = "ground"; 飞行 = "flying"; 超能力 = "psychic"; 虫 = "bug"
    岩石 = "rock"; 幽灵 = "ghost"; 龙 = "dragon"; 恶 = "dark"
    钢 = "steel"; 妖精 = "fairy"; 萌 = "cute"

class WeatherType(Enum):
    晴天 = "sunny"; 雨天 = "rainy"; 暴风雪 = "snowstorm"
    沙暴 = "sandstorm"; 迷雾 = "mist"; 无 = "none"

class StatusCondition(Enum):
    无 = "none"; 中毒 = "poisoned"; 剧毒 = "badly_poisoned"
    麻痹 = "paralyzed"; 烧伤 = "burned"; 冰冻 = "frozen"
    睡眠 = "sleeping"; 迷惑 = "confused"; 害怕 = "scared"

class BuffType(Enum):
    攻击 = "attack"; 防御 = "defense"; 魔攻 = "sp_attack"
    魔抗 = "sp_defense"; 速度 = "speed"; 命中 = "accuracy"; 闪避 = "evasion"

class GamePhase(Enum):
    登录 = "login"; 大厅 = "lobby"; 匹配中 = "matching"
    选宠 = "pet_select"; 战斗 = "battle"; 换宠 = "pet_swap"
    结算 = "settlement"; 异常 = "error"; 未知 = "unknown"

class BattleActionType(Enum):
    技能 = "skill"; 换宠 = "swap"; 使用道具 = "item"; 逃跑 = "flee"; 等待 = "wait"


# ──────────────────────────────────────────────────────────────────────
# 核心数据类
# ──────────────────────────────────────────────────────────────────────

@dataclass
class SkillState:
    name: str
    skill_type: PetType = PetType.普通
    power: int = 0
    pp_current: int = 0
    pp_max: int = 0
    accuracy: float = 1.0
    is_sealed: bool = False
    category: str = "physical"
    priority: int = 0

@dataclass
class PetState:
    name: str
    pet_id: Optional[int] = None
    types: List[PetType] = field(default_factory=list)
    hp_current: int = 0
    hp_max: int = 0
    hp_percent: float = 0.0
    skills: List[SkillState] = field(default_factory=list)
    pp_total: int = 0
    pp_max: int = 0
    buffs: Dict[BuffType, int] = field(default_factory=dict)
    status: StatusCondition = StatusCondition.无
    status_turns: int = 0
    level: int = 100
    is_alive: bool = True
    is_active: bool = False

    def __post_init__(self):
        self.hp_percent = (self.hp_current / self.hp_max) if self.hp_max > 0 else 0.0

@dataclass
class BattleAction:
    action_type: BattleActionType
    target: Optional[str] = None
    skill_index: Optional[int] = None  # 客户端用：技能按钮索引
    confidence: float = 0.0
    reasoning: str = ""

@dataclass
class BattleState:
    phase: GamePhase = GamePhase.未知
    is_my_turn: bool = False
    turn_number: int = 0
    my_pets: List[PetState] = field(default_factory=list)
    my_active: Optional[PetState] = None
    enemy_pet: Optional[PetState] = None
    enemy_pets_known: List[PetState] = field(default_factory=list)
    weather: WeatherType = WeatherType.无
    battlefield_effects: Dict[str, Any] = field(default_factory=dict)
    my_last_action: Optional[BattleAction] = None
    enemy_last_action: Optional[BattleAction] = None
    available_actions: List[BattleActionType] = field(default_factory=list)
    screenshot_timestamp: float = 0.0
    parse_confidence: float = 0.0
    raw_text_detected: List[str] = field(default_factory=list)

    def to_prompt_context(self) -> str:
        lines = []
        lines.append(f"=== 第 {self.turn_number} 回合 ===")
        lines.append(f"{'我方' if self.is_my_turn else '敌方'}回合")
        lines.append(f"天气: {self.weather.value}")
        if self.my_active:
            m = self.my_active
            lines.append(f"【我方出战】{m.name} ({'/'.join(t.value for t in m.types)})")
            lines.append(f"  HP: {m.hp_current}/{m.hp_max} ({m.hp_percent:.0%})")
            for s in m.skills:
                status = " [封印]" if s.is_sealed else ""
                lines.append(f"  技能: {s.name} PP {s.pp_current}/{s.pp_max}{status}")
            if m.buffs:
                buff_str = ", ".join(f"{k.value}: {v:+d}" for k, v in m.buffs.items() if v != 0)
                lines.append(f"  能力变化: {buff_str}")
            if m.status != StatusCondition.无:
                lines.append(f"  异常: {m.status.value}")
        if self.enemy_pet:
            e = self.enemy_pet
            lines.append(f"【敌方出战】{e.name} ({'/'.join(t.value for t in e.types)})")
            lines.append(f"  HP: {e.hp_current}/{e.hp_max} ({e.hp_percent:.0%})")
            if e.buffs:
                buff_str = ", ".join(f"{k.value}: {v:+d}" for k, v in e.buffs.items() if v != 0)
                lines.append(f"  能力变化: {buff_str}")
            if e.status != StatusCondition.无:
                lines.append(f"  异常: {e.status.value}")
        return "\n".join(lines)


# ──────────────────────────────────────────────────────────────────────
# 客户端协议（WebSocket 传输）
# ──────────────────────────────────────────────────────────────────────

@dataclass
class ClientHeartbeat:
    """客户端心跳"""
    device_id: str
    battery: int = 0
    phase: str = "unknown"

@dataclass
class ClientUpload:
    """客户端上传截图"""
    device_id: str
    session_id: str
    image_base64: str  # JPEG Base64

@dataclass
class ServerAction:
    """服务器返回动作"""
    action_type: str          # "skill" | "swap" | "wait"
    skill_index: Optional[int] = None  # 技能按钮索引 (1-4)
    swap_index: Optional[int] = None   # 换宠按钮索引 (1-3)
    coordinate: Optional[Tuple[int, int]] = None  # 绝对坐标
    delay_ms: int = 500       # 执行前等待
    reasoning: str = ""


# ──────────────────────────────────────────────────────────────────────
# 属性克制表
# ──────────────────────────────────────────────────────────────────────

TYPE_CHART: Dict[str, Dict[str, float]] = {
    "normal":   {"rock": 0.5, "ghost": 0, "steel": 0.5},
    "fire":     {"fire": 0.5, "water": 0.5, "grass": 2, "ice": 2, "bug": 2, "rock": 0.5, "dragon": 0.5, "steel": 2},
    "water":    {"fire": 2, "water": 0.5, "grass": 0.5, "ground": 2, "rock": 2, "dragon": 0.5},
    "grass":    {"fire": 0.5, "water": 2, "grass": 0.5, "poison": 0.5, "ground": 2, "flying": 0.5, "bug": 0.5, "rock": 2, "dragon": 0.5, "steel": 0.5},
    "electric": {"water": 2, "electric": 0.5, "grass": 0.5, "ground": 0, "flying": 2, "dragon": 0.5},
    "ice":      {"fire": 0.5, "water": 0.5, "grass": 2, "ice": 0.5, "ground": 2, "flying": 2, "dragon": 2, "steel": 0.5},
    "fighting": {"normal": 2, "ice": 2, "poison": 0.5, "flying": 0.5, "psychic": 0.5, "bug": 0.5, "rock": 2, "ghost": 0, "dark": 2, "steel": 2, "fairy": 0.5},
    "poison":   {"grass": 2, "poison": 0.5, "ground": 0.5, "rock": 0.5, "ghost": 0.5, "steel": 0, "fairy": 2},
    "ground":   {"fire": 2, "electric": 2, "grass": 0.5, "poison": 2, "flying": 0, "bug": 0.5, "rock": 2, "steel": 2},
    "flying":   {"electric": 0.5, "grass": 2, "fighting": 2, "bug": 2, "rock": 0.5, "steel": 0.5},
    "psychic":  {"fighting": 2, "poison": 2, "psychic": 0.5, "dark": 0, "steel": 0.5},
    "bug":      {"fire": 0.5, "grass": 2, "fighting": 0.5, "poison": 0.5, "flying": 0.5, "psychic": 2, "ghost": 0.5, "dark": 2, "steel": 0.5, "fairy": 0.5},
    "rock":     {"fire": 2, "ice": 2, "fighting": 0.5, "ground": 0.5, "flying": 2, "bug": 2, "steel": 0.5},
    "ghost":    {"normal": 0, "psychic": 2, "ghost": 2, "dark": 0.5},
    "dragon":   {"dragon": 2, "steel": 0.5, "fairy": 0},
    "dark":     {"fighting": 0.5, "psychic": 2, "ghost": 2, "dark": 0.5, "fairy": 0.5},
    "steel":    {"fire": 0.5, "water": 0.5, "electric": 0.5, "ice": 2, "rock": 2, "steel": 0.5, "fairy": 2},
    "fairy":    {"fire": 0.5, "poison": 0.5, "fighting": 2, "dragon": 2, "dark": 2, "steel": 0.5},
}

def get_type_effectiveness(attack_type: str, defend_types: List[str]) -> float:
    multiplier = 1.0
    for dt in defend_types:
        if attack_type in TYPE_CHART and dt in TYPE_CHART[attack_type]:
            multiplier *= TYPE_CHART[attack_type][dt]
    return multiplier