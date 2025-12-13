document.addEventListener('DOMContentLoaded', () => {
    const fields = {
        rarity: document.getElementById('promptRarity'),
        name: document.getElementById('promptName'),
        description: document.getElementById('promptDescription'),
        atk: document.getElementById('promptAtk'),
        def: document.getElementById('promptDef')
    };

    const singleCallMode = document.getElementById('singleCallMode');
    const promptSingle = document.getElementById('promptSingle');
    const singleCallSection = document.getElementById('singleCallSection');
    const multiCallSection = document.getElementById('multiCallSection');

    const saveBtn = document.getElementById('saveBtn');
    const resetBtn = document.getElementById('resetBtn');
    const statusText = document.getElementById('statusText');

    function toggleSections() {
        if (singleCallMode.checked) {
            singleCallSection.style.display = 'block';
            multiCallSection.style.display = 'none';
        } else {
            singleCallSection.style.display = 'none';
            multiCallSection.style.display = 'block';
        }
    }

    singleCallMode.addEventListener('change', toggleSections);

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

            if (data.single_call_mode) {
                singleCallMode.checked = true;
            } else {
                singleCallMode.checked = false;
            }

            if (data.single_call_prompt) {
                promptSingle.value = data.single_call_prompt;
            }

            toggleSections();
        });

    // Save Settings
    saveBtn.addEventListener('click', async () => {
        const prompts = {};
        for (const key in fields) {
            prompts[key] = fields[key].value;
        }

        const settings = {
            prompts: prompts,
            single_call_mode: singleCallMode.checked,
            single_call_prompt: promptSingle.value
        };

        saveBtn.disabled = true;
        statusText.textContent = "Saving...";

        try {
            const response = await fetch('/api/settings', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(settings)
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
            singleCallMode.checked = false;
            promptSingle.value = "";
            toggleSections();
            saveBtn.click();
        }
    });
});
