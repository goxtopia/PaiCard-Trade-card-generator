document.addEventListener('DOMContentLoaded', () => {
    const drawBtn = document.getElementById('drawBtn');
    const statusText = document.getElementById('statusText');
    const batchGrid = document.getElementById('batchGrid');

    // God Draw Generation
    drawBtn.addEventListener('click', async () => {
        drawBtn.disabled = true;
        statusText.textContent = "Channeling cosmic energy... (Downloading images and summoning)";

        // Clear previous grid
        batchGrid.innerHTML = '';

        try {
            const response = await fetch('/api/god-draw', {
                method: 'POST'
            });

            if (!response.ok) throw new Error('God Draw failed');

            const cards = await response.json();

            statusText.textContent = `The Gods have granted you ${cards.length} cards! Click to reveal your destiny.`;

            renderCards(cards);

        } catch (error) {
            console.error(error);
            statusText.textContent = "The connection to the cosmos was interrupted!";
            alert('Failed to draw cards. Please try again.');
        } finally {
            drawBtn.disabled = false;
        }
    });

    function renderCards(cards) {
        cards.forEach((card, index) => {
            const wrapper = document.createElement('div');
            wrapper.className = 'batch-card-wrapper';

            // Initial state: Flipped (showing back)
            const cardEl = document.createElement('div');
            cardEl.className = `card rarity-${card.rarity.toLowerCase()} is-flipped`; // Start flipped

            // Front Face
            const front = document.createElement('div');
            front.className = 'card-face card-front';

            // Build Front Content
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
            // Use the bound card back, or random default?
            // The backend already assigns a card back in God Draw mode.
            backImg.src = card.card_back || '/static/card_backs/default.svg';
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
