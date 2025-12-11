from fastapi import FastAPI, UploadFile, File, HTTPException, Form, BackgroundTasks
from fastapi.staticfiles import StaticFiles
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from starlette.middleware.base import BaseHTTPMiddleware
import shutil
import os
import uuid
import hashlib
import json
import random
import requests
import time
from typing import Optional, List, Dict
from vlm import VLMService

app = FastAPI()

# Security Headers Middleware
class SecurityHeadersMiddleware(BaseHTTPMiddleware):
    async def dispatch(self, request, call_next):
        response = await call_next(request)
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Cache-Control"] = "no-cache, no-store, must-revalidate"
        response.headers["Pragma"] = "no-cache"
        response.headers["Expires"] = "0"
        return response

app.add_middleware(SecurityHeadersMiddleware)

# Allow CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Setup directories
UPLOAD_DIR = "uploads"
CARD_BACKS_DIR = "static/card_backs"
CARDS_DB = "cards.json"
PACKS_DB = "packs.json"
SETTINGS_DB = "settings.json"

os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(CARD_BACKS_DIR, exist_ok=True)

# Helper for Databases
def load_cards():
    if not os.path.exists(CARDS_DB):
        return {}
    with open(CARDS_DB, "r") as f:
        return json.load(f)

def save_cards(cards):
    with open(CARDS_DB, "w") as f:
        json.dump(cards, f, indent=2)

def load_packs():
    if not os.path.exists(PACKS_DB):
        return {}
    with open(PACKS_DB, "r") as f:
        return json.load(f)

def save_packs(packs):
    with open(PACKS_DB, "w") as f:
        json.dump(packs, f, indent=2)

def load_settings():
    if not os.path.exists(SETTINGS_DB):
        return {}
    with open(SETTINGS_DB, "r") as f:
        return json.load(f)

def save_settings(settings):
    with open(SETTINGS_DB, "w") as f:
        json.dump(settings, f, indent=2)

def calculate_md5(file_path):
    hash_md5 = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(4096), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()

def get_available_card_backs():
    files = []
    if os.path.exists(CARD_BACKS_DIR):
        for f in os.listdir(CARD_BACKS_DIR):
            if f.lower().endswith(('.png', '.jpg', '.jpeg', '.webp', '.svg')):
                files.append(f"/static/card_backs/{f}")
    return files

# Initialize VLM Service
API_BASE = os.getenv("VLM_API_BASE", "http://192.168.124.22:8080")
API_KEY = os.getenv("VLM_API_KEY", "sk-placeholder")
USE_STUB = os.getenv("USE_STUB", "true").lower() == "true"

vlm_service = VLMService(api_base=API_BASE, api_key=API_KEY, use_stub=USE_STUB)

app.mount("/static", StaticFiles(directory="static"), name="static")
app.mount("/uploads", StaticFiles(directory="uploads"), name="uploads")

def get_random_effect_and_theme(rarity):
    rarity = rarity.upper()

    # Defaults
    effect = ""
    theme = ""

    # Probability Weights
    if rarity == "N":
        # Effect: 10% chance for low tier effect
        if random.random() < 0.1:
            effect = random.choice(["effect-dust", "effect-static"])
        # Theme
        theme = random.choice(["theme-gray", "theme-pale-blue", "theme-pale-green"])

    elif rarity == "R":
        # Effect: 30% chance
        if random.random() < 0.3:
            effect = random.choice(["effect-shine", "effect-sweep"])
        # Theme
        theme = random.choice(["theme-bronze", "theme-silver", "theme-steel"])

    elif rarity == "SR":
        # Effect: 60% chance
        if random.random() < 0.6:
            effect = random.choice(["effect-holographic", "effect-shine", "effect-sweep"])
        # Theme
        theme = random.choice(["theme-gold", "theme-orange", "theme-crimson"])

    elif rarity == "SSR":
        # Effect: 90% chance
        if random.random() < 0.9:
            effect = random.choice(["effect-lightning", "effect-pulse", "effect-holographic"])
        # Theme
        theme = random.choice(["theme-purple", "theme-magenta", "theme-deep-blue"])

    elif rarity == "UR":
        # Effect: 100% chance
        effect = random.choice(["effect-cosmic", "effect-pulse", "effect-lightning", "effect-holographic"])
        # Theme
        theme = random.choice(["theme-rainbow", "theme-black-gold", "theme-galaxy"])

    return effect, theme

def process_single_file_generation(file_path, file_md5, card_back, existing_card=None, hidden=False):
    # Load custom prompts
    settings = load_settings()
    custom_prompts = settings.get("prompts", None)

    # Analyze
    analysis = vlm_service.analyze_image(file_path, custom_prompts=custom_prompts)

    filename = os.path.basename(file_path)

    # Generate random visual attributes
    effect, theme = get_random_effect_and_theme(analysis["rarity"])

    new_card = {
        "md5": file_md5,
        "filename": filename,
        "image_url": f"/uploads/{filename}",
        "rarity": analysis["rarity"],
        "name": analysis["name"],
        "description": analysis["description"],
        "atk": analysis.get("atk", "0"),
        "def": analysis.get("def", "0"),
        "card_back": card_back,
        "created_at": int(time.time()),
        "effect_type": effect,
        "color_theme": theme,
        "hidden": hidden
    }

    # Preserve existing attributes if needed
    if existing_card:
        if not card_back and "card_back" in existing_card:
            new_card["card_back"] = existing_card["card_back"]

    return new_card

def background_pack_processing_wrapper(file_paths, pack_ids):
    # This wrapper handles the file logic properly
    chunk_size = 10
    total_files = len(file_paths)

    current_file_idx = 0

    for pack_id in pack_ids:
        pack_card_md5s = []

        # Load pack to get assigned card back
        packs = load_packs()
        current_pack_back = None
        if pack_id in packs:
            current_pack_back = packs[pack_id].get("card_back")

        # Process up to 10 files for this pack
        files_in_pack = 0
        while files_in_pack < 10 and current_file_idx < total_files:
            temp_path = file_paths[current_file_idx]
            current_file_idx += 1
            files_in_pack += 1

            try:
                file_md5 = calculate_md5(temp_path)

                cards = load_cards()
                if file_md5 in cards:
                    # Clean temp
                    if os.path.exists(temp_path):
                        os.remove(temp_path)
                    pack_card_md5s.append(file_md5)
                else:
                    # Move to final
                    ext = os.path.splitext(temp_path)[1]
                    if not ext: ext = ".jpg"
                    final_filename = f"{file_md5}{ext}"
                    final_path = os.path.join(UPLOAD_DIR, final_filename)

                    if os.path.exists(final_path):
                        if os.path.exists(temp_path):
                            os.remove(temp_path)
                    else:
                        os.rename(temp_path, final_path)

                    # Generate
                    new_card = process_single_file_generation(
                        final_path,
                        file_md5,
                        current_pack_back,
                        hidden=True
                    )
                    cards[file_md5] = new_card
                    save_cards(cards)
                    pack_card_md5s.append(file_md5)
            except Exception as e:
                print(f"Error processing file {temp_path}: {e}")

        # Update Pack
        packs = load_packs()
        if pack_id in packs:
            packs[pack_id]["status"] = "ready"
            packs[pack_id]["cards"] = pack_card_md5s
            save_packs(packs)

@app.post("/api/generate")
async def generate_card(
    file: Optional[UploadFile] = File(None),
    regenerate: bool = Form(False),
    existing_md5: Optional[str] = Form(None),
    card_back: Optional[str] = Form(None)
):
    try:
        cards = load_cards()
        
        # Scenario 1: Re-generating an existing card by MD5 (no new file upload)
        if existing_md5 and existing_md5 in cards and regenerate:
            card_data = cards[existing_md5]
            file_path = os.path.join(UPLOAD_DIR, card_data['filename'])
            if not os.path.exists(file_path):
                raise HTTPException(status_code=404, detail="Original image file missing")
            
            new_card = process_single_file_generation(file_path, existing_md5, card_back, existing_card=card_data, hidden=False)
            
            cards[existing_md5] = new_card
            save_cards(cards)
            return JSONResponse(content=new_card, media_type="application/json; charset=utf-8")

        # Scenario 2: Uploading a file
        if not file:
             raise HTTPException(status_code=400, detail="File is required if not regenerating by MD5")

        # Save temporarily to calculate MD5
        temp_filename = f"temp_{uuid.uuid4()}"
        temp_path = os.path.join(UPLOAD_DIR, temp_filename)
        
        with open(temp_path, "wb") as buffer:
            shutil.copyfileobj(file.file, buffer)
            
        file_md5 = calculate_md5(temp_path)
        
        # Check if exists
        if file_md5 in cards and not regenerate:
            # Clean up temp file
            os.remove(temp_path)
            # Update the binding if provided
            if card_back:
                 cards[file_md5]["card_back"] = card_back
                 # Ensure it's visible if the user explicitly uploaded it again
                 cards[file_md5]["hidden"] = False
                 save_cards(cards)

            return JSONResponse(content=cards[file_md5], media_type="application/json; charset=utf-8")
            
        # If new or force regenerate with new file
        file_extension = os.path.splitext(file.filename)[1]
        if not file_extension:
            file_extension = ".jpg"
            
        final_filename = f"{file_md5}{file_extension}"
        final_path = os.path.join(UPLOAD_DIR, final_filename)
        
        # Rename temp to final
        if os.path.exists(final_path):
            os.remove(temp_path) # File exists (e.g. same content different name), just use existing
        else:
            os.rename(temp_path, final_path)
            
        new_card = process_single_file_generation(final_path, file_md5, card_back, hidden=False)
        
        cards[file_md5] = new_card
        save_cards(cards)
        
        return JSONResponse(content=new_card, media_type="application/json; charset=utf-8")
        
    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/batch-generate")
async def batch_generate_card(
    files: List[UploadFile] = File(...),
    card_back: Optional[str] = Form(None)
):
    try:
        cards = load_cards()
        generated_cards = []

        # Limit to 10 files
        files_to_process = files
        if len(files) > 10:
            files_to_process = random.sample(files, 10)

        for file in files_to_process:
            # Save temporarily to calculate MD5
            temp_filename = f"temp_{uuid.uuid4()}"
            temp_path = os.path.join(UPLOAD_DIR, temp_filename)

            with open(temp_path, "wb") as buffer:
                shutil.copyfileobj(file.file, buffer)

            file_md5 = calculate_md5(temp_path)

            # Check if exists (Skip regeneration for batch to save time/cost)
            if file_md5 in cards:
                os.remove(temp_path)
                card = cards[file_md5]
                # Update card back binding for the batch
                if card_back:
                    card["card_back"] = card_back
                    card["hidden"] = False # Unhide
                    cards[file_md5] = card # Ensure update is saved
                generated_cards.append(card)
                continue

            # If new
            file_extension = os.path.splitext(file.filename)[1]
            if not file_extension:
                file_extension = ".jpg"

            final_filename = f"{file_md5}{file_extension}"
            final_path = os.path.join(UPLOAD_DIR, final_filename)

            if os.path.exists(final_path):
                os.remove(temp_path)
            else:
                os.rename(temp_path, final_path)

            new_card = process_single_file_generation(final_path, file_md5, card_back, hidden=False)
            cards[file_md5] = new_card
            generated_cards.append(new_card)

        save_cards(cards)
        return JSONResponse(content=generated_cards, media_type="application/json; charset=utf-8")

    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/god-draw")
async def god_draw_card():
    try:
        cards = load_cards()
        generated_cards = []
        available_card_backs = get_available_card_backs()

        # Determine number of cards to draw (5-10)
        num_draws = random.randint(5, 10)

        for _ in range(num_draws):
            # Fetch random image from API
            try:
                # The API returns one image at a time
                resp = requests.get("https://api.tcslw.cn/api/img/tbmjx?type=json", timeout=10)
                if resp.status_code != 200:
                    continue
                data = resp.json()
                image_url = data.get("image_url")
                if not image_url:
                    continue

                # Download the image
                img_resp = requests.get(image_url, timeout=10)
                if img_resp.status_code != 200:
                    continue

                image_content = img_resp.content

                # Save temporarily to calculate MD5
                temp_filename = f"temp_{uuid.uuid4()}"
                temp_path = os.path.join(UPLOAD_DIR, temp_filename)

                with open(temp_path, "wb") as f:
                    f.write(image_content)

                file_md5 = calculate_md5(temp_path)

                # Pick a random card back
                random_card_back = random.choice(available_card_backs) if available_card_backs else None

                # Check if exists
                if file_md5 in cards:
                    os.remove(temp_path)
                    card = cards[file_md5]
                    # For god draw, maybe we force the random back for visual effect?
                    # Or just use the existing one? The requirement says "randomly choose card back".
                    # If we want to persist this random choice for this session, we can return it modified
                    # without saving, OR update the binding. Updating binding is safer for persistence.
                    if random_card_back:
                        card["card_back"] = random_card_back
                        card["hidden"] = False
                        cards[file_md5] = card # Update binding
                    generated_cards.append(card)
                    continue

                # If new
                # Try to guess extension from URL or default to .jpg
                ext = os.path.splitext(image_url)[1]
                if not ext or len(ext) > 5:
                    ext = ".jpg"

                final_filename = f"{file_md5}{ext}"
                final_path = os.path.join(UPLOAD_DIR, final_filename)

                if os.path.exists(final_path):
                    os.remove(temp_path)
                else:
                    os.rename(temp_path, final_path)

                new_card = process_single_file_generation(final_path, file_md5, random_card_back, hidden=False)
                cards[file_md5] = new_card
                generated_cards.append(new_card)

            except Exception as loop_e:
                print(f"Error in god draw loop: {loop_e}")
                continue

        save_cards(cards)
        return JSONResponse(content=generated_cards, media_type="application/json; charset=utf-8")

    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/upload-packs")
async def upload_packs(
    background_tasks: BackgroundTasks,
    files: List[UploadFile] = File(...),
    card_back: Optional[str] = Form(None) # Ignored now
):
    try:
        packs = load_packs()
        new_pack_ids = []
        file_paths = []
        available_card_backs = get_available_card_backs()

        # Save all files first
        for file in files:
            temp_filename = f"temp_pack_{uuid.uuid4()}{os.path.splitext(file.filename)[1]}"
            temp_path = os.path.join(UPLOAD_DIR, temp_filename)
            with open(temp_path, "wb") as buffer:
                shutil.copyfileobj(file.file, buffer)
            file_paths.append(temp_path)

        # Create Packs
        num_files = len(files)
        # Math.ceil
        num_packs = (num_files + 9) // 10

        for _ in range(num_packs):
            pack_id = str(uuid.uuid4())

            # Randomly select card back for this pack
            random_back = random.choice(available_card_backs) if available_card_backs else None

            packs[pack_id] = {
                "id": pack_id,
                "status": "processing",
                "cards": [],
                "created_at": int(time.time()),
                "card_back": random_back
            }
            new_pack_ids.append(pack_id)

        save_packs(packs)

        # Start Background Processing
        background_tasks.add_task(background_pack_processing_wrapper, file_paths, new_pack_ids)

        return JSONResponse(content={"message": f"Processing {num_files} images into {num_packs} packs.", "pack_ids": new_pack_ids})

    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/packs")
async def get_packs():
    packs = load_packs()

    pack_list = []
    for pack in packs.values():
        if pack["status"] != "opened":
            pack_list.append(pack)

    pack_list.sort(key=lambda x: x.get("created_at", 0), reverse=True)
    return JSONResponse(content=pack_list)

@app.post("/api/open-pack/{pack_id}")
async def open_pack(pack_id: str):
    packs = load_packs()
    if pack_id not in packs:
        raise HTTPException(status_code=404, detail="Pack not found")

    pack = packs[pack_id]
    if pack["status"] == "processing":
        raise HTTPException(status_code=400, detail="Pack is still processing")

    # Reveal cards
    cards = load_cards()
    revealed_cards = []

    for md5 in pack["cards"]:
        if md5 in cards:
            card = cards[md5]
            card["hidden"] = False # Unhide!
            cards[md5] = card
            revealed_cards.append(card)

    pack["status"] = "opened"
    packs[pack_id] = pack

    save_cards(cards)
    save_packs(packs)

    return JSONResponse(content=revealed_cards)

# Settings Endpoints
@app.get("/api/settings")
async def get_settings():
    return JSONResponse(content=load_settings())

class SettingsModel(dict):
    pass

@app.post("/api/settings")
async def update_settings(settings: dict):
    # Validate structure?
    # Expected: { "prompts": { "rarity": "...", ... } }
    current_settings = load_settings()
    current_settings.update(settings)
    save_settings(current_settings)
    return JSONResponse(content={"message": "Settings saved"})

@app.get("/api/cards")
async def get_cards():
    cards = load_cards()
    valid_cards = []
    
    for md5, card in cards.items():
        # Check hidden status
        if card.get("hidden", False):
            continue

        file_path = os.path.join(UPLOAD_DIR, card['filename'])
        if os.path.exists(file_path):
            valid_cards.append(card)
            
    return JSONResponse(content=valid_cards, media_type="application/json; charset=utf-8")

@app.get("/api/card-backs")
async def list_card_backs():
    files = get_available_card_backs()
    return JSONResponse(content={"card_backs": files}, media_type="application/json; charset=utf-8")

@app.get("/")
async def read_index():
    return FileResponse('static/index.html')

@app.get("/batch")
async def read_batch():
    return FileResponse('static/batch.html')

@app.get("/god-draw")
async def read_god_draw():
    return FileResponse('static/god_draw.html')

@app.get("/packs")
async def read_packs():
    return FileResponse('static/packs.html')

@app.get("/settings")
async def read_settings():
    return FileResponse('static/settings.html')
