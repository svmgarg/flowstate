const http = require('http');
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, 'src/main/resources/static');
const mime = {'.html':'text/html','.css':'text/css','.js':'application/javascript','.png':'image/png','.svg':'image/svg+xml','.json':'application/json','.ico':'image/x-icon'};
http.createServer((req, res) => {
  let url = decodeURIComponent(req.url.split('?')[0]);
  if (url === '/') url = '/index.html';
  const file = path.join(root, url);
  fs.readFile(file, (err, data) => {
    if (err) { res.writeHead(404); res.end('Not found'); }
    else { res.writeHead(200, {'Content-Type': mime[path.extname(file)] || 'application/octet-stream'}); res.end(data); }
  });
}).listen(3000, () => console.log('Serving at http://localhost:3000'));
