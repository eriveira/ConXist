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
});
