#!/usr/bin/env python3
"""Create signing material outside the checkout; never print secret values."""
import os
from pathlib import Path
import secrets
import subprocess
import sys


def prepare(config: Path, root: Path):
    config = config.expanduser().resolve()
    if config.is_relative_to(root.resolve()):
        raise ValueError('La configuration de signature doit rester hors du dépôt.')
    config.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    if config.exists():
        values = dict(line.strip().split('=', 1) for line in config.read_text().splitlines()
                      if line.strip() and not line.lstrip().startswith(('#', '!')) and '=' in line)
        store = Path(values['storeFile']).expanduser().resolve()
        if store.is_relative_to(root.resolve()):
            raise ValueError('Le keystore doit rester hors du dépôt.')
        for key in ('storePassword', 'keyPassword', 'keyAlias'):
            if not values.get(key):
                raise ValueError('Configuration de signature incomplète.')
        if not store.is_file():
            # Never replace a missing existing key: it may already sign public releases.
            raise ValueError('Keystore configuré absent : restaurer la sauvegarde de la clé.')
        config.chmod(0o600)
        store.chmod(0o600)
        return
    store = config.parent / 'release.keystore'
    if store.exists():
        raise ValueError('Keystore présent sans configuration : restaurer release.properties.')
    print('Création de la clé de release hors du dépôt, dans ~/.config/regie (ou REGIE_RELEASE_CONFIG). Sauvegarder la clé et release.properties.', flush=True)
    password = secrets.token_hex(32)
    env = dict(os.environ, REGIE_SIGN_PASSWORD=password)
    subprocess.run(['keytool', '-genkeypair', '-keystore', str(store), '-storetype', 'PKCS12',
                    '-alias', 'regie', '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                    '-dname', 'CN=Regie', '-storepass:env', 'REGIE_SIGN_PASSWORD',
                    '-keypass:env', 'REGIE_SIGN_PASSWORD'], env=env, check=True,
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    store.chmod(0o600)
    # Java Properties accepts spaces in a value, but backslashes must be escaped.
    store_value = str(store).replace('\\', '\\\\')
    fd = os.open(config, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as output:
        output.write(f'storeFile={store_value}\nstorePassword={password}\nkeyAlias=regie\nkeyPassword={password}\n')


if __name__ == '__main__':
    try:
        prepare(Path(sys.argv[1]), Path(sys.argv[2]))
    except (OSError, ValueError, KeyError, subprocess.CalledProcessError):
        print('Signature impossible : vérifier la configuration externe et la disponibilité de keytool. Aucune clé existante remplacée.', file=sys.stderr)
        sys.exit(1)
