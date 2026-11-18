import { MAX_FILE_BYTES, extensionOf, problemWith } from './files';

describe('problemWith', () => {
  it('accepts the supported types within the limit', () => {
    for (const name of ['spec.pdf', 'brief.DOCX', 'notes.md', 'readme.markdown', 'log.txt']) {
      expect(problemWith({ name, size: 10 })).toBeNull();
    }
  });

  it('refuses other types', () => {
    expect(problemWith({ name: 'setup.exe', size: 10 })).toBe('unsupported-type');
    expect(problemWith({ name: 'no-extension', size: 10 })).toBe('unsupported-type');
  });

  it('refuses empty and oversized files', () => {
    expect(problemWith({ name: 'a.pdf', size: 0 })).toBe('empty');
    expect(problemWith({ name: 'a.pdf', size: MAX_FILE_BYTES + 1 })).toBe('too-large');
  });

  it('reads the extension after the last dot', () => {
    expect(extensionOf('archive.tar.PDF')).toBe('pdf');
  });
});
