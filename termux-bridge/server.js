#!/usr/bin/env node

const express = require('express');
const cors = require('cors');
const bodyParser = require('body-parser');
const fs = require('fs').promises;
const fsSync = require('fs');
const path = require('path');
const { exec } = require('child_process');
const { promisify } = require('util');

const execAsync = promisify(exec);

const app = express();
const PORT = 8080;
const HOST = '127.0.0.1';
const HOME_DIR = process.env.HOME || '/data/data/com.termux/files/home';

// Middleware
app.use(cors());
app.use(bodyParser.json({ limit: '10mb' }));
app.use(bodyParser.urlencoded({ extended: true, limit: '10mb' }));

// Logging middleware
app.use((req, res, next) => {
  console.log(`[${new Date().toISOString()}] ${req.method} ${req.path}`);
  next();
});

// Health check
app.get('/health', (req, res) => {
  res.json({ 
    status: 'ok', 
    timestamp: Date.now(),
    service: 'Termux Bridge'
  });
});

// List files in directory
app.post('/files/list', async (req, res) => {
  try {
    const { path: dirPath } = req.body;
    const targetPath = dirPath || HOME_DIR;

    const entries = await fs.readdir(targetPath, { withFileTypes: true });
    
    const files = await Promise.all(
      entries.map(async (entry) => {
        const fullPath = path.join(targetPath, entry.name);
        let stats = null;
        
        try {
          stats = await fs.stat(fullPath);
        } catch (err) {
          // Skip files we can't stat
          return null;
        }

        return {
          name: entry.name,
          path: fullPath,
          isDirectory: entry.isDirectory(),
          size: stats ? stats.size : 0,
          lastModified: stats ? stats.mtimeMs : 0,
          permissions: stats ? stats.mode.toString(8) : ''
        };
      })
    );

    res.json(files.filter(f => f !== null));
  } catch (error) {
    console.error('Error listing files:', error);
    res.status(500).json({ error: error.message });
  }
});

// Read file content
app.post('/files/read', async (req, res) => {
  try {
    const { path: filePath } = req.body;
    
    if (!filePath) {
      return res.status(400).json({ error: 'Path required' });
    }

    const content = await fs.readFile(filePath, 'utf-8');
    res.json({ content });
  } catch (error) {
    console.error('Error reading file:', error);
    res.status(500).json({ error: error.message });
  }
});

// Write file content
app.post('/files/write', async (req, res) => {
  try {
    const { path: filePath, content } = req.body;
    
    if (!filePath || content === undefined) {
      return res.status(400).json({ error: 'Path and content required' });
    }

    await fs.writeFile(filePath, content, 'utf-8');
    res.json({ success: true });
  } catch (error) {
    console.error('Error writing file:', error);
    res.status(500).json({ error: error.message });
  }
});

// Delete file or directory
app.post('/files/delete', async (req, res) => {
  try {
    const { path: filePath } = req.body;
    
    if (!filePath) {
      return res.status(400).json({ error: 'Path required' });
    }

    const stats = await fs.stat(filePath);
    
    if (stats.isDirectory()) {
      await fs.rm(filePath, { recursive: true, force: true });
    } else {
      await fs.unlink(filePath);
    }

    res.json({ success: true });
  } catch (error) {
    console.error('Error deleting file:', error);
    res.status(500).json({ error: error.message });
  }
});

// Create directory
app.post('/files/mkdir', async (req, res) => {
  try {
    const { path: dirPath } = req.body;
    
    if (!dirPath) {
      return res.status(400).json({ error: 'Path required' });
    }

    await fs.mkdir(dirPath, { recursive: true });
    res.json({ success: true });
  } catch (error) {
    console.error('Error creating directory:', error);
    res.status(500).json({ error: error.message });
  }
});

// Rename/move file
app.post('/files/rename', async (req, res) => {
  try {
    const { oldPath, newPath } = req.body;
    
    if (!oldPath || !newPath) {
      return res.status(400).json({ error: 'Both oldPath and newPath required' });
    }

    await fs.rename(oldPath, newPath);
    res.json({ success: true });
  } catch (error) {
    console.error('Error renaming file:', error);
    res.status(500).json({ error: error.message });
  }
});

// Copy file
app.post('/files/copy', async (req, res) => {
  try {
    const { source, destination } = req.body;
    
    if (!source || !destination) {
      return res.status(400).json({ error: 'Source and destination required' });
    }

    await fs.copyFile(source, destination);
    res.json({ success: true });
  } catch (error) {
    console.error('Error copying file:', error);
    res.status(500).json({ error: error.message });
  }
});

// List projects (detect Gradle projects)
app.get('/projects/list', async (req, res) => {
  try {
    const entries = await fs.readdir(HOME_DIR, { withFileTypes: true });
    const projects = [];

    for (const entry of entries) {
      if (entry.isDirectory()) {
        const projectPath = path.join(HOME_DIR, entry.name);
        
        // Check if it's an Android project
        const hasGradle = fsSync.existsSync(path.join(projectPath, 'build.gradle')) || 
                          fsSync.existsSync(path.join(projectPath, 'build.gradle.kts'));
        const hasAndroidManifest = fsSync.existsSync(path.join(projectPath, 'app', 'src', 'main', 'AndroidManifest.xml'));

        if (hasGradle || hasAndroidManifest || entry.name.match(/^[A-Z]/)) {
          projects.push({
            name: entry.name,
            path: projectPath,
            isAndroidProject: hasAndroidManifest,
            hasGradle: hasGradle
          });
        }
      }
    }

    res.json(projects);
  } catch (error) {
    console.error('Error listing projects:', error);
    res.status(500).json({ error: error.message });
  }
});

// Execute command (for build, etc)
app.post('/command/execute', async (req, res) => {
  try {
    const { command, cwd } = req.body;
    
    if (!command) {
      return res.status(400).json({ error: 'Command required' });
    }

    const options = {
      cwd: cwd || HOME_DIR,
      maxBuffer: 10 * 1024 * 1024, // 10MB
      timeout: 300000 // 5 minutes
    };

    const { stdout, stderr } = await execAsync(command, options);
    
    res.json({
      stdout,
      stderr,
      exitCode: 0
    });
  } catch (error) {
    console.error('Error executing command:', error);
    res.json({
      stdout: error.stdout || '',
      stderr: error.stderr || error.message,
      exitCode: error.code || 1
    });
  }
});

// Start server
app.listen(PORT, HOST, () => {
  console.log('╔════════════════════════════════════════════╗');
  console.log('║   Termux Bridge Server                     ║');
  console.log('╚════════════════════════════════════════════╝');
  console.log('');
  console.log(`🚀 Server running on http://${HOST}:${PORT}`);
  console.log(`🏠 Home directory: ${HOME_DIR}`);
  console.log(`📅 Started at: ${new Date().toLocaleString()}`);
  console.log('');
  console.log('Available endpoints:');
  console.log('  GET  /health');
  console.log('  POST /files/list');
  console.log('  POST /files/read');
  console.log('  POST /files/write');
  console.log('  POST /files/delete');
  console.log('  POST /files/mkdir');
  console.log('  POST /files/rename');
  console.log('  POST /files/copy');
  console.log('  GET  /projects/list');
  console.log('  POST /command/execute');
  console.log('');
  console.log('Press Ctrl+C to stop');
  console.log('════════════════════════════════════════════');
});

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\n\n👋 Shutting down Termux Bridge Server...');
  process.exit(0);
});
