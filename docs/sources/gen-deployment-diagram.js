const sharp = require('sharp');
const fs = require('fs');

const W = 1750, H = 1380;

function box(x,y,w,h,label,opts={}) {
  const fill = opts.fill || '#eef2ff';
  const stroke = opts.stroke || '#3b4ba0';
  const fs_ = opts.fontSize || 15;
  const rx = opts.rx || 8;
  const lines = Array.isArray(label) ? label : [label];
  const lineH = fs_ + 5;
  const startY = y + h/2 - ((lines.length-1)*lineH)/2 + 5;
  return `
  <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}" stroke="${stroke}" stroke-width="${opts.strokeWidth||1.6}" ${opts.dashed?'stroke-dasharray="6,4"':''}/>
  ${lines.map((l,i)=>`<text x="${x+w/2}" y="${startY+i*lineH}" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-size="${fs_}" fill="${opts.textColor||'#1f2937'}">${l}</text>`).join('')}
  `;
}
function arrow(x1,y1,x2,y2,opts={}){
  const dash = opts.dashed ? ' stroke-dasharray="7,5"' : '';
  const color = opts.color || '#374151';
  return `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="2" ${dash} marker-end="url(#arrow)"/>`;
}
function elbow(points, opts={}){
  const dash = opts.dashed ? ' stroke-dasharray="7,5"' : '';
  const color = opts.color || '#374151';
  const d = points.map((p,i)=> (i===0?'M ':'L ') + p[0] + ',' + p[1]).join(' ');
  return `<path d="${d}" fill="none" stroke="${color}" stroke-width="2" ${dash} marker-end="url(#arrow)"/>`;
}
function label(x,y,text,opts={}){
  return `<text x="${x}" y="${y}" text-anchor="${opts.anchor||'middle'}" font-family="Helvetica, Arial, sans-serif" font-size="${opts.fontSize||13}" font-style="${opts.italic?'italic':'normal'}" fill="${opts.color||'#4b5563'}">${text}</text>`;
}

const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <rect width="${W}" height="${H}" fill="#ffffff"/>
  <defs>
    <marker id="arrow" markerWidth="11" markerHeight="11" refX="9" refY="3.5" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,7 L10,3.5 z" fill="#374151"/>
    </marker>
  </defs>
  <text x="${W/2}" y="40" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-size="24" font-weight="700" fill="#111827">Diagramme de déploiement — Infrastructure cible</text>

  ${box(700,70,350,56,'Utilisateurs / Organisations (Internet)',{fill:'#ffffff',stroke:'#111827'})}
  ${arrow(875,126,875,166)}

  ${box(625,170,500,56,'CDN + WAF (protection DDoS, TLS)',{fill:'#ecfeff',stroke:'#0e7490'})}
  ${arrow(875,226,875,266)}

  ${box(575,270,600,56,"Load Balancer (HTTPS, certificats TLS, health checks)",{fill:'#ecfeff',stroke:'#0e7490'})}
  ${arrow(730,326,730,366)}
  ${arrow(1020,326,1020,366)}

  <!-- VPC boundary -->
  <rect x="150" y="370" width="1450" height="560" rx="12" fill="#fafafa" stroke="#6b7280" stroke-width="2" stroke-dasharray="3,3"/>
  ${label(875,398,'Réseau privé (VPC) — sous-réseau applicatif', {fontSize:14.5, color:'#111827'})}

  ${box(560,415,340,90,['Instance App #1','Spring Boot (monolithe)'],{fill:'#e0e7ff',stroke:'#4338ca'})}
  ${box(1000,415,340,90,['Instance App #2','Spring Boot (monolithe)'],{fill:'#e0e7ff',stroke:'#4338ca'})}

  ${arrow(650,505,650,560)}
  ${arrow(820,505,820,560)}
  ${arrow(1080,505,1080,560)}
  ${arrow(1250,505,1250,560)}

  <!-- sous-réseau données -->
  <rect x="190" y="565" width="1370" height="300" rx="10" fill="#ffffff" stroke="#9ca3af" stroke-width="1.6" stroke-dasharray="3,3"/>
  ${label(875,588,'Sous-réseau données (accès restreint depuis le sous-réseau applicatif uniquement)', {fontSize:13, italic:true, color:'#4b5563'})}

  ${box(230,605,370,90,['PostgreSQL — Primaire','(lecture / écriture)'],{fill:'#fff7ed',stroke:'#c2410c'})}
  ${box(650,605,370,90,['PostgreSQL — Réplique','(lecture seule, bascule)'],{fill:'#fff7ed',stroke:'#c2410c', dashed:true})}
  ${arrow(600,650,648,650,{color:'#c2410c'})}
  ${label(624,636,'réplication', {fontSize:12, italic:true, color:'#c2410c'})}
  ${box(1070,605,200,90,'Redis',{fill:'#fff7ed',stroke:'#c2410c'})}
  ${box(1300,605,230,90,['Stockage objet','S3-compatible'],{fill:'#fff7ed',stroke:'#c2410c'})}

  ${box(230,730,370,90,['Sauvegardes automatisées','quotidiennes · rétention 30 j'],{fill:'#f3f4f6',stroke:'#9ca3af'})}
  ${arrow(415,695,415,728,{dashed:true,color:'#9ca3af'})}

  <!-- side: secrets + monitoring, routed through the clear gap between the two instances (x 900-1155) -->
  ${box(190,935,400,90,['Secrets Manager / Vault','(clés API providers, identifiants DB)'],{fill:'#fef2f2',stroke:'#b91c1c'})}
  ${elbow([[390,935],[210,935],[210,530],[900,530],[900,505]],{dashed:true,color:'#b91c1c'})}
  ${label(230,920,'configuration sécurisée', {fontSize:12, italic:true, color:'#b91c1c', anchor:'start'})}

  ${box(960,935,640,90,['Supervision','Logs structurés (correlationId) · Métriques (Prometheus/Grafana) · Health checks /actuator/health'],{fill:'#eff6ff',stroke:'#1d4ed8'})}
  ${elbow([[1150,935],[1545,935],[1545,530],[1000,530],[1000,505]],{dashed:true,color:'#1d4ed8'})}
  ${label(1390,920,'health checks — toutes instances', {fontSize:12, italic:true, color:'#1d4ed8', anchor:'start'})}

  ${label(W/2,1060,"Environnements distincts (dev / staging / production) répliquant cette même topologie, isolés réseau et données.", {fontSize:13.5, italic:true, color:'#374151'})}
  ${label(W/2,1084,"MVP : une seule région ; réplique PostgreSQL et stockage objet suffisent pour la continuité de service à ce stade (pas de multi-région).", {fontSize:13.5, italic:true, color:'#374151'})}
  ${label(W/2,1108,"Scaling horizontal : instances applicatives sans état, ajoutées derrière le load balancer selon la charge.", {fontSize:13.5, italic:true, color:'#374151'})}
</svg>`;

fs.writeFileSync('deployment.svg', svg);
sharp(Buffer.from(svg)).png({ density: 220 }).resize(2300).toFile('../diagrams/deploiement-infrastructure.png')
  .then(info => console.log('OK', info))
  .catch(err => { console.error(err); process.exit(1); });
