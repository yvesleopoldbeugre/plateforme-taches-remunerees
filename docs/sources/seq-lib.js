const sharp = require('sharp');
const fs = require('fs');

function esc(s){ return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;'); }

function buildSequence({ title, participants, messages, width = 1500 }) {
  const topY = 70;
  const boxH = 56;
  const rowH = 78;
  const marginTop = topY + boxH + 40;
  const marginBottom = 60;
  const n = participants.length;
  const pad = 125;
  const usableW = width - pad * 2;
  const stepX = usableW / (n - 1);
  const xs = participants.map((_, i) => pad + i * stepX);

  let y = marginTop;
  const rows = [];
  for (const m of messages) {
    if (m.type === 'note') {
      rows.push({ ...m, y, h: (m.lines.length * 18) + 26 });
      y += (m.lines.length * 18) + 26 + 30;
    } else {
      rows.push({ ...m, y });
      y += rowH;
    }
  }
  const bottomY = y + 10;
  const height = bottomY + marginBottom;

  const idx = id => participants.findIndex(p => p.id === id);

  let svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <rect width="${width}" height="${height}" fill="#ffffff"/>
  <defs>
    <marker id="arrowS" markerWidth="11" markerHeight="11" refX="9" refY="3.5" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L0,7 L10,3.5 z" fill="#1f2937"/>
    </marker>
    <marker id="arrowO" markerWidth="12" markerHeight="12" refX="9" refY="4" orient="auto" markerUnits="strokeWidth">
      <path d="M0,0 L9,4 L0,8" fill="none" stroke="#1f2937" stroke-width="1.6"/>
    </marker>
  </defs>
  <text x="${width/2}" y="34" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-size="20" font-weight="700" fill="#111827">${esc(title)}</text>
`;

  // Lifelines
  participants.forEach((p, i) => {
    const x = xs[i];
    svg += `
  <rect x="${x-95}" y="${topY}" width="190" height="${boxH}" rx="6" fill="${p.color || '#1f3a8a'}" stroke="#111827" stroke-width="1"/>
  <text x="${x}" y="${topY+boxH/2+5}" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-size="15" font-weight="600" fill="#ffffff">${esc(p.label)}</text>
  <line x1="${x}" y1="${topY+boxH}" x2="${x}" y2="${bottomY}" stroke="#9ca3af" stroke-width="1.6" stroke-dasharray="5,4"/>
`;
  });

  // Messages / notes
  for (const r of rows) {
    if (r.type === 'note') {
      const i1 = idx(r.over[0]);
      const i2 = r.over.length > 1 ? idx(r.over[r.over.length-1]) : i1;
      const x1 = Math.min(xs[i1], xs[i2]) - 80;
      const x2 = Math.max(xs[i1], xs[i2]) + 80;
      svg += `
  <rect x="${x1}" y="${r.y}" width="${x2-x1}" height="${r.h}" fill="#fef9c3" stroke="#ca8a04" stroke-width="1.3"/>
  ${r.lines.map((l,i)=>`<text x="${(x1+x2)/2}" y="${r.y+22+i*18}" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-style="italic" font-size="13" fill="#713f12">${esc(l)}</text>`).join('')}
`;
      continue;
    }
    const i1 = idx(r.from), i2 = idx(r.to);
    const x1 = xs[i1], x2 = xs[i2];
    const dashed = r.type === 'return';
    const marker = r.type === 'return' ? 'arrowO' : 'arrowS';
    const color = r.type === 'return' ? '#4b5563' : (r.type === 'async' ? '#7c3aed' : '#1f2937');
    const ly = r.y;
    const labelY = ly - 8;
    svg += `
  <line x1="${x1}" y1="${ly}" x2="${x2}" y2="${ly}" stroke="${color}" stroke-width="1.8" ${dashed?'stroke-dasharray="6,4"':''} marker-end="url(#${marker})"/>
  <text x="${(x1+x2)/2}" y="${labelY}" text-anchor="middle" font-family="Helvetica, Arial, sans-serif" font-size="13.5" fill="#111827">${esc(r.label)}</text>
`;
    if (r.step) {
      svg += `<text x="${(x1+x2)/2 - (x2>x1? ( (x2-x1)/2 - 14) : -((x1-x2)/2 - 14))}" y="${labelY}" font-family="Helvetica, Arial, sans-serif" font-size="12" font-weight="700" fill="#6b7280">${esc(r.step)}</text>`;
    }
  }

  svg += `</svg>`;
  return svg;
}

function renderTo(svg, outPath) {
  return sharp(Buffer.from(svg)).png({ density: 220 }).resize(2300).toFile(outPath);
}

module.exports = { buildSequence, renderTo };
