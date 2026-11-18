import type { Category, Provider } from './model';

export function providersIn(category: Category, providers: readonly Provider[]): Provider[] {
  return providers.filter((provider) => provider.category === category);
}

export function canBeAdded(provider: Provider): boolean {
  return provider.availability !== 'COMING_SOON';
}

/** A category is open when at least one of its providers can be added. */
export function isCategoryOpen(category: Category, providers: readonly Provider[]): boolean {
  return providersIn(category, providers).some(canBeAdded);
}
