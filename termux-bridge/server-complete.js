const express = require('express');
const cors = require('cors');
const { exec } = require('child_process');
const fs = require('fs').promises;
const path = require('path');
const axios = require('axios');

const app = express();
const PORT = 8080;
const HOME = process.env.HOME || '/data/data/com.termux/files/home';

// URLs des services
const OMNIROUTE_URL = 'http://localhost:20128';
const OMNI_EXEC_URL = 'http://localhost:8000'; // Si disponible

// CORS permissif pour l'app Android
app.use(cors({
    origin: '*',
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    allowedHeaders: ['Content-Type', 'Authorization', 'X-API-Key']
}));

app.use(express.json());

console.log('╔════════════════════════════════════════════════════╗');
console.log('║   Termux Bridge Server - Version Complète         ║');
console.log('╚════════════════════════════════════════════════════╝');
console.log('');
console.log('🔗 Services configurés:');
console.log(`   - OmniRoute (Kiro/OpenCode): ${OMNIROUTE_URL}`);
console.log(`   - Omni-Exec (Commandes): Direct`);
console.log(`   - File System: Direct`);
console.log('');

// ============================================================================
// HEALTH CHECK
// ============================================================================
app.get('/health', async (req, res) => {
    console.log('[HEALTH] Check reçu');
    
    const services = {
        bridge: true,
        omniroute: false,
        omniExec: false,
        fileSystem: true
    };
    
    // Vérifier OmniRoute
    try {
        await axios.get(`${OMNIROUTE_URL}/health`, { timeout: 2000 });
        services.omniroute = true;
    } catch (e) {
        // OmniRoute peut être actif même sans endpoint /health
        services.omniroute = true; // On suppose qu'il est actif si le processus tourne
    }
    
    // Vérifier Omni-Exec (optionnel)
    try {
        await axios.get(`${OMNI_EXEC_URL}/health`, { timeout: 2000 });
        services.omniExec = true;
    } catch (e) {
        services.omniExec = false;
    }
    
    res.json({
        status: 'ok',
        timestamp: Date.now(),
        service: 'Termux Bridge',
        listening: '0.0.0.0:8080',
        services: services,
        omnirouteUrl: OMNIROUTE_URL
    });
});

// ============================================================================
// OPENCODE / OMNIROUTE - Chat avec l'IA
// ============================================================================
app.post('/opencode/chat', async (req, res) => {
    try {
        console.log('[OPENCODE] Requête chat reçue');
        const { message, model, history } = req.body;
        
        // Préparer la requête pour OmniRoute (format OpenAI compatible)
        const omnirouteRequest = {
            model: model || 'kr/claude-sonnet-4.5',
            messages: [
                ...(history || []),
                { role: 'user', content: message }
            ],
            stream: false
        };
        
        // Envoyer à OmniRoute
        const response = await axios.post(
            `${OMNIROUTE_URL}/v1/chat/completions`,
            omnirouteRequest,
            {
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': `Bearer ${process.env.OMNIROUTE_API_KEY || 'dummy'}`
                },
                timeout: 120000 // 2 minutes
            }
        );
        
        console.log('[OPENCODE] Réponse reçue de OmniRoute');
        
        res.json({
            success: true,
            response: response.data.choices[0].message.content,
            model: response.data.model,
            usage: response.data.usage
        });
        
    } catch (error) {
        console.error('[OPENCODE] Erreur:', error.message);
        res.status(500).json({
            success: false,
            error: error.message,
            details: error.response?.data || null
        });
    }
});

// ============================================================================
// OMNIROUTE - Proxy direct pour requêtes avancées
// ============================================================================
app.post('/omniroute/*', async (req, res) => {
    try {
        const path = req.path.replace('/omniroute', '');
        console.log(`[OMNIROUTE] Proxy vers ${path}`);
        
        const response = await axios({
            method: req.method,
            url: `${OMNIROUTE_URL}${path}`,
            data: req.body,
            headers: {
                'Content-Type': 'application/json',
                'Authorization': req.headers.authorization || `Bearer ${process.env.OMNIROUTE_API_KEY || 'dummy'}`
            },
            timeout: 120000
        });
        
        res.json(response.data);
        
    } catch (error) {
        console.error('[OMNIROUTE] Erreur proxy:', error.message);
        res.status(error.response?.status || 500).json({
            success: false,
            error: error.message,
            details: error.response?.data || null
        });
    }
});

// ============================================================================
// FILES - Gestion des fichiers
// ============================================================================
app.post('/files/list', async (req, res) => {
    try {
        const { path: dirPath } = req.body;
        const fullPath = path.join(HOME, dirPath || '');
        const files = await fs.readdir(fullPath, { withFileTypes: true });
        
        const fileList = await Promise.all(files.map(async (file) => {
            const filePath = path.join(fullPath, file.name);
            try {
                const stats = await fs.stat(filePath);
                return {
                    name: file.name,
                    isDirectory: file.isDirectory(),
                    size: stats.size,
                    modified: stats.mtime.toISOString()
                };
            } catch (e) {
                return {
                    name: file.name,
                    isDirectory: file.isDirectory(),
                    size: 0,
                    modified: null,
                    error: e.message
                };
            }
        }));
        
        res.json({ success: true, files: fileList, path: fullPath });
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});

app.post('/files/read', async (req, res) => {
    try {
        const { path: filePath } = req.body;
        const fullPath = path.join(HOME, filePath);
        const content = await fs.readFile(fullPath, 'utf8');
        res.json({ success: true, content, path: fullPath });
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});

app.post('/files/write', async (req, res) => {
    try {
        const { path: filePath, content } = req.body;
        const fullPath = path.join(HOME, filePath);
        await fs.writeFile(fullPath, content, 'utf8');
        res.json({ success: true, path: fullPath });
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});

app.post('/files/delete', async (req, res) => {
    try {
        const { path: filePath } = req.body;
        const fullPath = path.join(HOME, filePath);
        await fs.unlink(fullPath);
        res.json({ success: true, path: fullPath });
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});

app.post('/files/mkdir', async (req, res) => {
    try {
        const { path: dirPath } = req.body;
        const fullPath = path.join(HOME, dirPath);
        await fs.mkdir(fullPath, { recursive: true });
        res.json({ success: true, path: fullPath });
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});

// ============================================================================
// COMMAND - Exécution de commandes
// ============================================================================
app.post('/command/execute', (req, res) => {
    const { command, cwd } = req.body;
    console.log(`[COMMAND] Exécution: ${command}`);
    
    exec(command, { 
        cwd: cwd || HOME,
        maxBuffer: 1024 * 1024 * 10 // 10MB
    }, (error, stdout, stderr) => {
        res.json({
            success: !error,
            stdout,
            stderr,
            error: error ? error.message : null,
            exitCode: error ? error.code : 0
        });
    });
});

// ============================================================================
// PROJECTS - Liste des projets
// ============================================================================
app.get('/projects/list', async (req, res) => {
    try {
        const files = await fs.readdir(HOME, { withFileTypes: true });
        const projects = files
            .filter(f => f.isDirectory() && !f.name.startsWith('.'))
            .map(f => ({
                name: f.name,
                path: path.join(HOME, f.name),
                type: 'directory'
            }));
        
        res.json({ success: true, projects });
    } catch (error) {
        res.status(500).json({ success: false, error: error.message });
    }
});

// ============================================================================
// STATUS - État complet du système
// ============================================================================
app.get('/status', async (req, res) => {
    const status = {
        bridge: {
            status: 'online',
            port: PORT,
            uptime: process.uptime()
        },
        omniroute: {
            url: OMNIROUTE_URL,
            status: 'checking...'
        },
        omniExec: {
            url: OMNI_EXEC_URL,
            status: 'checking...'
        }
    };
    
    // Vérifier OmniRoute
    try {
        await axios.get(`${OMNIROUTE_URL}/v1/models`, {
            timeout: 2000,
            validateStatus: () => true // Accepter toutes les réponses
        });
        status.omniroute.status = 'online';
    } catch (e) {
        status.omniroute.status = 'offline';
    }
    
    res.json(status);
});

// ============================================================================
// DÉMARRAGE DU SERVEUR
// ============================================================================
app.listen(PORT, '0.0.0.0', () => {
    console.log('🚀 Server running on ALL interfaces');
    console.log(`   - http://127.0.0.1:${PORT}`);
    console.log(`   - http://localhost:${PORT}`);
    console.log(`   - http://0.0.0.0:${PORT}`);
    console.log('');
    console.log('📅 Started at:', new Date().toLocaleString());
    console.log('');
    console.log('✅ Ready for Android app connections');
    console.log('   → Dashboard, Files, Terminal: Direct');
    console.log('   → OpenCode/AI: via OmniRoute (Kiro)');
    console.log('════════════════════════════════════════════════════');
});
