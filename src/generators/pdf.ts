function pdfSafe(input: string): string {
  return input
    .normalize("NFKD")
    .replace(/[^\x20-\x7E\n\r\t]/g, "?")
    .replace(/\\/g, "\\\\")
    .replace(/\(/g, "\\(")
    .replace(/\)/g, "\\)");
}

function wrap(text: string, width = 88): string[] {
  const lines: string[] = [];
  for (const raw of text.replace(/\r/g, "").split("\n")) {
    if (!raw) { lines.push(""); continue; }
    let line = raw;
    while (line.length > width) {
      let cut = line.lastIndexOf(" ", width);
      if (cut < 1) cut = width;
      lines.push(line.slice(0, cut));
      line = line.slice(cut).trimStart();
    }
    lines.push(line);
  }
  return lines;
}

export function makeSimplePdf(text: string): Uint8Array {
  const lines = wrap(text);
  const pages: string[][] = [];
  for (let i = 0; i < lines.length; i += 52) pages.push(lines.slice(i, i + 52));
  if (!pages.length) pages.push([""]);

  const objects: string[] = [];
  const pageIds: number[] = [];
  const contentIds: number[] = [];
  const fontId = 3;
  const pagesId = 2;
  objects.push("<< /Type /Catalog /Pages 2 0 R >>");
  objects.push(""); // patched after page IDs are known
  objects.push("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");

  for (const page of pages) {
    const stream = `BT\n/F1 11 Tf\n72 742 Td\n14 TL\n${page.map((line) => `(${pdfSafe(line)}) Tj T*`).join("\n")}\nET`;
    contentIds.push(objects.length + 1);
    objects.push(`<< /Length ${new TextEncoder().encode(stream).length} >>\nstream\n${stream}\nendstream`);
    pageIds.push(objects.length + 1);
    objects.push(""); // patched with actual content object id
  }

  const kids = pageIds.map((id) => `${id} 0 R`).join(" ");
  objects[1] = `<< /Type /Pages /Kids [${kids}] /Count ${pageIds.length} >>`;
  for (let i = 0; i < pageIds.length; i++) {
    objects[pageIds[i] - 1] = `<< /Type /Page /Parent ${pagesId} 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 ${fontId} 0 R >> >> /Contents ${contentIds[i]} 0 R >>`;
  }

  const enc = new TextEncoder();
  let pdf = "%PDF-1.4\n%\xE2\xE3\xCF\xD3\n";
  const offsets: number[] = [0];
  for (let i = 0; i < objects.length; i++) {
    offsets.push(enc.encode(pdf).length);
    pdf += `${i + 1} 0 obj\n${objects[i]}\nendobj\n`;
  }
  const xrefOffset = enc.encode(pdf).length;
  pdf += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (let i = 1; i <= objects.length; i++) pdf += `${String(offsets[i]).padStart(10, "0")} 00000 n \n`;
  pdf += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xrefOffset}\n%%EOF\n`;
  return enc.encode(pdf);
}
