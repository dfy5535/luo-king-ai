"""
luo_king_server/vision/llm_parser.py
=====================================
视觉解析层——与之前一致。
截图 → Base64 → SenseNova → VisionResult
纯服务器端，不依赖任何客户端。
"""

from __future__ import annotations
from typing import Optional, List, Dict, Any, Tuple
from dataclasses import dataclass, field
import logging
import json
import base64
import os
import time

logger = logging.getLogger(__name__)

# ── 协议层 ──
@dataclass
class VisionSkill:
    name: str; pp: int; pp_max: int

@dataclass
class VisionPet:
    pet_name: str; hp: int; hp_max: int
    status: List[str] = field(default_factory=list)
    buffs: Dict[str, int] = field(default_factory=dict)
    skills: List[VisionSkill] = field(default_factory=list)

@dataclass
class VisionResult:
    phase: Optional[str] = None
    turn: Optional[int] = None
    weather: Optional[str] = None
    self: Optional[VisionPet] = None
    enemy: Optional[VisionPet] = None
    available_actions: List[str] = field(default_factory=list)
    confidence: float = 0.0

# ── 校验器 ──
REQUIRED_FIELDS_BATTLE = [
    "self.pet_name", "self.hp", "self.hp_max", "self.skills",
    "enemy.pet_name", "enemy.hp", "enemy.hp_max"
]
MAX_RETRIES = 3
CONFIDENCE_ACCEPTABLE = 0.70
CONFIDENCE_LOW = 0.70

class VisionValidator:
    @staticmethod
    def check_required(result: VisionResult, phase: str) -> List[str]:
        missing = []
        if phase == "battle":
            for fp in REQUIRED_FIELDS_BATTLE:
                if not VisionValidator._get_field(result, fp):
                    missing.append(fp)
        return missing
    
    @staticmethod
    def check_types(result: VisionResult) -> List[str]:
        errors = []
        if result.self:
            if not isinstance(result.self.hp, int): errors.append("self.hp 不是整数")
            if not isinstance(result.self.hp_max, int): errors.append("self.hp_max 不是整数")
            for s in result.self.skills:
                if not isinstance(s.pp, int): errors.append(f"{s.name}.pp 不是整数")
        if result.enemy:
            if not isinstance(result.enemy.hp, int): errors.append("enemy.hp 不是整数")
            if not isinstance(result.enemy.hp_max, int): errors.append("enemy.hp_max 不是整数")
        if not isinstance(result.confidence, (int, float)): errors.append("confidence 不是数字")
        elif result.confidence < 0 or result.confidence > 1: errors.append("confidence 超出范围")
        return errors
    
    @staticmethod
    def _get_field(obj, path: str):
        parts = path.split(".")
        cur = obj
        for p in parts:
            cur = getattr(cur, p, None) if hasattr(cur, p) else None
            if cur is None: return None
        return cur

# ── 提示词 ──
SYSTEM_PROMPT = """你是一个游戏战斗状态提取器。
你只负责从截图中提取可见信息，并输出严格 JSON。
不要分析策略，不要建议操作，不要解释原因。
如果某个字段无法确认，填 null。
如果某个值看不清，优先保持保守，不要猜测。
输出必须符合给定结构，不能包含多余文本。"""

USER_PROMPT = "请从这张游戏截图中提取战斗状态，严格输出 JSON。"

# ── 解析器 ──
class LLMVisionParser:
    def __init__(self, llm_client=None, model: str = "sensenova-6.7-flash-lite"):
        self.llm_client = llm_client
        self.model = model
        self.validator = VisionValidator()
        self.total_calls = 0; self.retry_count = 0; self.failure_count = 0
    
    def parse_screenshot(self, image_path: str, max_retries: int = MAX_RETRIES) -> VisionResult:
        self.total_calls += 1
        image_data, media_type = self._prepare_image(image_path)
        if image_data is None:
            return VisionResult(confidence=0.0)
        last_error = ""
        for attempt in range(max_retries):
            try:
                llm_json = self._call_llm(image_data, media_type)
                result = self._parse_to_vision_result(llm_json)
                missing = self.validator.check_required(result, result.phase or "unknown")
                type_errors = self.validator.check_types(result)
                if not missing and not type_errors:
                    if result.confidence >= CONFIDENCE_ACCEPTABLE:
                        return result
                last_error = f"missing={missing} types={type_errors}"
            except Exception as e:
                last_error = str(e)
            self.retry_count += 1
            if attempt < max_retries - 1: time.sleep(1.0)
        self.failure_count += 1
        return VisionResult(confidence=0.0, phase="unknown")
    
    def parse_base64(self, image_base64: str, media_type: str = "image/jpeg") -> VisionResult:
        """直接从Base64解析（客户端上传场景）"""
        self.total_calls += 1
        try:
            llm_json = self._call_llm(image_base64, media_type)
            result = self._parse_to_vision_result(llm_json)
            return result
        except Exception as e:
            self.failure_count += 1
            return VisionResult(confidence=0.0, phase="unknown")
    
    def _prepare_image(self, image_path: str) -> Tuple[Optional[str], Optional[str]]:
        try:
            with open(image_path, "rb") as f:
                data = base64.b64encode(f.read()).decode("utf-8")
            ext = os.path.splitext(image_path)[1].lower()
            mt = {".png":"image/png",".jpg":"image/jpeg",".jpeg":"image/jpeg",".webp":"image/webp"}.get(ext,"image/png")
            return data, mt
        except Exception as e:
            logger.error(f"读取图片失败: {e}")
            return None, None
    
    def _call_llm(self, image_data: str, media_type: str) -> Dict[str, Any]:
        response = self.llm_client.chat.completions.create(
            model=self.model,
            messages=[
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": [
                    {"type": "text", "text": USER_PROMPT},
                    {"type": "image_url", "image_url": {"url": f"data:{media_type};base64,{image_data}"}}
                ]}
            ],
            temperature=0.0, max_tokens=2000, response_format={"type": "json_object"},
        )
        return json.loads(response.choices[0].message.content)
    
    def _parse_to_vision_result(self, data: Dict[str, Any]) -> VisionResult:
        r = VisionResult()
        r.phase = data.get("phase"); r.turn = data.get("turn"); r.weather = data.get("weather")
        r.confidence = float(data.get("confidence", 0.0))
        r.available_actions = data.get("available_actions", [])
        sd = data.get("self")
        if sd and isinstance(sd, dict): r.self = self._parse_pet_json(sd)
        ed = data.get("enemy")
        if ed and isinstance(ed, dict): r.enemy = self._parse_pet_json(ed)
        return r
    
    def _parse_pet_json(self, data: Dict[str, Any]) -> VisionPet:
        p = VisionPet(pet_name=data.get("pet_name",""), hp=data.get("hp",0),
                       hp_max=data.get("hp_max",0), status=data.get("status",[]),
                       buffs=data.get("buffs",{}))
        for sd in data.get("skills", []):
            if isinstance(sd, dict):
                p.skills.append(VisionSkill(name=sd.get("name",""), pp=sd.get("pp",0), pp_max=sd.get("pp_max",0)))
        return p