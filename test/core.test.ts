import { describe, expect, it } from "vitest";
import { sanitizeFilename, parseCommand, chunkText, bytesToBase64, base64ToBytes } from "../src/utils";
import { makeSimplePdf } from "../src/generators/pdf";
import { makeSimplePptx } from "../src/generators/pptx";
import { extractZip, makeZip } from "../src/generators/zip";

describe("utils", () => {
  it("sanitizes path traversal characters", () => {
    const safe = sanitizeFilename("../../secret.txt");
    expect(safe).not.toContain("/");
    expect(safe).not.toContain("\\");
  });
  it("parses commands", () => {
    expect(parseCommand("/new hello")).toEqual({ command: "new", args: "hello" });
    expect(parseCommand("hello")).toBeNull();
  });
  it("round-trips base64", () => {
    const bytes = new TextEncoder().encode("Neuralis ✓");
    expect(new TextDecoder().decode(base64ToBytes(bytesToBase64(bytes)))).toBe("Neuralis ✓");
  });
  it("chunks long text", () => {
    expect(chunkText("a".repeat(8000), 3900)).toHaveLength(3);
  });
});

describe("generators", () => {
  it("creates a PDF header", () => {
    const pdf = makeSimplePdf("hello");
    expect(new TextDecoder().decode(pdf.slice(0, 8))).toContain("%PDF-1.4");
  });
  it("creates and extracts a ZIP", () => {
    const zip = makeZip([{ name: "hello.txt", bytes: new TextEncoder().encode("hello") }]);
    const files = extractZip(zip);
    expect(new TextDecoder().decode(files[0].bytes)).toBe("hello");
  });
  it("creates a PPTX zip package", () => {
    const pptx = makeSimplePptx([{ title: "Title", body: "Body" }]);
    expect(pptx[0]).toBe(0x50);
    expect(pptx[1]).toBe(0x4b);
  });
});
