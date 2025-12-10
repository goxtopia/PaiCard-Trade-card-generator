document.addEventListener('DOMContentLoaded', () => {
    const uploadArea = document.getElementById('uploadArea');
    const imageInput = document.getElementById('imageInput');
    const generateBtn = document.getElementById('generateBtn');
    const cardElement = document.getElementById('cardElement');
    // const flipBtn = document.getElementById('flipBtn'); // Removed
    const cardBackList = document.getElementById('cardBackList');
    const statusText = document.getElementById('statusText');

    // Card Elements
    const cardName = document.getElementById('cardName');
    const cardRarity = document.getElementById('cardRarity');
    const cardDescription = document.getElementById('cardDescription');
    const cardImage = document.getElementById('cardImage');
    const cardBackImage = document.getElementById('cardBackImage');
    const cardRarityContainer = document.getElementById('cardRarityContainer');
    const cardAtk = document.getElementById('cardAtk');
    const cardDef = document.getElementById('cardDef');
    const downloadBtn = document.getElementById('downloadBtn');
    const regenerateBtn = document.getElementById('regenerateBtn');
    const cardLibraryGrid = document.getElementById('cardLibraryGrid');
    const openLibraryBtn = document.getElementById('openLibraryBtn');
    const closeLibraryBtn = document.getElementById('closeLibraryBtn');
    const libraryModal = document.getElementById('libraryModal');

    let currentFile = null;
    let currentCardData = null; // Store current card data for regenerate logic

    // Load Card Backs
    fetch('/api/card-backs')
        .then(res => res.json())
        .then(data => {
            if (data.card_backs && data.card_backs.length > 0) {
                data.card_backs.forEach((backUrl, index) => {
                    const div = document.createElement('div');
                    div.className = 'card-back-option';
                    div.style.backgroundImage = `url(${backUrl})`;
                    if (index === 0) div.classList.add('selected');
                    
                    div.onclick = () => {
                        document.querySelectorAll('.card-back-option').forEach(el => el.classList.remove('selected'));
                        div.classList.add('selected');
                        cardBackImage.src = backUrl;
                    };
                    cardBackList.appendChild(div);
                });
                // Set default
                cardBackImage.src = data.card_backs[0];
            }
        });

    // Modal Logic
    openLibraryBtn.addEventListener('click', () => {
        libraryModal.style.display = 'block';
        loadLibrary(); // Reload when opening
    });

    closeLibraryBtn.addEventListener('click', () => {
        libraryModal.style.display = 'none';
    });

    window.addEventListener('click', (e) => {
        if (e.target == libraryModal) {
            libraryModal.style.display = 'none';
        }
    });

    // Helper to create Mini Card DOM (Scaled Down)
    function createMiniCardDOM(data) {
        // Create wrapper
        const wrapper = document.createElement('div');
        wrapper.className = 'library-item';
        
        // Create scaled container
        const container = document.createElement('div');
        container.className = 'mini-card-container';
        
        // Create Front Face (similar structure to index.html)
        const cardFront = document.createElement('div');
        cardFront.className = `card-front rarity-${data.rarity.toLowerCase()}`;
        
        // Content
        const frameContent = document.createElement('div');
        frameContent.className = 'card-frame-content';
        
        // Header
        const header = document.createElement('div');
        header.className = 'card-top-header';
        header.innerHTML = `
            <div class="card-name-box">${data.name}</div>
            <div class="card-attribute-box">${data.rarity}</div>
        `;
        
        // Image
        const imgWrapper = document.createElement('div');
        imgWrapper.className = 'card-image-wrapper';
        const imgDiv = document.createElement('div');
        imgDiv.className = 'card-art-div';
        imgDiv.style.backgroundImage = `url(${data.image_url})`;
        imgWrapper.appendChild(imgDiv);
        
        // Info
        const infoBox = document.createElement('div');
        infoBox.className = 'card-info-box';
        infoBox.innerHTML = `
            <div class="card-type">[ AI / Effect ]</div>
            <div class="card-desc-text" style="font-size: 0.85rem">${data.description}</div>
            <div class="card-stats">
                <span>ATK / ${data.atk || "?"}</span>
                <span>DEF / ${data.def || "?"}</span>
            </div>
        `;
        
        // Assemble
        frameContent.appendChild(header);
        frameContent.appendChild(imgWrapper);
        frameContent.appendChild(infoBox);
        cardFront.appendChild(frameContent);
        
        // Add overlay if rarity demands (light pollution)
        const overlay = document.createElement('div');
        overlay.className = 'rarity-overlay';
        cardFront.appendChild(overlay);

        container.appendChild(cardFront);
        wrapper.appendChild(container);
        
        return wrapper;
    }

    // Load Library
    function loadLibrary() {
        fetch('/api/cards')
            .then(res => res.json())
            .then(cards => {
                cardLibraryGrid.innerHTML = '';
                cards.forEach(card => {
                    const miniCard = createMiniCardDOM(card);
                    miniCard.onclick = () => loadCardToView(card);
                    cardLibraryGrid.appendChild(miniCard);
                });
            });
    }
    // Initial load
    loadLibrary();

    function loadCardToView(data) {
        currentCardData = data;
        libraryModal.style.display = 'none'; // Close modal on select
        
        // Update Card UI
        cardName.textContent = data.name;
        cardRarity.textContent = data.rarity;
        cardDescription.textContent = data.description;
        cardAtk.textContent = data.atk || "?";
        cardDef.textContent = data.def || "?";
        cardImage.style.backgroundImage = `url(${data.image_url})`;
        
        // Adjust description font size if needed
        adjustDescriptionSize(cardDescription);

        // Reset Rarities
        cardElement.className = 'card'; // Reset to just 'card'
        cardElement.classList.add(`rarity-${data.rarity.toLowerCase()}`);
        
        // Show regenerate button
        regenerateBtn.style.display = 'inline-block';
        
        statusText.textContent = `Loaded ${data.name} from library.`;
    }

    // File Upload Handlers
    uploadArea.addEventListener('click', () => imageInput.click());

    uploadArea.addEventListener('dragover', (e) => {
        e.preventDefault();
        uploadArea.classList.add('drag-over');
    });

    uploadArea.addEventListener('dragleave', () => {
        uploadArea.classList.remove('drag-over');
    });

    uploadArea.addEventListener('drop', (e) => {
        e.preventDefault();
        uploadArea.classList.remove('drag-over');
        if (e.dataTransfer.files.length > 0) {
            handleFileSelect(e.dataTransfer.files[0]);
        }
    });

    imageInput.addEventListener('change', (e) => {
        if (e.target.files.length > 0) {
            handleFileSelect(e.target.files[0]);
        }
    });

    function handleFileSelect(file) {
        if (!file.type.startsWith('image/')) {
            alert('Please upload an image file.');
            return;
        }
        currentFile = file;
        generateBtn.disabled = false;
        statusText.textContent = `Selected: ${file.name}`;
        
        // Preview
        const reader = new FileReader();
        reader.onload = (e) => {
            uploadArea.style.backgroundImage = `url(${e.target.result})`;
            uploadArea.style.backgroundSize = 'contain';
            uploadArea.style.backgroundRepeat = 'no-repeat';
            uploadArea.style.backgroundPosition = 'center';
            uploadArea.innerHTML = '';
        };
        reader.readAsDataURL(file);
    }

    // Generate Logic
    async function performGeneration(formData) {
        generateBtn.disabled = true;
        regenerateBtn.disabled = true;
        statusText.textContent = "Summoning card...";

        try {
            const response = await fetch('/api/generate', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) throw new Error('Generation failed');

            const data = await response.json();
            currentCardData = data;
            
            loadCardToView(data);

            // Trigger Summoning Animation
            cardElement.classList.add('is-summoning');
            setTimeout(() => {
                cardElement.classList.remove('is-summoning');
            }, 2000);

            statusText.textContent = `Successfully summoned ${data.name}!`;
            
            // Ensure card is showing front
            cardElement.classList.remove('is-flipped');
            
            // Refresh Library
            loadLibrary();

        } catch (error) {
            console.error(error);
            statusText.textContent = "Summoning failed!";
            alert('Failed to generate card. Please try again.');
        } finally {
            generateBtn.disabled = false;
            regenerateBtn.disabled = false;
        }
    }

    // Generate Button (New Upload)
    generateBtn.addEventListener('click', async () => {
        if (!currentFile) return;
        const formData = new FormData();
        formData.append('file', currentFile);
        await performGeneration(formData);
    });

    // Regenerate Button (Existing MD5)
    regenerateBtn.addEventListener('click', async () => {
        if (!currentCardData || !currentCardData.md5) return;
        
        if (!confirm("Regenerate this card's stats? The image will remain the same.")) return;

        const formData = new FormData();
        formData.append('existing_md5', currentCardData.md5);
        formData.append('regenerate', 'true');
        await performGeneration(formData);
    });

    function adjustDescriptionSize(element) {
        // Reset to base size
        element.style.fontSize = '0.85rem';
        const textLength = element.textContent.length;
        
        // Basic heuristic for font scaling
        if (textLength > 150) {
            element.style.fontSize = '0.7rem';
        } else if (textLength > 100) {
            element.style.fontSize = '0.75rem';
        }
        // CSS also handles text-overflow: ellipsis for ultimate safety
    }

    // 3D Tilt & Drag Logic (with Auto Flip)
    const scene = document.querySelector('.scene');
    let isDragging = false;
    let startX, startY;
    let currentRotateY = 0; // Total accumulated rotation Y
    let currentRotateX = 0;

    // We store the 'base' rotation (0 or 180) in the class is-flipped,
    // but for smooth dragging we need continuous values.
    // On drag start, we assume current rotation matches the state.

    scene.addEventListener('mousedown', (e) => {
        isDragging = true;
        startX = e.clientX;
        startY = e.clientY;
        
        // Initialize rotation based on current state
        const isFlipped = cardElement.classList.contains('is-flipped');
        currentRotateY = isFlipped ? 180 : 0;
        currentRotateX = 0; // Reset X tilt on new drag

        cardElement.classList.remove('is-springing');
        cardElement.style.cursor = 'grabbing';
    });

    window.addEventListener('mousemove', (e) => {
        if (!isDragging) return;
        
        const deltaX = e.clientX - startX;
        const deltaY = e.clientY - startY;
        
        // Sensitivity
        const rotY = deltaX * 0.5; 
        const rotX = -deltaY * 0.5;

        // Apply visual rotation
        // We add the delta to the base state (captured at mousedown)
        const totalY = currentRotateY + rotY;
        
        cardElement.style.transform = `rotateX(${rotX}deg) rotateY(${totalY}deg)`;
    });

    window.addEventListener('mouseup', (e) => {
        if (!isDragging) return;
        isDragging = false;
        cardElement.style.cursor = 'grab';
        
        // Calculate total rotation from drag
        const deltaX = e.clientX - startX;
        const rotY = deltaX * 0.5;
        const finalY = currentRotateY + rotY;
        
        // Determine snap target
        // Normalize angle to 0-360 range for logic (simplistic)
        // Actually, we just care if we crossed the 90 degree threshold from our start.
        
        // We define "Flipped" as being near 180deg (back showing). "Normal" is 0deg.
        // If finalY is closer to 180 (or -180) than 0, we snap to flipped.
        
        // Use modulo to handle multiple spins
        let normalizedY = finalY % 360; 
        if (normalizedY < 0) normalizedY += 360;
        
        // 0-90 or 270-360 => Front
        // 90-270 => Back
        
        const showBack = (normalizedY > 90 && normalizedY < 270);
        
        cardElement.classList.add('is-springing');
        
        if (showBack) {
            cardElement.classList.add('is-flipped');
            cardElement.style.transform = 'rotateY(180deg)';
        } else {
            cardElement.classList.remove('is-flipped');
            cardElement.style.transform = 'rotateY(0deg)';
        }
    });
    
    scene.addEventListener('mouseleave', () => {
        if(isDragging) {
             window.dispatchEvent(new Event('mouseup'));
        }
    });


    // Download Button
    downloadBtn.addEventListener('click', () => {
        if (!html2canvas) {
            alert('Download library not loaded.');
            return;
        }

        // Determine which face is visible
        const isFlipped = cardElement.classList.contains('is-flipped');
        const targetFace = isFlipped ? document.querySelector('.card-back') : document.querySelector('.card-front');
        
        // We capture the target face directly to avoid 3D transform issues in html2canvas
        html2canvas(targetFace, {
            scale: 2, // Higher resolution
            backgroundColor: null,
            useCORS: true // Important for loaded images
        }).then(canvas => {
            const link = document.createElement('a');
            link.download = `ai-card-${Date.now()}.png`;
            link.href = canvas.toDataURL();
            link.click();
        }).catch(err => {
            console.error(err);
            alert('Failed to download image.');
        });
    });
});
