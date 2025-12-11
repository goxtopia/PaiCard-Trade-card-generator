document.addEventListener('DOMContentLoaded', () => {
    const filesInput = document.getElementById('filesInput');
    const uploadBtn = document.getElementById('uploadBtn');
    const statusText = document.getElementById('statusText');
    const fileCount = document.getElementById('fileCount');
    const packGrid = document.getElementById('packGrid');
    const packsView = document.getElementById('packsView');
    const openedView = document.getElementById('openedView');
    const openedGrid = document.getElementById('openedGrid');
    const backToPacksBtn = document.getElementById('backToPacksBtn');

    let pollInterval = null;

    // File Input
    filesInput.addEventListener('change', (e) => {
        const files = e.target.files;
        if (files.length > 0) {
            fileCount.textContent = `${files.length} files selected`;
            uploadBtn.disabled = false;
        } else {
            fileCount.textContent = "0 files selected";
            uploadBtn.disabled = true;
        }
    });

    // Upload & Create Packs
    uploadBtn.addEventListener('click', async () => {
        const files = filesInput.files;
        if (files.length === 0) return;

        uploadBtn.disabled = true;
        statusText.textContent = "Uploading and creating packs...";

        const formData = new FormData();
        Array.from(files).forEach(file => {
            formData.append('files', file);
        });

        try {
            const response = await fetch('/api/upload-packs', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) throw new Error('Upload failed');

            const data = await response.json();
            statusText.textContent = data.message;

            // Clear input
            filesInput.value = '';
            fileCount.textContent = "0 files selected";

            loadPacks(); // Refresh list

        } catch (error) {
            console.error(error);
            statusText.textContent = "Upload failed!";
            alert('Failed to create packs.');
            uploadBtn.disabled = false;
        }
    });

    // Load Packs List
    function loadPacks() {
        fetch('/api/packs')
            .then(res => res.json())
            .then(packs => {
                renderPacks(packs);

                // Poll if any processing
                const anyProcessing = packs.some(p => p.status === 'processing');
                if (anyProcessing && !pollInterval) {
                    pollInterval = setInterval(loadPacks, 3000);
                } else if (!anyProcessing && pollInterval) {
                    clearInterval(pollInterval);
                    pollInterval = null;
                }
            });
    }

    function renderPacks(packs) {
        packGrid.innerHTML = '';
        packs.forEach(pack => {
            const div = document.createElement('div');
            div.className = `pack-item ${pack.status}`;

            let statusLabel = pack.status === 'processing' ? 'Processing...' : (pack.status === 'opened' ? 'Opened' : 'Ready to Open');
            if (pack.status === 'ready') div.classList.add('ready');

            div.innerHTML = `
                <div class="pack-label">Card Pack</div>
                <div class="pack-status">${statusLabel}</div>
                <div class="pack-status" style="font-size: 0.8em; margin-top: 5px;">${pack.cards.length} Cards</div>
            `;

            if (pack.status === 'ready' || pack.status === 'opened') {
                div.onclick = () => openPack(pack.id);
            }

            packGrid.appendChild(div);
        });
    }

    // Initial Load
    loadPacks();

    // Open Pack Logic
    async function openPack(packId) {
        // Switch view
        packsView.style.display = 'none';
        openedView.style.display = 'block';
        openedGrid.innerHTML = '<p style="color:white; text-align:center; width:100%;">Opening pack...</p>';

        try {
            const response = await fetch(`/api/open-pack/${packId}`, { method: 'POST' });
            if (!response.ok) throw new Error('Failed to open pack');

            const cards = await response.json();
            renderOpenedCards(cards);

            // Refresh packs list in background so it's updated when we go back
            loadPacks();

        } catch (e) {
            console.error(e);
            alert('Error opening pack');
            backToPacksBtn.click();
        }
    }

    function renderOpenedCards(cards) {
        openedGrid.innerHTML = '';
        cards.forEach(card => {
            const wrapper = document.createElement('div');
            wrapper.className = 'batch-card-wrapper';

            const cardEl = document.createElement('div');
            cardEl.className = `card rarity-${card.rarity.toLowerCase()} is-flipped`;

            if (card.color_theme) cardEl.classList.add(card.color_theme);
            if (card.effect_type) cardEl.classList.add(card.effect_type);

            // Front
            const front = document.createElement('div');
            front.className = 'card-face card-front';
            front.innerHTML = `
                <div class="rarity-overlay"></div>
                <div class="card-frame-content">
                    <div class="card-top-header">
                        <div class="card-name-box">${card.name}</div>
                        <div class="card-attribute-box">${card.rarity}</div>
                    </div>
                    <div class="card-image-wrapper">
                        <div class="card-art-div" style="background-image: url('${card.image_url}')"></div>
                    </div>
                    <div class="card-info-box">
                        <div class="card-type">[ AI / Effect ]</div>
                        <div class="card-desc-text">${card.description}</div>
                        <div class="card-stats">
                            <span>ATK / ${card.atk}</span>
                            <span>DEF / ${card.def}</span>
                        </div>
                    </div>
                </div>
            `;

            // Back
            const back = document.createElement('div');
            back.className = 'card-face card-back';
            const backImg = document.createElement('img');
            backImg.src = card.card_back || '/static/card_backs/default.svg';
            back.appendChild(backImg);

            cardEl.appendChild(front);
            cardEl.appendChild(back);
            wrapper.appendChild(cardEl);

            // Flip logic
            wrapper.addEventListener('click', () => {
                cardEl.classList.toggle('is-flipped');
            });

            openedGrid.appendChild(wrapper);

            // Adjust font size
            const descEl = front.querySelector('.card-desc-text');
            adjustDescriptionSize(descEl);
        });
    }

    backToPacksBtn.addEventListener('click', () => {
        openedView.style.display = 'none';
        packsView.style.display = 'flex';
        loadPacks();
    });

    function adjustDescriptionSize(element) {
        element.style.fontSize = '0.9em';
        const textLength = element.textContent.length;
        if (textLength > 150) {
            element.style.fontSize = '0.7em';
        } else if (textLength > 100) {
            element.style.fontSize = '0.8em';
        }
    }
});
