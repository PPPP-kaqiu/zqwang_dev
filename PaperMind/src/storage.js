// Storage management for PaperMind

// IndexedDB Configuration
const DB_NAME = 'PaperMindDB';
const DB_VERSION = 1;
const STORE_FILES = 'files';

const Storage = {
    // --- Chrome Local Storage (Settings & Notes) ---

    async getSettings() {
        const result = await chrome.storage.local.get('settings');
        return result.settings || {};
    },

    async saveSettings(settings) {
        await chrome.storage.local.set({ settings });
    },

    async getNotes(fileId) {
        const key = `notes_${fileId}`;
        const result = await chrome.storage.local.get(key);
        return result[key] || { records: [] };
    },

    async addNote(fileId, note) {
        const data = await this.getNotes(fileId);
        if (!note.id) {
            note.id = this.generateId();
        }
        if (!note.timestamp) {
            note.timestamp = Date.now();
        }
        data.records.push(note);
        const key = `notes_${fileId}`;
        await chrome.storage.local.set({ [key]: data });
        return data;
    },

    async deleteNote(fileId, noteId) {
        const data = await this.getNotes(fileId);
        data.records = data.records.filter(note => 
            note.id !== noteId && note.relatedNoteId !== noteId
        );
        const key = `notes_${fileId}`;
        await chrome.storage.local.set({ [key]: data });
    },
    
    // --- IndexedDB (PDF Files) ---

    async openDB() {
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(DB_NAME, DB_VERSION);

            request.onerror = (event) => reject("IndexedDB error: " + event.target.error);

            request.onupgradeneeded = (event) => {
                const db = event.target.result;
                if (!db.objectStoreNames.contains(STORE_FILES)) {
                    db.createObjectStore(STORE_FILES, { keyPath: 'id' });
                }
            };

            request.onsuccess = (event) => resolve(event.target.result);
        });
    },

    async saveFile(fileId, arrayBuffer) {
        const db = await this.openDB();
        return new Promise((resolve, reject) => {
            const transaction = db.transaction([STORE_FILES], 'readwrite');
            const store = transaction.objectStore(STORE_FILES);
            const request = store.put({ id: fileId, data: arrayBuffer, timestamp: Date.now() });

            request.onsuccess = () => resolve();
            request.onerror = (e) => reject(e.target.error);
        });
    },

    async getFile(fileId) {
        const db = await this.openDB();
        return new Promise((resolve, reject) => {
            const transaction = db.transaction([STORE_FILES], 'readonly');
            const store = transaction.objectStore(STORE_FILES);
            const request = store.get(fileId);

            request.onsuccess = () => resolve(request.result ? request.result.data : null);
            request.onerror = (e) => reject(e.target.error);
        });
    },

    async setLastOpenedFile(fileId) {
        await chrome.storage.local.set({ 'last_opened_file_id': fileId });
    },

    async getLastOpenedFileId() {
        const result = await chrome.storage.local.get('last_opened_file_id');
        return result.last_opened_file_id || null;
    },

    // Helper to generate UUID
    generateId() {
        return Date.now().toString(36) + Math.random().toString(36).substr(2);
    }
};
