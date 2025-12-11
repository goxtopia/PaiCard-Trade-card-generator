function triggerRarityFX(rarity) {
    if (!rarity) return;
    rarity = rarity.toUpperCase();

    // Create FX container if not exists
    let fxContainer = document.getElementById('fx-container');
    if (!fxContainer) {
        fxContainer = document.createElement('div');
        fxContainer.id = 'fx-container';
        fxContainer.style.position = 'fixed';
        fxContainer.style.top = '0';
        fxContainer.style.left = '0';
        fxContainer.style.width = '100%';
        fxContainer.style.height = '100%';
        fxContainer.style.pointerEvents = 'none';
        fxContainer.style.zIndex = '9999';
        document.body.appendChild(fxContainer);
    }

    // Clear previous FX timeout to avoid buildup if rapid clicking?
    // Nah, overlapping FX is cool.

    if (rarity === 'UR' || rarity === 'SSR') {
        createFlash(fxContainer, rarity === 'UR' ? '#ffd700' : '#8e44ad');
        createParticles(fxContainer, 50);
        shakeScreen();
    } else if (rarity === 'SR') {
        createFlash(fxContainer, '#f1c40f', 0.5);
        createParticles(fxContainer, 20);
    } else {
        // N/R: Subtle flash
        createFlash(fxContainer, '#ffffff', 0.2);
    }
}

function createFlash(container, color, duration = 1) {
    const flash = document.createElement('div');
    flash.style.position = 'absolute';
    flash.style.top = '0';
    flash.style.left = '0';
    flash.style.width = '100%';
    flash.style.height = '100%';
    flash.style.backgroundColor = color;
    flash.style.opacity = '0.8';
    flash.style.transition = `opacity ${duration}s ease-out`;

    container.appendChild(flash);

    // Trigger reflow
    void flash.offsetWidth;

    flash.style.opacity = '0';

    setTimeout(() => {
        if (flash.parentNode) flash.parentNode.removeChild(flash);
    }, duration * 1000);
}

function createParticles(container, count) {
    for (let i = 0; i < count; i++) {
        const p = document.createElement('div');
        p.style.position = 'absolute';
        p.style.left = '50%';
        p.style.top = '50%';
        p.style.width = '8px';
        p.style.height = '8px';
        p.style.borderRadius = '50%';
        p.style.backgroundColor = `hsl(${Math.random() * 360}, 100%, 50%)`;

        // Random velocity
        const angle = Math.random() * Math.PI * 2;
        const speed = Math.random() * 200 + 100;
        const tx = Math.cos(angle) * speed;
        const ty = Math.sin(angle) * speed;

        p.style.transition = 'transform 1s ease-out, opacity 1s ease-out';

        container.appendChild(p);

        // Animate
        requestAnimationFrame(() => {
            p.style.transform = `translate(${tx}px, ${ty}px)`;
            p.style.opacity = '0';
        });

        setTimeout(() => {
            if (p.parentNode) p.parentNode.removeChild(p);
        }, 1000);
    }
}

function shakeScreen() {
    document.body.style.animation = 'shake 0.5s';
    setTimeout(() => {
        document.body.style.animation = '';
    }, 500);
}

// Inject shake keyframes if missing
if (!document.getElementById('shake-style')) {
    const style = document.createElement('style');
    style.id = 'shake-style';
    style.innerHTML = `
        @keyframes shake {
            0% { transform: translate(1px, 1px) rotate(0deg); }
            10% { transform: translate(-1px, -2px) rotate(-1deg); }
            20% { transform: translate(-3px, 0px) rotate(1deg); }
            30% { transform: translate(3px, 2px) rotate(0deg); }
            40% { transform: translate(1px, -1px) rotate(1deg); }
            50% { transform: translate(-1px, 2px) rotate(-1deg); }
            60% { transform: translate(-3px, 1px) rotate(0deg); }
            70% { transform: translate(3px, 1px) rotate(-1deg); }
            80% { transform: translate(-1px, -1px) rotate(1deg); }
            90% { transform: translate(1px, 2px) rotate(0deg); }
            100% { transform: translate(1px, -2px) rotate(-1deg); }
        }
    `;
    document.head.appendChild(style);
}
