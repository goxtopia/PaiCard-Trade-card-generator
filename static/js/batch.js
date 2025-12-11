document.addEventListener('DOMContentLoaded', () => {
    const cardBackList = document.getElementById('cardBackList');
    const filesInput = document.getElementById('filesInput');
    const batchGenerateBtn = document.getElementById('batchGenerateBtn');
    const statusText = document.getElementById('statusText');
    const batchGrid = document.getElementById('batchGrid');
    const fileCount = document.getElementById('fileCount');

    let selectedCardBackUrl = '';

    // Load Card Backs
    fetch('/api/card-backs')
        .then(res => res.json())
        .then(data => {
            if (data.card_backs && data.card_backs.length > 0) {
                data.card_backs.forEach((backUrl, index) => {
                    const div = document.createElement('div');
                    div.className = 'card-back-option';
                    div.style.backgroundImage = `url(${backUrl})`;
                    if (index === 0) {
                        div.classList.add('selected');
                        selectedCardBackUrl = backUrl;
                    }

                    div.onclick = () => {
                        document.querySelectorAll('.card-back-option').forEach(el => el.classList.remove('selected'));
                        div.classList.add('selected');
                        selectedCardBackUrl = backUrl;
                    };
                    cardBackList.appendChild(div);
                });
            }
        });

    // File Input Handling
    filesInput.addEventListener('change', (e) => {
        const files = Array.from(e.target.files);
        if (files.length > 0) {
            batchGenerateBtn.disabled = false;
            if (files.length > 10) {
                fileCount.textContent = `${files.length} files selected (Only 10 random files will be processed)`;
                fileCount.style.color = '#ffaa00';
            } else {
                fileCount.textContent = `${files.length} files selected`;
                fileCount.style.color = '#ccc';
            }
        } else {
            batchGenerateBtn.disabled = true;
            fileCount.textContent = "0 files selected";
        }
    });

    // Batch Generation
    batchGenerateBtn.addEventListener('click', async () => {
        const files = filesInput.files;
        if (files.length === 0) return;

        batchGenerateBtn.disabled = true;
        statusText.textContent = "Summoning cards... This may take a while.";

        // Clear previous grid
        batchGrid.innerHTML = '';

        const formData = new FormData();
        Array.from(files).forEach(file => {
            formData.append('files', file);
        });

        if (selectedCardBackUrl) {
            formData.append('card_back', selectedCardBackUrl);
        }

        try {
            const response = await fetch('/api/batch-generate', {
                method: 'POST',
                body: formData
            });

            if (!response.ok) throw new Error('Batch generation failed');

            const cards = await response.json();

            statusText.textContent = `Summoned ${cards.length} cards! Click to reveal them.`;

            renderBatchCards(cards, selectedCardBackUrl);

        } catch (error) {
            console.error(error);
            statusText.textContent = "Summoning failed!";
            alert('Failed to generate cards. Please try again.');
        } finally {
            batchGenerateBtn.disabled = false;
        }
    });

    function renderBatchCards(cards, defaultBackUrl) {
        cards.forEach((card, index) => {
            const wrapper = document.createElement('div');
            wrapper.className = 'batch-card-wrapper';

            // Initial state: Flipped (showing back)
            // We use a slight delay for each card to appear? No, just render them.

            const cardEl = document.createElement('div');
            cardEl.className = `card rarity-${card.rarity.toLowerCase()} is-flipped`; // Start flipped

            // Front Face
            const front = document.createElement('div');
            front.className = 'card-face card-front';

            // Build Front Content (Reusing structure manually to avoid dependency on main script)
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

            // Adjust description size after insertion
            const descEl = front.querySelector('.card-desc-text');
            adjustDescriptionSize(descEl);

            // Back Face
            const back = document.createElement('div');
            back.className = 'card-face card-back';

            const backImg = document.createElement('img');
            backImg.src = card.card_back || defaultBackUrl || '/static/card_backs/default.svg';
            back.appendChild(backImg);

            cardEl.appendChild(front);
            cardEl.appendChild(back);
            wrapper.appendChild(cardEl);

            // Click to Flip Logic
            wrapper.addEventListener('click', () => {
                cardEl.classList.toggle('is-flipped');
            });

            batchGrid.appendChild(wrapper);
        });
    }

    function adjustDescriptionSize(element) {
        // Reset to base size
        element.style.fontSize = '0.9em';
        const textLength = element.textContent.length;

        // Basic heuristic for font scaling (scaled for batch card size)
        if (textLength > 150) {
            element.style.fontSize = '0.7em';
        } else if (textLength > 100) {
            element.style.fontSize = '0.8em';
        }
    }
});
