// sidepanel.js

let favorites = [];
let myName = "";

// Initialize: Load favorites and name from storage
chrome.storage.local.get(['favorites', 'myName'], (localResult) => {
    // Check if migration is needed (if local is empty but sync has data)
    if (!localResult.favorites && !localResult.myName) {
        chrome.storage.sync.get(['favorites', 'myName'], (syncResult) => {
            if (syncResult.favorites || syncResult.myName) {
                // Migrate to local
                favorites = syncResult.favorites || [];
                myName = syncResult.myName || "";
                
                chrome.storage.local.set({ favorites, myName }, () => {
                    console.log("[Scholar Scraper] Migrated data to local storage.");
                    initializeUI();
                });
            } else {
                initializeUI();
            }
        });
    } else {
        favorites = localResult.favorites || [];
        myName = localResult.myName || "";
        initializeUI();
    }
});

// Helper to remove favorite (defined at top level for access)
function removeFavorite(index) {
    favorites.splice(index, 1);
    chrome.storage.local.set({ favorites }, () => {
        renderFavorites();
    });
}

function initializeUI() {
    if (myName) {
        document.getElementById('myNameInput').value = myName;
        updateMeBadge();
    }
    if (favorites.length > 0) {
        renderFavorites();
        // Auto refresh all cards on startup
        refreshAllCards(); 
    }
}

function updateMeBadge() {
    const badge = document.getElementById('meBadge');
    const nameSpan = document.getElementById('currentMe');
    if (myName) {
        badge.style.display = 'block';
        nameSpan.innerText = myName;
    } else {
        badge.style.display = 'none';
    }
}

// Update global myName whenever the input changes
document.getElementById('myNameInput').addEventListener('input', (e) => {
    myName = e.target.value.trim();
    chrome.storage.local.set({ myName }, () => {
        updateMeBadge();
    });
});

function renderFavorites() {
    const favoritesSection = document.getElementById('favoritesSection');
    const favoritesList = document.getElementById('favoritesList');
    
    if (favorites.length === 0) {
        favoritesSection.style.display = 'none';
        return;
    }

    favoritesSection.style.display = 'block';
    favoritesList.innerHTML = '';

    favorites.forEach((fav, index) => {
        const card = document.createElement('div');
        card.className = 'card-item';
        card.dataset.index = index;
        
        const authorHtml = fav.authorName ? 
            `<div class="tracking-info"><span>👤</span> Tracking: ${fav.authorName}</div>` : '';

        // Calculate stats or placeholders
        const citations = fav.summary ? fav.summary.citations : "-";
        const hIndex = fav.summary ? fav.summary.hIndex : "-";
        const lastUpdate = fav.summary ? `Updated: ${fav.summary.lastUpdate}` : "Waiting for update...";

        // Calculate paper count if available
        const papersCount = (fav.firstAuthorPapers && fav.firstAuthorPapers.length) || 0;
        const papersBtnText = papersCount > 0 ? `Papers (${papersCount})` : "Papers";

        card.innerHTML = `
            <div class="card-header">
                <div class="author-info">
                    <div class="author-name">${fav.name}</div>
                    ${authorHtml}
                </div>
                <div class="card-actions">
                    <div class="delete-fav" title="Delete">&times;</div>
                </div>
            </div>
            
            <div class="stats-row">
                <div class="stat-item stat-citations">
                    <span class="stat-value">${citations}</span>
                    <span class="stat-label">Citations</span>
                </div>
                <div class="stat-item stat-hindex">
                    <span class="stat-value">${hIndex}</span>
                    <span class="stat-label">h-index</span>
                </div>
                <div class="papers-badge toggle-papers">${papersBtnText}</div>
            </div>

            <div class="card-footer">
                ${lastUpdate}
            </div>

            <div id="papers-list-${index}" class="papers-list-container" style="display: none;"></div>
        `;
        
        favoritesList.appendChild(card);
    });
}

// Global Event Delegation - Robust Interaction Handling
document.getElementById('favoritesList').addEventListener('click', (e) => {
    // 1. Handle Delete
    const deleteBtn = e.target.closest('.delete-fav');
    if (deleteBtn) {
        e.stopPropagation(); // Stop card click
        const card = deleteBtn.closest('.card-item');
        if (card) {
            const index = parseInt(card.dataset.index);
            console.log('[Scholar Scraper] Deleting index:', index);
            removeFavorite(index);
        }
        return;
    }

    // 2. Handle Toggle Papers
    const toggleBtn = e.target.closest('.toggle-papers');
    if (toggleBtn) {
        e.stopPropagation(); // Stop card click
        const card = toggleBtn.closest('.card-item');
        if (card) {
            const index = parseInt(card.dataset.index);
            console.log('[Scholar Scraper] Toggling papers index:', index);
            togglePapersList(index);
        }
        return;
    }

    // 3. Handle Card Click (Navigation)
    const card = e.target.closest('.card-item');
    if (card) {
        // Prevent navigation if clicking inside the papers list container (e.g. selecting text)
        if (e.target.closest('.papers-list-container')) return;

        const index = parseInt(card.dataset.index);
        const fav = favorites[index];
        
        if (fav) {
            console.log('[Scholar Scraper] Navigating to:', fav.url);
            if (fav.authorName) {
                myName = fav.authorName;
                document.getElementById('myNameInput').value = myName;
                chrome.storage.local.set({ myName }, () => updateMeBadge());
            }
            navigateToProfile(fav.url);
        }
    }
});

function togglePapersList(index) {
    const listDiv = document.getElementById(`papers-list-${index}`);
    if (listDiv.style.display === 'none') {
        const fav = favorites[index];
        if (fav.firstAuthorPapers && fav.firstAuthorPapers.length > 0) {
            listDiv.innerHTML = fav.firstAuthorPapers.map(p => `
                <div class="paper-entry">
                    <div class="paper-title">${p.title}</div>
                    <div class="paper-meta">${p.publication} (${p.year}) • Cited by ${p.citations}</div>
                </div>
            `).join('');
            listDiv.style.display = 'block';
        } else {
            listDiv.innerHTML = '<div style="font-size: 0.8rem; color: #999; padding: 8px 0;">No 1st author papers found (or not yet scraped).</div>';
            listDiv.style.display = 'block';
        }
    } else {
        listDiv.style.display = 'none';
    }
}

function cleanScholarUrl(url) {
    try {
        if (!url.includes('scholar.google')) return url;
        const urlObj = new URL(url);
        const params = new URLSearchParams(urlObj.search);
        // Get all 'user' parameters
        const users = params.getAll('user');
        // Find the one that looks like an ID (usually 12 chars, mixed case, alphanumeric)
        // e.g., kGYv10AAAAAJ
        const validUser = users.find(u => /^[a-zA-Z0-9_-]{12,}$/.test(u));
        
        if (validUser) {
            return `https://scholar.google.com/citations?user=${validUser}&hl=en`;
        }
        return url;
    } catch (e) {
        return url;
    }
}

async function navigateToProfile(url) {
    try {
        // Use lastFocusedWindow to ensure we get the browser window, not the sidepanel context
        const tabs = await chrome.tabs.query({ active: true, lastFocusedWindow: true });
        if (tabs.length > 0) {
            await chrome.tabs.update(tabs[0].id, { url: url });
        } else {
            // Fallback if no active tab found (rare)
            await chrome.tabs.create({ url: url });
        }
        console.log('[Scholar Scraper] Navigated to:', url);
    } catch (error) {
        console.error('[Scholar Scraper] Navigation error:', error);
        // Fallback to creating a new tab if update fails
        chrome.tabs.create({ url: url });
    }
}

async function refreshSingleCard(index) {
    const fav = favorites[index];
    // Auto-fix URL if it looks broken
    const cleanUrl = cleanScholarUrl(fav.url);
    if (cleanUrl !== fav.url) {
        console.log(`[Scholar Scraper] Fixing broken URL: ${fav.url} -> ${cleanUrl}`);
        fav.url = cleanUrl;
        favorites[index].url = cleanUrl;
        chrome.storage.local.set({ favorites });
    }

    const refreshBtn = document.getElementById('refreshAllBtn');
    
    // Determine the name to filter by: prefer card-specific name, fallback to global
    const filterName = fav.authorName || myName;

    // Check if name is set
    if (!filterName) {
        console.warn("No author name set for filtering 1st author papers.");
    }

    try {
        const tab = await chrome.tabs.create({ url: fav.url, active: false });
        
        // SPEED OPTIMIZATION: Removed 'await page load complete' listener.
        
        // Wait for content script to be ready
        // We loop pinging the tab until it responds or we timeout
        let contentReady = false;
        for (let i = 0; i < 10; i++) { // Try for 2 seconds (10 * 200ms)
            try {
                await new Promise(r => setTimeout(r, 200));
                // Just check if we can send a message. 'ping' action is not needed, 'load_more_papers' will fail if script not ready.
                // We'll just try load_more directly in the next block.
                contentReady = true; 
                break; 
            } catch(e) {} 
        }

        // Trigger load all papers if we want accurate paper list
        try {
            // Attempt to load more papers (Reduced to 1 click only for speed)
            // Retry a few times if the content script wasn't ready immediately
            for (let i = 0; i < 3; i++) {
                 chrome.tabs.sendMessage(tab.id, { action: "load_more_papers" }, (response) => {
                     // Check if message delivery failed
                     if (chrome.runtime.lastError) {
                         // Script probably not ready yet
                     }
                 }); 
                 await new Promise(r => setTimeout(r, 600)); 
            }
        } catch(e) { /* ignore */ }
        
        let response = null;
        // Aggressive polling for scrape result
        for (let attempt = 1; attempt <= 8; attempt++) {
            try {
                response = await chrome.tabs.sendMessage(tab.id, { action: "scrape_scholar" });
                if (response && response.success && response.summary && response.summary.citations !== "-") {
                    break;
                }
            } catch (err) {}
            // Short interval
            if (attempt < 8) await new Promise(r => setTimeout(r, 400));
        }
        
        if (response && response.success && response.summary) {
            chrome.tabs.remove(tab.id); 
            
            // Process papers to find 1st author ones
            let myPapers = [];
            if (response.data && response.data.length > 0 && filterName) {
                // Generate variations for name matching
                // 1. Full name regex (current)
                const escapedMyName = filterName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
                const fullNameRegex = new RegExp(`\\b${escapedMyName}\\b`, 'i');
                
                // 2. Initial + Last Name regex (e.g. "Ziqi Wang" -> "Z Wang")
                const parts = filterName.trim().split(/\s+/);
                let abbrRegex = null;
                if (parts.length >= 2) {
                    const firstNameInitial = parts[0][0];
                    const lastName = parts[parts.length - 1];
                    // Matches "Z Wang" or "Z. Wang"
                    const escapedLastName = lastName.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
                    abbrRegex = new RegExp(`\\b${firstNameInitial}[.]?\\s*${escapedLastName}\\b`, 'i');
                }

                myPapers = response.data.filter(paper => {
                    if (!paper.authors) return false;
                    const authorsList = paper.authors.split(/[,，]/).map(a => a.trim());
                    if (authorsList.length > 0) {
                        const firstAuthor = authorsList[0];
                        // Check full name match
                        if (fullNameRegex.test(firstAuthor)) return true;
                        // Check abbreviation match
                        if (abbrRegex && abbrRegex.test(firstAuthor)) return true;
                    }
                    return false;
                });
            }

            // Safe Update: Operate on global favorites array to avoid race conditions with delete
            // Find the item by URL because index might have shifted if user deleted something
            const liveIndex = favorites.findIndex(f => f.url === fav.url);
            
            if (liveIndex !== -1) {
                // Update data
                favorites[liveIndex].summary = response.summary;
                favorites[liveIndex].firstAuthorPapers = myPapers;
                
                // Save to storage
                chrome.storage.local.set({ favorites }, () => {
                    console.log("[Scholar Scraper] Storage saved for", fav.name);
                    
                    // Patch DOM if the card still exists
                    const card = document.querySelector(`.card-item[data-index="${liveIndex}"]`);
                    if (card) {
                        // Update papers count badge
                        const papersCount = myPapers.length;
                        const papersBtnText = papersCount > 0 ? `Papers (${papersCount})` : "Papers";
                        const toggleBtn = card.querySelector('.toggle-papers');
                        if (toggleBtn) toggleBtn.innerText = papersBtnText;

                        // Update metrics text
                        // Robustly find elements by class
                        const citEl = card.querySelector('.stat-citations .stat-value');
                        if (citEl) citEl.innerText = response.summary.citations;
                        
                        const hEl = card.querySelector('.stat-hindex .stat-value');
                        if (hEl) hEl.innerText = response.summary.hIndex;
                        
                        // Update timestamp
                        const footerDiv = card.querySelector('.card-footer');
                        if (footerDiv) {
                            footerDiv.innerText = `Updated: ${response.summary.lastUpdate}`;
                        }
                    }
                });
                if (refreshBtn) refreshBtn.innerText = "Updated";
            } else {
                console.warn("[Scholar Scraper] Item was deleted during refresh, skipping save.");
            }
        } else {
             // Failure logic
             console.warn("[Scholar Scraper] Failed. Response:", response);
             chrome.tabs.update(tab.id, { active: true });
             
             let failMsg = "Failed";
             if (response && response.error === "CAPTCHA") {
                  failMsg = "CAPTCHA!";
             } else if (response && response.error === "PROFILE_NOT_FOUND") {
                  failMsg = "404";
             } else if (response && response.error === "NO_STATS_FOUND") {
                  failMsg = "No Stats";
             }
             if (refreshBtn) refreshBtn.innerText = failMsg;
        }
    } catch (e) {
        console.error(e);
        if (refreshBtn) refreshBtn.innerText = "Error";
    }
}

async function refreshAllCards() {
    if (favorites.length === 0) return;
    
    const refreshBtn = document.getElementById('refreshAllBtn');
    const originalText = refreshBtn.innerText;

    for (let i = 0; i < favorites.length; i++) {
        refreshBtn.innerText = `... ${i + 1}/${favorites.length}`;
        
        await refreshSingleCard(i);
        
        if (i < favorites.length - 1) {
             // Speed mode: 300ms - 800ms
             const delay = Math.floor(Math.random() * 500) + 300;
             await new Promise(r => setTimeout(r, delay));
        }
    }
    refreshBtn.innerText = "Done";
    setTimeout(() => refreshBtn.innerText = originalText, 3000);
}

document.getElementById('refreshAllBtn').addEventListener('click', () => {
    refreshAllCards();
});

document.getElementById('saveProfileBtn').addEventListener('click', async () => {
    const urlInput = document.getElementById('profileUrlInput').value.trim();
    const authorNameInput = document.getElementById('myNameInput').value.trim();
    
    if (!urlInput) {
        alert("Please enter a Profile URL or ID.");
        return;
    }

    let targetUrl = urlInput;
    if (!targetUrl.startsWith('http')) {
        targetUrl = 'https://scholar.google.com/citations?user=' + urlInput;
    }

    const name = authorNameInput || "Scholar Profile";
    const cleanUrl = cleanScholarUrl(targetUrl);
    const newFav = { name, url: cleanUrl, authorName: authorNameInput };
    
    favorites.push(newFav);
    chrome.storage.local.set({ favorites }, () => {
        renderFavorites();
        document.getElementById('profileUrlInput').value = "";
        refreshSingleCard(favorites.length - 1); // Auto scrape on add
    });
});

