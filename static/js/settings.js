document.addEventListener('DOMContentLoaded', () => {
    const fields = {
        rarity: document.getElementById('promptRarity'),
        name: document.getElementById('promptName'),
        description: document.getElementById('promptDescription'),
        atk: document.getElementById('promptAtk'),
        def: document.getElementById('promptDef')
    };

    const saveBtn = document.getElementById('saveBtn');
    const resetBtn = document.getElementById('resetBtn');
    const statusText = document.getElementById('statusText');

    // Load Settings
    fetch('/api/settings')
        .then(res => res.json())
        .then(data => {
            if (data.prompts) {
                for (const key in fields) {
                    if (data.prompts[key]) {
                        fields[key].value = data.prompts[key];
                    }
                }
            }
        });

    // Save Settings
    saveBtn.addEventListener('click', async () => {
        const prompts = {};
        for (const key in fields) {
            prompts[key] = fields[key].value;
        }

        saveBtn.disabled = true;
        statusText.textContent = "Saving...";

        try {
            const response = await fetch('/api/settings', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ prompts })
            });

            if (response.ok) {
                statusText.textContent = "Settings saved successfully!";
                setTimeout(() => statusText.textContent = "", 3000);
            } else {
                throw new Error("Failed to save");
            }
        } catch (e) {
            statusText.textContent = "Error saving settings.";
            console.error(e);
        } finally {
            saveBtn.disabled = false;
        }
    });

    // Reset Defaults
    resetBtn.addEventListener('click', () => {
        if (confirm("Are you sure you want to clear all custom prompts?")) {
            for (const key in fields) {
                fields[key].value = "";
            }
            saveBtn.click();
        }
    });
});
