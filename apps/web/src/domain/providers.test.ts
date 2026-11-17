import type { Provider } from './model';
import { canBeAdded, isCategoryOpen, providersIn } from './providers';

const provider = (id: Provider['provider'], category: Provider['category'], availability: Provider['availability']): Provider =>
  ({ provider: id, category, availability, displayName: id, signInRequired: false });

const PROVIDERS = [
  provider('UPLOAD', 'CAPTURE', 'LIVE'),
  provider('PLAUD', 'CAPTURE', 'PREVIEW'),
  provider('GITHUB', 'REPO', 'COMING_SOON'),
];

describe('providers', () => {
  it('lists the providers of one category', () => {
    expect(providersIn('CAPTURE', PROVIDERS).map((p) => p.provider)).toEqual(['UPLOAD', 'PLAUD']);
  });

  it('lets live and preview providers be added, not coming-soon ones', () => {
    expect(PROVIDERS.map(canBeAdded)).toEqual([true, true, false]);
  });

  it('opens a category only when something in it can be added', () => {
    expect(isCategoryOpen('CAPTURE', PROVIDERS)).toBe(true);
    expect(isCategoryOpen('REPO', PROVIDERS)).toBe(false);
  });
});
