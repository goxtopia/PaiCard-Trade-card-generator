from fastapi import FastAPI, UploadFile, File, HTTPException, Form
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
from typing import Optional, List
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

os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(CARD_BACKS_DIR, exist_ok=True)

# Helper for Card Database
def load_cards():
    if not os.path.exists(CARDS_DB):
        return {}
    with open(CARDS_DB, "r") as f:
        return json.load(f)

def save_cards(cards):
    with open(CARDS_DB, "w") as f:
        json.dump(cards, f, indent=2)

def calculate_md5(file_path):
    hash_md5 = hashlib.md5()
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(4096), b""):
            hash_md5.update(chunk)
    return hash_md5.hexdigest()

# Initialize VLM Service
API_BASE = os.getenv("VLM_API_BASE", "http://192.168.124.22:8080")
API_KEY = os.getenv("VLM_API_KEY", "sk-placeholder")
USE_STUB = os.getenv("USE_STUB", "true").lower() == "true"

vlm_service = VLMService(api_base=API_BASE, api_key=API_KEY, use_stub=USE_STUB)

app.mount("/static", StaticFiles(directory="static"), name="static")
app.mount("/uploads", StaticFiles(directory="uploads"), name="uploads")

def process_single_file_generation(file_path, file_md5, card_back, existing_card=None):
    # Analyze
    analysis = vlm_service.analyze_image(file_path)

    filename = os.path.basename(file_path)

    new_card = {
        "md5": file_md5,
        "filename": filename,
        "image_url": f"/uploads/{filename}",
        "rarity": analysis["rarity"],
        "name": analysis["name"],
        "description": analysis["description"],
        "atk": analysis.get("atk", "0"),
        "def": analysis.get("def", "0"),
        "card_back": card_back
    }

    # Preserve existing card_back if not provided
    if existing_card and not card_back and "card_back" in existing_card:
        new_card["card_back"] = existing_card["card_back"]

    return new_card

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
            
            new_card = process_single_file_generation(file_path, existing_md5, card_back, existing_card=card_data)
            
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
            
        new_card = process_single_file_generation(final_path, file_md5, card_back)
        
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

            new_card = process_single_file_generation(final_path, file_md5, card_back)
            cards[file_md5] = new_card
            generated_cards.append(new_card)

        save_cards(cards)
        return JSONResponse(content=generated_cards, media_type="application/json; charset=utf-8")

    except Exception as e:
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/api/cards")
async def get_cards():
    cards = load_cards()
    valid_cards = []
    
    # Filter out cards where the file is missing
    # We do not delete them from DB automatically to avoid race conditions,
    # but we prevent the frontend from trying to load them.
    for md5, card in cards.items():
        file_path = os.path.join(UPLOAD_DIR, card['filename'])
        if os.path.exists(file_path):
            valid_cards.append(card)
            
    return JSONResponse(content=valid_cards, media_type="application/json; charset=utf-8")

@app.get("/api/card-backs")
async def list_card_backs():
    files = []
    if os.path.exists(CARD_BACKS_DIR):
        for f in os.listdir(CARD_BACKS_DIR):
            if f.lower().endswith(('.png', '.jpg', '.jpeg', '.webp', '.svg')):
                files.append(f"/static/card_backs/{f}")
    return JSONResponse(content={"card_backs": files}, media_type="application/json; charset=utf-8")

@app.get("/")
async def read_index():
    return FileResponse('static/index.html')

@app.get("/batch")
async def read_batch():
    return FileResponse('static/batch.html')
