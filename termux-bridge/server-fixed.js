const express = require('express');
const cors = require('cors');
const { exec } = require('child_process');
const fs = require('fs').promises;
const path = require('path');

const app = express();
const PORT = 8080;
const HOME = process.env.HOME || '/data/data/com.termux/files/home';

// CORS permissif pour l'app Android
app.use(cors({
    origin: '*',
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization']
}));

app.use(express.json());

// Health check
app.get('/health', (req, res) => {
    console.log('[HEALTH] Check reçu');
    res.json({
        status: 'ok',
        timestamp: Date.now(),
        service: 'Termux Bridge',
        listening: '0.0.0.0:8080'
    });
});

// Liste fichiers
app.post('/files/list', async (req, res) => {
    try {
        const { path: dirPath } = req.body;
        const fullPath = path.join(HOME, dirPath || '');
        const files = await fs.readdir(fullPath, { withFileTypes: true });
        
        const fileList = await Promise.all(files.map(async (file) => {
            const filePath = path.join(fullPath, file.name);
            const stats = await fs.stat(filePath);
            return {
                name: file.name,
                isDirectory: file.isDirectory(),
                size: stats.size,
                modified: stats.mtime
            };
        }));
        
        res.json({ success: true, files: fileList });
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});

// Lire fichier
app.post('/files/read', async (req, res) => {
    try {
        const { path: filePath } = req.body;
        const fullPath = path.join(HOME, filePath);
        const content = await fs.readFile(fullPath, 'utf8');
        res.json({ success: true, content });
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});

// Exécuter commande
app.post('/command/execute', (req, res) => {
    const { command } = req.body;
    exec(command, { cwd: HOME }, (error, stdout, stderr) => {
        res.json({
            success: !error,
            stdout,
            stderr,
            error: error ? error.message : null
        });
    });
});

// Démarrer le serveur sur TOUTES les interfaces (0.0.0.0)
app.listen(PORT, '0.0.0.0', () => {
    console.log('╔════════════════════════════════════════════════════╗');
    console.log('║   Termux Bridge Server - Mode Android App         ║');
    console.log('╚════════════════════════════════════════════════════╝');
    console.log('');
    console.log(`🚀 Server running on ALL interfaces`);
    console.log(`   - http://127.0.0.1:${PORT}`);
    console.log(`   - http://localhost:${PORT}`);
    console.log(`   - http://0.0.0.0:${PORT}`);
    console.log('');
    console.log('📅 Started at:', new Date().toLocaleString());
    console.log('');
    console.log('✅ Ready for Android app connections');
    console.log('════════════════════════════════════════════════════');
});
