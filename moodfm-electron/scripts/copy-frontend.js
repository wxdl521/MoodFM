const path = require('path');
const fs = require('fs');

const src = path.resolve(__dirname, '../../moodfm-frontend/dist');
const dest = path.resolve(__dirname, '../app');

if (!fs.existsSync(src)) {
  console.error('Error: Frontend dist directory not found at', src);
  console.error('Run "npm run build:frontend" first.');
  process.exit(1);
}

if (fs.existsSync(dest)) {
  fs.rmSync(dest, { recursive: true, force: true });
}

fs.cpSync(src, dest, { recursive: true });
console.log('Frontend files copied to', dest);
