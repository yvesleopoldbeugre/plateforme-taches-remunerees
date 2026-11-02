const sharp = require('sharp');
const fs = require('fs');

const W = 1750, H = 1210;

function box(x,y,w,h,label,opts={}) {
  const fill = opts.fill || '#eef2ff';
  const stroke = opts.stroke || '#3b4ba0';
  const fs_ = opts.fontSize || 15;
  const rx = opts.rx || 8;
  const bold = opts.bold ? 'font-weight="700"' : '';
  const lines = Array.isArray(label) ? label : [label];
  const lineH = fs_ + 5;
  const startY = y + h/2 - ((lines.length-1)*lineH)/2 + 5;
  return `
  <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}" stroke="${stroke}" stroke-width="${opts.strokeWidth||1.6}" ${opts.dashed?'stroke-dasharray="6,4"':''}/>
  ${lines.map((l,i)=>`<text x="${x+w/2}" y="${startY+i*lineH}" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-size="${fs_}" ${bold} fill="${opts.textColor||'#1f2937'}">${l}</text>`).join('')}
  `;
}

function arrow(x1,y1,x2,y2,opts={}){
  const dash = opts.dashed ? ' stroke-dasharray="7,5"' : '';
  const color = opts.color || '#374151';
  return `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="2" ${dash} marker-end="url(#arrow)"/>`;
}

function label(x,y,text,opts={}){
  return `<text x="${x}" y="${y}" text-anchor="${opts.anchor||'middle'}" font-family="Helvetica, Arial, sans-serif" font-size="${opts.fontSize||13}" font-style="${opts.italic?'italic':'normal'}" fill="${opts.color||'#4b5563'}">${text}</text>`;
}

const modules = [
  'Auth','Users','Organizations','Campaigns','Tasks',
  'Participations','Submissions','Validation','Wallet','Payments',
  'Withdrawals','Disputes','Notifications','Fraud','Audit'
];

let modBoxes = '';
const modCols = 5, modRows = 3;
const modW = 275, modH = 56, modGapX = 18, modGapY = 14;
const modStartX = 150, modStartY = 430;
modules.forEach((m,i)=>{
  const col = i % modCols, row = Math.floor(i/modCols);
  const x = modStartX + col*(modW+modGapX);
  const y = modStartY + row*(modH+modGapY);
  const highlight = m==='Wallet';
  modBoxes += box(x,y,modW,modH,m,{fill: highlight?'#fde68a':'#e0e7ff', stroke: highlight?'#b45309':'#4338ca', fontSize:15.5});
});

const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <rect width="${W}" height="${H}" fill="#ffffff"/>
  <defs>
    <marker id="arrow" markerWidth="11" markerHeight="11" refX="9" refY="3.5" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,7 L10,3.5 z" fill="#374151"/>
    </marker>
  </defs>
  <text x="${W/2}" y="40" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-size="24" font-weight="700" fill="#111827">Architecture technique — Monolithe modulaire</text>

  <!-- Layer 1: Frontend -->
  ${label(W/2,78,'Couche présentation — Frontend Web (SPA, responsive)', {fontSize:15, color:'#111827'})}
  ${box(150,95,480,70,['Application','Utilisateur (travailleur)'],{fill:'#f0fdf4',stroke:'#15803d'})}
  ${box(650,95,480,70,['Application','Organisation'],{fill:'#f0fdf4',stroke:'#15803d'})}
  ${box(1150,95,450,70,['Back-office','Plateforme'],{fill:'#f0fdf4',stroke:'#15803d'})}

  ${arrow(390,165,390,230)}
  ${arrow(890,165,890,230)}
  ${arrow(1375,165,1375,230)}
  ${label(W/2,210,'HTTPS — REST JSON (API versionnée /api/v1, JWT Bearer)', {fontSize:13.5, italic:true})}

  <!-- Layer 2: Backend monolith boundary -->
  <rect x="100" y="235" width="1550" height="460" rx="12" fill="#fafafa" stroke="#6b7280" stroke-width="2"/>
  ${label(875,265,'Backend — Spring Boot (monolithe modulaire déployé comme un seul artefact)', {fontSize:16.5, color:'#111827'})}
  ${box(150,285,1500,110,['Spring Security (authn / RBAC) · Bean Validation · Rate limiting','Event bus interne (Spring ApplicationEvent) — diffuse les événements de domaine','vers les modules transverses : Notifications · Fraud · Audit'],{fill:'#ede9fe',stroke:'#6d28d9', fontSize:14.5})}

  ${label(875,410,'Modules métier (package-by-feature, dépendances unidirectionnelles)', {fontSize:14, italic:true})}
  ${modBoxes}

  <!-- Layer 3: Data -->
  ${arrow(390,695,390,740)}
  ${arrow(875,695,875,740)}
  ${arrow(1375,695,1375,740)}
  ${label(W/2,730,"Accès données (JDBC/JPA transactionnel) · cache · objets", {fontSize:13, italic:true})}

  ${box(170,745,440,90,['PostgreSQL','(source de vérité + ledger financier)'],{fill:'#fff7ed',stroke:'#c2410c'})}
  ${box(660,745,420,90,['Redis','(cache lecture, rate limit, sessions)'],{fill:'#fff7ed',stroke:'#c2410c'})}
  ${box(1130,745,470,90,['Stockage objet S3-compatible','(preuves : photos, documents)'],{fill:'#fff7ed',stroke:'#c2410c'})}

  <!-- Layer 4: Async (future) -->
  ${arrow(390,835,390,880,{dashed:true,color:'#9ca3af'})}
  ${label(600,865,"introduit en Phase 8 (paiements / retraits)", {fontSize:12.5, italic:true, color:'#6b7280'})}
  ${box(170,885,440,80,['RabbitMQ','(notifications, webhooks provider de paiement)'],{fill:'#f3f4f6',stroke:'#9ca3af',dashed:true, textColor:'#4b5563'})}

  <!-- Legend -->
  ${box(1130,885,150,34,'Module noyau',{fill:'#fde68a',stroke:'#b45309', fontSize:13})}
  ${label(1130+150+16,907,'Wallet : aucune dépendance vers les autres modules métier', {fontSize:13, anchor:'start'})}
  ${box(1130,930,150,34,'Hors MVP jour 1',{fill:'#f3f4f6',stroke:'#9ca3af', fontSize:13})}
  ${label(1130+150+16,952,"Introduit seulement quand le besoin asynchrone apparaît", {fontSize:13, anchor:'start'})}

  ${label(W/2,1010,"Choix d'architecture : monolithe modulaire plutôt que microservices — cf. §7.1 du dossier de conception.", {fontSize:13.5, italic:true, color:'#374151'})}
  ${label(W/2,1034,"Modules isolés en packages à interfaces explicites : extraction en service indépendant possible plus tard sans réécriture du domaine.", {fontSize:13.5, italic:true, color:'#374151'})}
</svg>`;

fs.writeFileSync('architecture.svg', svg);
sharp(Buffer.from(svg)).png({ density: 220 }).resize(2300).toFile('../diagrams/architecture-technique.png')
  .then(info => console.log('OK', info))
  .catch(err => { console.error(err); process.exit(1); });
