# mibox-launcher

Un launcher Android TV minimaliste et un ensemble de scripts ADB **réversibles** pour remplacer l'interface Google TV d'une **Xiaomi Mi Box S 2e génération** (`MiTV-AFKR0`, Android 11), **sans root**.

> Statut : **Phase 2 (conception)**. Aucun APK n'est encore publié. Les versions `v0.x` du dépôt correspondent à un ancien projet (Razorbill), conservé dans la branche `legacy-razorbill`.

## Principes

- **Sans root, sans flash.** Les composants Google TV sont *désactivés* (`pm disable-user`), jamais supprimés. Le bootloader de cet appareil n'est de toute façon pas déverrouillable.
- **Une étape à la fois**, avec un instantané avant et un script de restauration après.
- **Les applications d'abord** : Netflix, YouTube, Prime Video, SFR TV, la télécommande Bluetooth, le CEC Samsung et l'audio Bluetooth doivent continuer à fonctionner. Voir le [plan de tests](docs/02-plan-de-tests.md).
- **Mises à jour du launcher** via GitHub Releases, vérifiées par SHA-256 et par certificat de signature épinglé, installées avec la confirmation d'Android.

## Documentation

| Document | Contenu |
|---|---|
| [01 — Rapport initial](docs/01-rapport-analyse-initiale.md) | Analyse de CleanYourTV, possibilités et limites, risques |
| [02 — Plan de tests](docs/02-plan-de-tests.md) | Tests avant/après chaque étape (Bluetooth, CEC, volume, SFR TV, applications) |
| [03 — Audit de la box](docs/03-audit-mi-box.md) | État de référence, inventaire classé des packages, décisions |
| [04 — Architecture](docs/04-architecture.md) | Launcher, système de mise à jour, scripts, étapes |

## Scripts

| Script | Rôle | Modifie la box ? |
|---|---|---|
| [`scripts/audit/audit.sh`](scripts/audit/audit.sh) | Audit complet par ADB (liste blanche de commandes en lecture seule) | Non |
| [`scripts/maintenance/uninstall-razorbill.sh`](scripts/maintenance/uninstall-razorbill.sh) | Désinstallation vérifiée de l'ancien launcher (déjà exécutée) | Oui, avec confirmation |

Les résultats d'audit sont écrits dans `audits/`, qui n'est **jamais** versionné : ils contiennent des identifiants de l'appareil.

## Crédits

Analyse inspirée de [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (licence MIT), notamment pour les listes validées sur la Mi Box S 2e gén. et les garde-fous ADB.
