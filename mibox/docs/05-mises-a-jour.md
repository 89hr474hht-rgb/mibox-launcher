# Mises à jour du launcher : publier une version, et ce qui est vérifié

Cette fiche complète la §5 de [04-architecture.md](04-architecture.md). Le code se trouve dans `launcher/mibox/`, le workflow dans `.github/workflows/release.yml`.

## 1. Ce que voit la famille

1. Une fois par jour, et au démarrage du launcher, la box interroge GitHub. Pas plus d'une fois par heure au démarrage.
2. Si une version plus récente existe, une **pastille** apparaît en haut à droite de l'accueil, avec [Mettre à jour] et [Plus tard].
   - **Retour** équivaut à « Plus tard », qui masque la pastille 24 h.
   - Ni la pastille ni la vérification ne bloquent l'accueil.
3. [Mettre à jour] ouvre la page « Mises à jour du launcher ». Le launcher y télécharge l'APK, le vérifie, puis Android affiche **sa fenêtre de confirmation**, obligatoire sur Android 11.
4. Après « Installer », l'accueil revient avec le message « Launcher mis à jour : version X ».

La page s'ouvre aussi depuis le bouton **Paramètres** de l'accueil → **Mises à jour du launcher** (première ligne), ou depuis la liste des applis de Google TV. Elle affiche :
- la version installée et la dernière vérification ;
- les nouveautés et l'avancement ;
- les erreurs et l'historique ;
- les boutons « Vérifier maintenant », « Ignorer cette version » et « La proposer à nouveau ».

## 2. Ce qui est vérifié avant d'installer

Si une seule de ces vérifications échoue, le fichier est supprimé et rien n'est installé.

| # | Vérification | Protège contre |
|---|---|---|
| 1 | Release publiée (ni brouillon ni pré-release), tag `vX.Y.Z`, fichiers `mibox-launcher-X.Y.Z.apk` et `.sha256` hébergés sur `github.com/89hr474hht-rgb/mibox-launcher/releases/download/` | Un lien vers un autre site |
| 2 | HTTPS de bout en bout, sans jeton, et taille du fichier = taille annoncée par l'API | Un téléchargement interrompu ou détourné |
| 3 | Espace libre ≥ 3 × taille de l'APK + 20 Mo | Une box saturée (environ 870 Mo libres) |
| 4 | SHA-256 calculé = fichier `.sha256` = `digest` de l'API GitHub | Un fichier abîmé |
| 5 | Package `com.gathin.tvhome`, `versionName` = tag, `versionCode` = tag, et supérieur à la version installée | Un autre APK, ou un retour en arrière |
| 6 | **Certificat de signature identique à celui du launcher installé** | Un APK qui ne vient pas de votre clé. C'est la vraie garantie : les SHA-256 viennent de GitHub, comme l'APK |
| 7 | Android revérifie la signature à l'installation et demande confirmation | — |

Aucun jeton GitHub n'est intégré à l'application. L'API publique accepte 60 requêtes par heure ; le launcher en fait environ une par jour.

## 3. Publier une version

Préalable (une seule fois, **avec votre accord**) : créer les 4 secrets du dépôt, dans *Settings → Secrets and variables → Actions* :
- `MIBOX_KEYSTORE_BASE64`, qui est `base64 -i ~/.mibox-launcher/release.jks` ;
- `MIBOX_KEYSTORE_PASSWORD`, `MIBOX_KEY_ALIAS` et `MIBOX_KEY_PASSWORD`, qui se trouvent dans `~/.mibox-launcher/keystore.properties`.

Ensuite, pour chaque version :

1. Changer `launcher/mibox/VERSION` (par exemple `1.0.1`).
2. Dans `CHANGELOG.md`, renommer `## [Non publié]` en `## [1.0.1] - AAAA-MM-JJ`. Ces lignes deviennent les notes affichées sur la box : les écrire en français, de façon courte.
3. Commiter sur `main`, puis `git tag v1.0.1 && git push origin main v1.0.1`.
4. Le workflow vérifie, puis publie :
   - le tag est bien sur `main` et correspond au fichier `VERSION` et au CHANGELOG ;
   - les tests, le lint et la compilation signée réussissent ;
   - la taille de l'APK est inférieure à 720 Ko ;
   - le package et les numéros de version sont les bons ;
   - **l'empreinte de la clé** est celle de votre clé ;
   - il publie alors l'APK, son `.sha256` et les notes.

La box propose la mise à jour au plus tard 24 h après, ou tout de suite avec « Vérifier maintenant ».

## 4. Clé de signature

- Emplacement : `~/.mibox-launcher/release.jks` et `keystore.properties`, hors du dépôt, en `chmod 600`.
- Empreinte SHA-256 : `EA:32:31:BA:D9:36:83:3E:9F:2B:7D:81:E6:4B:94:6B:2C:29:0A:7E:56:8D:99:63:91:AF:57:70:6F:CB:50:E7`.
- **À sauvegarder hors du Mac** (clé USB, gestionnaire de mots de passe). Si elle est perdue, plus aucune mise à jour n'est possible : il faudrait désinstaller le launcher et le réinstaller depuis le Mac.

## 5. Première installation (étape E1+E2)

Le script `mibox/scripts/install/install-launcher.sh` fait, dans l'ordre :

1. Il compile l'édition Mi Box signée et vérifie l'APK.
2. Il appelle le script de CleanYourTV `launcher/build-and-install.sh` (`SKIP_BUILD=1 STOCK_LAUNCHER=none`), qui installe le launcher sans désactiver Google TV.
3. Il exécute `appops set com.gathin.tvhome REQUEST_INSTALL_PACKAGES allow`. Google TV n'a pas d'écran pour cette autorisation « applis inconnues ». Elle ne permet que de *proposer* une installation : la confirmation reste demandée à chaque fois.

Sans `--execute`, le script ne fait que compiler et vérifier. Avec `--execute`, il agit sur la box et s'affiche à l'écran : **seulement dans un créneau convenu**.

## 6. Limites connues

- **La confirmation d'Android ne peut pas être évitée.** Sur Android 11, une installation silencieuse demanderait un « propriétaire de l'appareil », ce qui oblige à retirer tous les comptes Google de la box. Ce n'est pas envisagé.
- **Play Protect** peut, la première fois, demander d'analyser l'application. C'est à accepter sur la box.
- Le launcher ne se met à jour que lui-même. Il ne touche à aucune autre application ni à aucun réglage.
