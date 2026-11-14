import { BridgeError } from './model.ts';

/** A single portable folder name, never a path. */
export function projectFolderName(raw: string): string {
  const text = raw.normalize('NFD').replace(/\p{M}/gu, '').toLowerCase().trim();
  if (!/^[a-z0-9 _’'-]+$/.test(text)) {
    throw new BridgeError(
      'bad_project_name',
      'Nom de projet invalide : utilise des lettres, chiffres, espaces ou tirets.',
    );
  }
  const name = text.replace(/[ _’'-]+/g, '-').replace(/^-|-$/g, '');
  if (name.length === 0 || name.length > 80) {
    throw new BridgeError(
      'bad_project_name',
      'Nom de projet vide ou trop long (80 caractères maximum).',
    );
  }
  return name;
}
