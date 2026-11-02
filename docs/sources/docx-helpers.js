const fs = require('fs');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell,
  WidthType, ShadingType, BorderStyle, AlignmentType, TableOfContents, PageBreak,
  ImageRun, LevelFormat, Header, Footer, PageNumber, convertInchesToTwip, Numbering,
  VerticalAlign, TabStopType, TabStopPosition,
} = require('docx');

const COLORS = {
  primary: '1f3a8a',
  primaryDark: '111827',
  accent: '0f766e',
  text: '1f2937',
  muted: '6b7280',
  tableHeader: '1f3a8a',
  tableHeaderText: 'ffffff',
  tableAltRow: 'f3f4f6',
  border: '9ca3af',
};

const FONT = 'Calibri';

function titlePage({ docType, title, subtitle, project, version, date, author }) {
  return [
    new Paragraph({ spacing: { before: 2400 }, children: [] }),
    new Paragraph({
      alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: project, bold: true, size: 28, color: COLORS.muted, font: FONT })],
    }),
    new Paragraph({ spacing: { before: 200 }, alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: docType, bold: true, size: 56, color: COLORS.primary, font: FONT })] }),
    new Paragraph({ spacing: { before: 200, after: 600 }, alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: title, size: 32, color: COLORS.primaryDark, font: FONT })] }),
    subtitle ? new Paragraph({ alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: subtitle, italics: true, size: 24, color: COLORS.muted, font: FONT })] }) : new Paragraph({}),
    new Paragraph({ spacing: { before: 1200 }, alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: `Version ${version}`, size: 22, font: FONT })] }),
    new Paragraph({ alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: date, size: 22, font: FONT })] }),
    new Paragraph({ spacing: { before: 200 }, alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: author, size: 22, color: COLORS.muted, font: FONT })] }),
    new Paragraph({ spacing: { before: 2000 }, alignment: AlignmentType.CENTER,
      children: [new TextRun({ text: 'Document confidentiel — usage interne au projet', size: 18, italics: true, color: COLORS.muted, font: FONT })] }),
    new Paragraph({ children: [new PageBreak()] }),
  ];
}

function tocPage() {
  return [
    h1('Sommaire', { toc: false }),
    new TableOfContents('Sommaire', { hyperlink: true, headingStyleRange: '1-3' }),
    new Paragraph({ children: [new PageBreak()] }),
  ];
}

function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    spacing: { before: 420, after: 220 },
    border: { bottom: { color: COLORS.primary, space: 4, style: BorderStyle.SINGLE, size: 10 } },
    children: [new TextRun({ text, bold: true, size: 32, color: COLORS.primary, font: FONT })],
  });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 320, after: 160 },
    children: [new TextRun({ text, bold: true, size: 26, color: COLORS.primaryDark, font: FONT })],
  });
}
function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    spacing: { before: 240, after: 120 },
    children: [new TextRun({ text, bold: true, size: 23, color: COLORS.accent, font: FONT })],
  });
}
function p(text, opts = {}) {
  const runs = Array.isArray(text) ? text : [{ text }];
  return new Paragraph({
    spacing: { before: opts.before ?? 80, after: opts.after ?? 120, line: 276 },
    alignment: opts.align || AlignmentType.JUSTIFIED,
    children: runs.map(r => new TextRun({
      text: r.text, bold: r.bold, italics: r.italics, color: r.color || COLORS.text,
      size: r.size || 22, font: FONT,
    })),
  });
}
function note(text) {
  return new Paragraph({
    spacing: { before: 100, after: 160 },
    shading: { type: ShadingType.CLEAR, fill: 'FEF9C3' },
    border: {
      top: { color: 'CA8A04', space: 4, style: BorderStyle.SINGLE, size: 6 },
      bottom: { color: 'CA8A04', space: 4, style: BorderStyle.SINGLE, size: 6 },
      left: { color: 'CA8A04', space: 4, style: BorderStyle.SINGLE, size: 6 },
      right: { color: 'CA8A04', space: 4, style: BorderStyle.SINGLE, size: 6 },
    },
    children: [new TextRun({ text: `ℹ ${text}`, italics: true, size: 21, color: '713F12', font: FONT })],
  });
}

const bulletNumbering = {
  config: [{
    reference: 'bullet-list',
    levels: [{ level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: convertInchesToTwip(0.35), hanging: convertInchesToTwip(0.18) } } } },
      { level: 1, format: LevelFormat.BULLET, text: '◦', alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: convertInchesToTwip(0.7), hanging: convertInchesToTwip(0.18) } } } }],
  }, ...Array.from({ length: 12 }, (_, n) => ({
    reference: `numbered-list-${n}`,
    levels: [{ level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT,
      style: { paragraph: { indent: { left: convertInchesToTwip(0.4), hanging: convertInchesToTwip(0.25) } } } }],
  }))],
};

function bullet(text, opts = {}) {
  const runs = Array.isArray(text) ? text : [{ text }];
  return new Paragraph({
    numbering: { reference: 'bullet-list', level: opts.level || 0 },
    spacing: { before: 40, after: 40, line: 270 },
    children: runs.map(r => new TextRun({ text: r.text, bold: r.bold, italics: r.italics, color: r.color || COLORS.text, size: r.size || 22, font: FONT })),
  });
}
function numbered(text, opts = {}) {
  const runs = Array.isArray(text) ? text : [{ text }];
  return new Paragraph({
    numbering: { reference: `numbered-list-${opts.list || 0}`, level: 0 },
    spacing: { before: 40, after: 40, line: 270 },
    children: runs.map(r => new TextRun({ text: r.text, bold: r.bold, italics: r.italics, color: r.color || COLORS.text, size: r.size || 22, font: FONT })),
  });
}

function cell(text, opts = {}) {
  const baseColor = opts.color || (opts.header ? COLORS.tableHeaderText : COLORS.text);
  let paragraphs;
  if (text && typeof text === 'object' && !Array.isArray(text) && text.lines) {
    paragraphs = text.lines.map((line, i) => new Paragraph({
      spacing: { before: i === 0 ? 0 : 40, after: 0, line: 252 },
      alignment: opts.align || AlignmentType.LEFT,
      children: [new TextRun({
        text: (text.bulleted === false || i === 0) ? line : `– ${line}`,
        bold: (i === 0 ? (text.titleBold ?? opts.bold) : opts.bold), size: opts.size || 20,
        color: baseColor, font: FONT,
      })],
    }));
  } else {
    const runs = (Array.isArray(text) ? text : [{ text }]).map(r => new TextRun({
      text: r.text, bold: r.bold ?? opts.bold, italics: r.italics, size: opts.size || 20,
      color: r.color || baseColor, font: FONT,
    }));
    paragraphs = [new Paragraph({ children: runs, alignment: opts.align || AlignmentType.LEFT })];
  }
  return new TableCell({
    width: opts.width ? { size: opts.width, type: WidthType.DXA } : undefined,
    shading: opts.header ? { type: ShadingType.CLEAR, fill: COLORS.tableHeader }
      : (opts.alt ? { type: ShadingType.CLEAR, fill: COLORS.tableAltRow } : undefined),
    verticalAlign: VerticalAlign.CENTER,
    margins: { top: 90, bottom: 90, left: 110, right: 110 },
    children: paragraphs,
  });
}

function dataTable({ header, rows, widths }) {
  const totalWidth = widths ? widths.reduce((a, b) => a + b, 0) : 9638;
  const colWidths = widths || header.map(() => Math.floor(totalWidth / header.length));
  return new Table({
    width: { size: totalWidth, type: WidthType.DXA },
    columnWidths: colWidths,
    borders: {
      top: { style: BorderStyle.SINGLE, size: 4, color: COLORS.border },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: COLORS.border },
      left: { style: BorderStyle.SINGLE, size: 4, color: COLORS.border },
      right: { style: BorderStyle.SINGLE, size: 4, color: COLORS.border },
      insideHorizontal: { style: BorderStyle.SINGLE, size: 2, color: COLORS.border },
      insideVertical: { style: BorderStyle.SINGLE, size: 2, color: COLORS.border },
    },
    rows: [
      new TableRow({
        tableHeader: true,
        children: header.map((h, i) => cell(h, { header: true, bold: true, width: colWidths[i], align: AlignmentType.CENTER })),
      }),
      ...rows.map((row, ri) => new TableRow({
        children: row.map((c, i) => cell(c, { alt: ri % 2 === 1, width: colWidths[i] })),
      })),
    ],
  });
}

function imageParagraph(path, { width, caption } = {}) {
  const data = fs.readFileSync(path);
  const sizeOf = require('./imgsize')(path);
  const w = width || 560;
  const h = Math.round(w * (sizeOf.height / sizeOf.width));
  const paras = [
    new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 160, after: caption ? 60 : 160 },
      children: [new ImageRun({ type: 'png', data, transformation: { width: w, height: h } })],
    }),
  ];
  if (caption) {
    paras.push(new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { after: 220 },
      children: [new TextRun({ text: caption, italics: true, size: 19, color: COLORS.muted, font: FONT })],
    }));
  }
  return paras;
}

function pageBreak() { return new Paragraph({ children: [new PageBreak()] }); }

function wrapCodeLine(line, maxLen = 90, contIndent = 8) {
  if (line.length <= maxLen) return [line];
  const leading = line.match(/^\s*/)[0];
  let rest = line.slice(leading.length);
  const out = [];
  let first = true;
  while (true) {
    const prefix = first ? leading : leading + ' '.repeat(contIndent);
    const budget = maxLen - prefix.length;
    if (rest.length <= budget) { out.push(prefix + rest); break; }
    let cut = rest.lastIndexOf(',', budget);
    if (cut < Math.floor(budget * 0.4)) cut = rest.lastIndexOf(' ', budget);
    if (cut <= 0) cut = budget;
    out.push(prefix + rest.slice(0, cut + 1));
    rest = rest.slice(cut + 1).replace(/^\s+/, '');
    first = false;
  }
  return out;
}

function codeBlock(code) {
  const rawLines = code.replace(/\t/g, '    ').split('\n');
  const lines = rawLines.flatMap(l => wrapCodeLine(l));
  return lines.map((line, i) => new Paragraph({
    shading: { type: ShadingType.CLEAR, fill: 'F3F4F6' },
    spacing: { before: 0, after: 0, line: 240 },
    indent: { left: 180, right: 180 },
    border: i === 0 ? { top: { color: COLORS.border, space: 4, style: BorderStyle.SINGLE, size: 4 } }
      : (i === lines.length - 1 ? { bottom: { color: COLORS.border, space: 4, style: BorderStyle.SINGLE, size: 4 } } : undefined),
    children: [new TextRun({ text: line.length ? line : ' ', font: 'Consolas', size: 16, color: '111827' })],
  }));
}

function buildAndSave(sections, outPath) {
  const doc = new Document({
    creator: 'Plateforme de Tâches Rémunérées',
    title: outPath,
    numbering: bulletNumbering,
    styles: {
      default: {
        document: { run: { font: FONT, size: 22 } },
      },
    },
    sections: [{
      properties: {
        page: {
          margin: { top: 1100, bottom: 1100, left: 1100, right: 1100 },
        },
      },
      headers: {
        default: new Header({
          children: [new Paragraph({
            tabStops: [{ type: TabStopType.RIGHT, position: TabStopPosition.MAX }],
            border: { bottom: { color: COLORS.border, space: 2, style: BorderStyle.SINGLE, size: 4 } },
            children: [new TextRun({ text: 'Plateforme de Tâches Rémunérées — MVP', size: 16, color: COLORS.muted, font: FONT })],
          })],
        }),
      },
      footers: {
        default: new Footer({
          children: [new Paragraph({
            alignment: AlignmentType.CENTER,
            children: [
              new TextRun({ text: 'Page ', size: 18, color: COLORS.muted, font: FONT }),
              new TextRun({ children: [PageNumber.CURRENT], size: 18, color: COLORS.muted, font: FONT }),
              new TextRun({ text: ' / ', size: 18, color: COLORS.muted, font: FONT }),
              new TextRun({ children: [PageNumber.TOTAL_PAGES], size: 18, color: COLORS.muted, font: FONT }),
            ],
          })],
        }),
      },
      children: sections,
    }],
  });

  return Packer.toBuffer(doc).then(buf => {
    fs.writeFileSync(outPath, buf);
    console.log('Written', outPath);
  });
}

module.exports = {
  COLORS, FONT, titlePage, tocPage, h1, h2, h3, p, note, bullet, numbered,
  dataTable, cell, imageParagraph, pageBreak, codeBlock, buildAndSave, Paragraph, TextRun, AlignmentType,
};
