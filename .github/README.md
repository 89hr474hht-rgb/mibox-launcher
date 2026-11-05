# mibox-launcher

Fork de **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)** (licence MIT, © Gathin), adapté à une **Xiaomi Mi Box S 2e génération** (`MiTV-AFKR0`, Android 11) : launcher minimaliste à la place de l'interface Google TV, **sans root**, de façon **réversible**, avec mise à jour du launcher depuis GitHub.

> Statut : **Phase 3 (développement) en préparation.** Aucun APK n'est encore publié. Les tags `v0.x` de ce dépôt correspondent à un ancien projet (Razorbill), conservé dans la branche `legacy-razorbill`.

## Ce qui vient de CleanYourTV, ce qui est propre au fork

| Partie | Origine |
|---|---|
| `launcher/`, `tools/`, `examples/`, `themes/`, `addons/`, `docs/`, `README.md` | CleanYourTV, à jour avec l'upstream. Voir [README.md](../README.md) |
| `mibox/` | Ce fork : documentation, listes de désactivation par étape, configuration, scripts |
| Modifications du launcher | Ce fork, petites et isolées (signature, mise à jour depuis GitHub, vérification du rôle HOME) |

## Principes

- **Sans root, sans flash** : les composants Google TV sont *désactivés* (`pm disable-user`), jamais supprimés.
- **Une étape à la fois**, avec l'enregistrement de restauration des outils CleanYourTV.
- **Les applications d'abord** : Netflix, YouTube, Prime Video, SFR TV, la télécommande Bluetooth, le CEC et l'audio Bluetooth doivent continuer à fonctionner.
- **Mises à jour du launcher** via GitHub Releases, vérifiées (SHA-256 + certificat de signature épinglé), installées avec la confirmation d'Android.

## Documentation du fork

| Document | Contenu |
|---|---|
| [01 — Rapport initial](../mibox/docs/01-rapport-analyse-initiale.md) | Analyse de CleanYourTV, possibilités et limites, risques |
| [02 — Plan de tests](../mibox/docs/02-plan-de-tests.md) | Tests avant/après chaque étape |
| [03 — Audit de la box](../mibox/docs/03-audit-mi-box.md) | État de référence, inventaire classé des packages, décisions |
| [04 — Architecture](../mibox/docs/04-architecture.md) | Organisation du fork, modifications, mises à jour, étapes |

## Récupérer les nouveautés de CleanYourTV

```bash
git remote add upstream https://github.com/Gathin23/cleanyourtv.git   # une seule fois
git fetch upstream && git merge upstream/main
```

## Licence

MIT, comme CleanYourTV. Le fichier [LICENSE](../LICENSE) d'origine est conservé.
