import { translator } from './i18n';
import { MESSAGES } from './messages';

describe('translations', () => {
  it('has every English message in Spanish, none left empty', () => {
    expect(Object.keys(MESSAGES.es).sort()).toEqual(Object.keys(MESSAGES.en).sort());
    expect(Object.values(MESSAGES.es).every((text) => text.trim().length > 0)).toBe(true);
  });

  it('fills in values', () => {
    expect(translator('en')('dialog.step', { current: 1, total: 3 })).toBe('Step 1 of 3');
    expect(translator('es')('dialog.step', { current: 1, total: 3 })).toBe('Paso 1 de 3');
  });

  it('uses the singular wording for one item, in both languages', () => {
    expect(translator('en')('upload.review.files', { count: 1 })).toBe('1 file');
    expect(translator('en')('upload.review.files', { count: 2 })).toBe('2 files');
    expect(translator('es')('upload.review.files', { count: 1 })).toBe('1 archivo');
    expect(translator('en')('upload.result.lead', { done: 1, total: 1 })).toBe('1 of 1 file is waiting for review in the hub.');
  });

  it('has a plural form for every singular one', () => {
    const singular = Object.keys(MESSAGES.en).filter((key) => key.endsWith('_one'));
    expect(singular.every((key) => key.replace(/_one$/, '') in MESSAGES.en)).toBe(true);
  });
});
