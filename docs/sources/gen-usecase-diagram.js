const sharp = require('sharp');
const fs = require('fs');

const W = 1750, H = 1300;

function actor(cx, cy, label) {
  const headR = 18;
  const neckY = cy - 40 + headR;
  return `
  <g font-family="Helvetica, Arial, sans-serif">
    <circle cx="${cx}" cy="${cy-58}" r="${headR}" fill="#ffffff" stroke="#1f2937" stroke-width="2.5"/>
    <line x1="${cx}" y1="${cy-40}" x2="${cx}" y2="${cy+10}" stroke="#1f2937" stroke-width="2.5"/>
    <line x1="${cx-26}" y1="${cy-20}" x2="${cx+26}" y2="${cy-20}" stroke="#1f2937" stroke-width="2.5"/>
    <line x1="${cx}" y1="${cy+10}" x2="${cx-24}" y2="${cy+55}" stroke="#1f2937" stroke-width="2.5"/>
    <line x1="${cx}" y1="${cy+10}" x2="${cx+24}" y2="${cy+55}" stroke="#1f2937" stroke-width="2.5"/>
    ${label.map((l,i)=>`<text x="${cx}" y="${cy+80+i*20}" text-anchor="middle" font-size="16" font-weight="600" fill="#111827">${l}</text>`).join('')}
  </g>`;
}

function ellipseUC(cx, cy, lines, opts={}) {
  const rx = opts.rx || 140, ry = opts.ry || 46;
  const fill = opts.fill || '#eef2ff';
  const stroke = opts.stroke || '#3b4ba0';
  const fs_ = opts.fontSize || 15.5;
  const lineH = fs_ + 4;
  const startY = cy - ((lines.length-1)*lineH)/2 + 5;
  return `
  <g font-family="Helvetica, Arial, sans-serif">
    <ellipse cx="${cx}" cy="${cy}" rx="${rx}" ry="${ry}" fill="${fill}" stroke="${stroke}" stroke-width="2"/>
    ${lines.map((l,i)=>`<text x="${cx}" y="${startY + i*lineH}" text-anchor="middle" font-size="${fs_}" fill="#1f2937">${l}</text>`).join('')}
  </g>`;
}

function link(x1,y1,x2,y2, opts={}) {
  const dash = opts.dashed ? ' stroke-dasharray="7,5"' : '';
  const marker = opts.arrow ? ' marker-end="url(#arrow)"' : '';
  const color = opts.color || '#374151';
  return `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="1.8"${dash}${marker}/>`;
}

function curvedLink(x1,y1,x2,y2,bulgeX,opts={}) {
  const dash = opts.dashed ? ' stroke-dasharray="7,5"' : '';
  const marker = opts.arrow ? ' marker-end="url(#arrow)"' : '';
  const color = opts.color || '#374151';
  const cx = bulgeX, cy = (y1+y2)/2;
  return `<path d="M ${x1},${y1} Q ${cx},${cy} ${x2},${y2}" fill="none" stroke="${color}" stroke-width="1.8"${dash}${marker}/>`;
}

function stereo(x,y,text,color) {
  return `<text x="${x}" y="${y}" text-anchor="middle" font-size="13" font-style="italic" font-family="Helvetica, Arial, sans-serif" fill="${color||'#6b7280'}">${text}</text>`;
}

// Columns
const col1 = 500, col2 = 955, col3 = 1410;
const rowY = { r1:170, r2:310, r3:450, r4:590, r5:730, r6:870, r7:1010, r8:1150 };

const svg = `
<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
  <rect width="${W}" height="${H}" fill="#ffffff"/>
  <defs>
    <marker id="arrow" markerWidth="10" markerHeight="10" refX="8" refY="3" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,6 L9,3 z" fill="#374151"/>
    </marker>
  </defs>

  <!-- System boundary -->
  <rect x="280" y="70" width="1280" height="1170" rx="10" fill="#fafafa" stroke="#9ca3af" stroke-width="2"/>
  <text x="920" y="105" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-size="23" font-weight="700" fill="#111827">Plateforme de Tâches &amp; Campagnes Rémunérées</text>

  <!-- Actors -->
  ${actor(110, 350, ['Utilisateur','(Travailleur)'])}
  ${actor(110, 950, ['Organisation','(Owner / Manager)'])}
  ${actor(1660, 560, ['Administrateur','Plateforme'])}

  <!-- Associations: Utilisateur -->
  ${link(150,330, col1-140, rowY.r1)}
  ${link(150,340, col1-140, rowY.r2)}
  ${link(150,350, col1-140, rowY.r3)}
  ${link(150,360, col1-140, rowY.r4)}
  ${link(150,370, col1-140, rowY.r5)}
  ${link(160,330, col2-140, rowY.r1+10)}
  ${link(160,345, col2-140, rowY.r2+10)}

  <!-- Associations: Organisation -->
  ${link(150,930, col1-140, rowY.r6)}
  ${link(150,945, col1-140, rowY.r7)}
  ${link(150,960, col1-140, rowY.r8)}
  ${link(160,930, col2-140, rowY.r6)}
  ${link(160,945, col2-140, rowY.r7)}
  ${link(160,960, col2-140, rowY.r8)}

  <!-- Associations: Administrateur -->
  ${link(1620,520, col3+140, rowY.r1)}
  ${link(1620,540, col3+140, rowY.r2)}
  ${link(1620,560, col3+140, rowY.r4)}
  ${link(1620,580, col3+140, rowY.r6)}
  ${link(1620,600, col3+140, rowY.r8)}

  <!-- Include / Extend relationships -->
  ${link(col1+140, rowY.r3, col2-140, rowY.r3, {dashed:true, arrow:true})}
  ${stereo((col1+col2)/2, rowY.r3-14, '«include»')}

  ${link(col1+140, rowY.r4, col2-140, rowY.r4, {dashed:true, arrow:true})}
  ${stereo((col1+col2)/2, rowY.r4-14, '«include»')}

  ${curvedLink(col2+140, rowY.r8-10, col2+140, rowY.r5+10, col2+260, {dashed:true, arrow:true, color:'#6b7280'})}
  ${stereo(col2+300, (rowY.r8+rowY.r5)/2 - 10, '«include»' , '#6b7280')}
  ${stereo(col2+300, (rowY.r8+rowY.r5)/2+8, '(si approuvé)' , '#6b7280')}

  ${curvedLink(col2+140, rowY.r2+10, col2+140, rowY.r8-10, col2+300, {dashed:true, arrow:true})}
  ${stereo(col2+345, (rowY.r2+rowY.r8)/2 - 46, '«extend»')}
  ${stereo(col2+345, (rowY.r2+rowY.r8)/2 - 28, '(après rejet)')}

  <!-- Column 1 : Utilisateur -->
  ${ellipseUC(col1, rowY.r1, ["Créer un compte", "et vérifier l'identité"])}
  ${ellipseUC(col1, rowY.r2, ["Consulter et filtrer", "le catalogue de tâches"])}
  ${ellipseUC(col1, rowY.r3, ["Participer à", "une tâche"])}
  ${ellipseUC(col1, rowY.r4, ["Soumettre", "une preuve"])}
  ${ellipseUC(col1, rowY.r5, ["Consulter le", "portefeuille"])}
  ${ellipseUC(col1, rowY.r6, ["Créer une", "organisation"])}
  ${ellipseUC(col1, rowY.r7, ["Alimenter le budget", "(dépôt)"])}
  ${ellipseUC(col1, rowY.r8, ["Créer une campagne", "et ses tâches"])}

  <!-- Column 2 : transverse / inclus -->
  ${ellipseUC(col2, rowY.r1, ["Demander", "un retrait"], {fill:'#fff7ed', stroke:'#c2730b'})}
  ${ellipseUC(col2, rowY.r2, ["Ouvrir", "un litige"], {fill:'#fff7ed', stroke:'#c2730b'})}
  ${ellipseUC(col2, rowY.r3, ["Vérifier", "l'éligibilité"], {fill:'#f3f4f6', stroke:'#6b7280'})}
  ${ellipseUC(col2, rowY.r4, ["Détecter une fraude", "/ un doublon"], {fill:'#f3f4f6', stroke:'#6b7280'})}
  ${ellipseUC(col2, rowY.r5, ["Créditer", "la récompense"], {fill:'#f3f4f6', stroke:'#6b7280'})}
  ${ellipseUC(col2, rowY.r6, ["Soumettre la campagne", "à revue"], {fill:'#fff7ed', stroke:'#c2730b'})}
  ${ellipseUC(col2, rowY.r7, ["Suivre les", "participations"], {fill:'#fff7ed', stroke:'#c2730b'})}
  ${ellipseUC(col2, rowY.r8, ["Valider ou rejeter", "une soumission"], {fill:'#fff7ed', stroke:'#c2730b'})}

  <!-- Column 3 : Administrateur -->
  ${ellipseUC(col3, rowY.r1, ["Modérer / approuver", "une campagne"])}
  ${ellipseUC(col3, rowY.r2, ["Traiter", "un retrait"])}
  ${ellipseUC(col3, rowY.r4, ["Superviser les", "événements de fraude"])}
  ${ellipseUC(col3, rowY.r6, ["Arbitrer", "un litige"])}
  ${ellipseUC(col3, rowY.r8, ["Suspendre un compte", "ou une organisation"])}

  <!-- Legend -->
  <g font-family="Helvetica, Arial, sans-serif" font-size="13" fill="#4b5563">
    <rect x="300" y="1195" width="18" height="12" fill="#eef2ff" stroke="#3b4ba0"/>
    <text x="324" y="1205">Cas d'utilisation propre à un acteur</text>
    <rect x="300" y="1215" width="18" height="12" fill="#fff7ed" stroke="#c2730b"/>
    <text x="324" y="1225">Cas d'utilisation transverse (plusieurs acteurs impliqués dans le workflow)</text>
    <rect x="300" y="1235" width="18" height="12" fill="#f3f4f6" stroke="#6b7280"/>
    <text x="324" y="1245">Cas d'utilisation inclus (sous-fonction technique)</text>
  </g>
</svg>`;

fs.writeFileSync('usecase.svg', svg);
sharp(Buffer.from(svg)).png({ density: 220 }).resize(2300).toFile('../diagrams/diagramme-cas-utilisation.png')
  .then(info => console.log('OK', info))
  .catch(err => { console.error(err); process.exit(1); });
