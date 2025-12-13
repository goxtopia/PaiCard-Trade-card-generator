import base64
import os
import random
import requests
import time
from typing import Optional, Dict
from io import BytesIO
from PIL import Image

DEFAULT_PROMPTS = {
    "rarity": "Analyze this image and determine its rarity. Choose one from: N, R, SR, SSR, UR. Output only the rarity code (e.g., SSR).",
    "name": "Create a funny and creative name for a trading card based on this image. Output only the name. 回复中文(一定要简短、恶搞、有趣，最好还带点诗意)",
    "description": "Write a creative and funny ability（最好带点调侃意味） description（也可以是无效果，如果你觉得合理的话） for this trading card based on the image, in the style of Yu-Gi-Oh. KEEP IT SHORT (max 2 sentences). Output only the description. 回复中文",
    "atk": "Determine an ATK (Attack) value for this card between 0 and 5000 based on its power level. Output only the number.",
    "def": "Determine a DEF (Defense) value for this card between 0 and 5000 based on its toughness. Output only the number."
}

DEFAULT_SINGLE_CALL_PROMPT = """Create a funny and creative name and ability description for a trading card based on this image. Name and Description should be in Chinese (Chinese). The description should be short (max 2 sentences)."""

class VLMService:
    def __init__(self, api_base="http://192.168.124.22:8080", api_key="sk-placeholder", model="vlm-model", use_stub=True):
        self.api_base = api_base.rstrip('/')
        self.api_key = api_key
        self.model = model
        self.use_stub = use_stub

    def analyze_image(self, image_path: str, custom_prompts: Optional[Dict[str, str]] = None, single_call_mode: bool = False, single_call_prompt: str = "") -> Dict[str, str]:
        if self.use_stub:
            return self._stub_analyze(image_path)

        if single_call_mode:
            return self._analyze_single_call(image_path, single_call_prompt)
        
        # Merge defaults with custom prompts
        prompts = DEFAULT_PROMPTS.copy()
        if custom_prompts:
            for key, value in custom_prompts.items():
                if value and value.strip():
                    prompts[key] = value

        try:
            # Separate calls as requested to handle smaller models better
            rarity = self._call_vlm(image_path, prompts["rarity"])
            name = self._call_vlm(image_path, prompts["name"])
            description = self._call_vlm(image_path, prompts["description"])
            atk = self._call_vlm(image_path, prompts["atk"])
            def_ = self._call_vlm(image_path, prompts["def"])
            
            # Fallback if calls fail or return empty (basic error handling)
            if not rarity: rarity = "N"
            if not name: name = "Unknown Entity"
            if not description: description = "No effect."
            if not atk: atk = "0"
            if not def_: def_ = "0"

            return {
                "rarity": self._clean_rarity(rarity),
                "name": name.strip(),
                "description": description.strip(),
                "atk": self._clean_number(atk),
                "def": self._clean_number(def_)
            }
        except Exception as e:
            print(f"VLM Analysis failed: {e}")
            return self._stub_analyze(image_path) # Fallback to stub on error

    def _analyze_single_call(self, image_path: str, custom_instruction: str) -> Dict[str, str]:
        instruction = custom_instruction if custom_instruction.strip() else DEFAULT_SINGLE_CALL_PROMPT

        prompt = f"""
            {instruction}

            MANDATORY OUTPUT FORMAT:
            You must analyze the image and return a JSON object with the following keys:
            - "rarity": Choose one from [N, R, SR, SSR, UR] based on how epic the image looks.
            - "name": The name of the card.
            - "description": The ability text.
            - "atk": Number 0-5000.
            - "def": Number 0-5000.

            Return ONLY the raw JSON string. Do not include markdown formatting like ```json.
        """

        try:
            response_text = self._call_vlm(image_path, prompt)

            # Clean markdown code blocks if present
            clean_content = response_text.strip()
            if clean_content.startswith("```json"):
                clean_content = clean_content[7:]
            if clean_content.startswith("```"):
                clean_content = clean_content[3:]
            if clean_content.endswith("```"):
                clean_content = clean_content[:-3]

            card_json = json.loads(clean_content.strip())

            return {
                "rarity": self._clean_rarity(card_json.get("rarity", "N")),
                "name": card_json.get("name", "Unknown").strip(),
                "description": card_json.get("description", "No Data").strip(),
                "atk": self._clean_number(str(card_json.get("atk", "0"))),
                "def": self._clean_number(str(card_json.get("def", "0")))
            }
        except Exception as e:
            print(f"Single Call Analysis failed: {e}")
            # Try fallback to stub or simple error return
            return self._stub_analyze(image_path)

    def _clean_number(self, text: str) -> str:
        # Extract digits
        digits = ''.join(filter(str.isdigit, text))
        return digits if digits else "0"

    def _clean_rarity(self, text: str) -> str:
        text = text.upper().strip()
        valid_rarities = ["SSR", "UR", "SR", "R", "N",] # ["N", "R", "SR", "SSR", "UR"]
        for r in valid_rarities:
            if r in text:
                return r
        return "N"

    def _stub_analyze(self, image_path: str) -> Dict[str, str]:
        time.sleep(1.5) # Simulate network delay
        
        rarities = ["N", "R", "SR", "SSR", "UR"]
        names = [
            "Blue-Eyes White Developer", 
            "Dark Magician of Code", 
            "Pot of Greed (But for RAM)", 
            "Infinite Loop Dragon", 
            "Bug Squash Knight",
            "The Great Firewall",
            "Quantum Cat"
        ]
        descriptions = [
            "When this card is summoned, you can special summon one 'Stack Overflow' token to your opponent's field.",
            "Flip: Destroy all bugs on the field. If you do, draw 2 cards from your repository.",
            "Cannot be destroyed by syntax errors. Once per turn, you can negate a compilation failure.",
            "This card gains 500 ATK for every unclosed parenthesis in your graveyard.",
            "Pay 1000 LP; force your opponent to refactor their entire deck.",
            "When an opponent declares an attack, you can banish this card to restart the server.",
            "If this card is in the superposition state, it is both alive and dead until observed."
        ]
        
        return {
            "rarity": random.choice(rarities),
            "name": random.choice(names),
            "description": random.choice(descriptions),
            "atk": str(random.randint(0, 500) * 10),
            "def": str(random.randint(0, 500) * 10)
        }

    def _call_vlm(self, image_path: str, prompt: str) -> str:
        # Resize image logic
        MAX_SIZE = 576
        with Image.open(image_path) as img:
            # Convert to RGB to handle PNGs with alpha channel if necessary for JPEG saving
            if img.mode in ('RGBA', 'P'):
                img = img.convert('RGB')
                
            w, h = img.size
            if w > MAX_SIZE or h > MAX_SIZE:
                scale = min(MAX_SIZE / w, MAX_SIZE / h)
                new_w = int(w * scale)
                new_h = int(h * scale)
                img = img.resize((new_w, new_h), Image.Resampling.LANCZOS)
            
            buffered = BytesIO()
            img.save(buffered, format="JPEG", quality=85)
            base64_image = base64.b64encode(buffered.getvalue()).decode('utf-8')

        messages = [
            {
                "role": "user",
                "content": [
                    {
                        "type": "image_url",
                        "image_url": {
                            "url": f"data:image/jpeg;base64,{base64_image}"
                        }
                    },
                    {
                        "type": "text",
                        "text": prompt
                    }
                ]
            }
        ]
        
        url = f"{self.api_base}/v1/chat/completions"
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {self.api_key}"
        }
        payload = {
            "model": self.model,
            "messages": messages,
            "temperature": 0.6,
            "max_tokens": 4096
        }
        
        response = requests.post(url, headers=headers, json=payload, timeout=60)
        response.raise_for_status()
        data = response.json()
        return data["choices"][0]["message"]["content"]
