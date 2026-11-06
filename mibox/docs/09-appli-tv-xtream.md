# LaunchOS TV : l'appli pour ton compte Xtream

**Statut :** 0.1.0 publiée (tag `tv-v0.1.0`) et installée sur la box le 6 novembre 2026, mises à jour autorisées.
0.2.0 ajoute le menu Actualiser et le guide des programmes. Testé sur l'émulateur avec un faux serveur ; jamais
connecté à ton serveur par moi.

## Ce qu'on a décidé

| Question | Décision |
|---|---|
| Dans le launcher ou à part ? | **À part** : `com.gathin.launchos.tv`, nom « LaunchOS TV ». Le launcher reste léger (limite de 737 Ko), et un plantage du lecteur n'emporte pas l'accueil. |
| Appli existante ou la nôtre ? | **La nôtre**, interface écrite de zéro dans le style de LaunchOS (police Inter, mêmes couleurs). Seul le moteur vidéo vient d'une bibliothèque : Media3 / ExoPlayer de Google (Apache 2.0), compilé dans l'APK. |
| Play Store ? | Aucune dépendance : ni Play Store, ni services Google, ni compte. |
| Identifiants | Tu les tapes **dans l'appli, sur la box** (écran Compte). Ils ne passent jamais par moi. |
| Contenu de la V1 | Chaînes, films, séries, lecture. Le guide des programmes et le reste viendront en V2. |

## Ce que fait la V1

- **Compte** : serveur, identifiant, mot de passe. « Enregistrer et tester » interroge ton serveur d'abord, et ne
  garde le compte que s'il est accepté. Le résultat affiche la date de fin d'abonnement, le nombre de connexions
  et les formats du direct. Tu peux aussi coller un lien M3U (`get.php?username=…&password=…`) dans le champ
  Serveur : l'identifiant et le mot de passe sont repris.
- **Chaînes, Films, Séries** : à gauche les trois sections et Compte, au milieu les catégories de ton fournisseur, à
  droite les chaînes (logo et numéro) ou les affiches. Une catégorie se charge quand tu passes dessus. Retour
  remonte d'une colonne à la fois avant de quitter.
- **Fiche série** : affiche, résumé, saisons en haut, épisodes avec image et résumé.
- **Lecture** plein écran :
  - **direct** : nom et numéro de la chaîne pendant 4 s, CH+/CH− ou Haut/Bas pour zapper dans la catégorie, OK
    pour revoir le nom. Si le flux tombe, l'appli réessaie deux fois, puis en HLS, avant d'afficher l'erreur ;
  - **films et épisodes** : OK affiche les commandes, Gauche/Droite recule de 10 s ou avance de 30 s.
- **Actualiser** (0.2.0) : recharge les chaînes, les films, les séries ou tout, vide le guide, vide les logos et
  affiches gardés sur la box. Sans ça, les listes se rechargent seules après 6 heures.
- **Guide des programmes** (0.2.0) : `get_short_epg` chaîne par chaîne. Le programme en cours sous chaque chaîne
  avec une barre d'avancement ; en direct, les heures, le titre et le programme suivant. Gardé jusqu'à la fin du
  programme. La grille complète viendra plus tard.
- **Messages d'erreur en français** : identifiants refusés, compte expiré, adresse introuvable, serveur muet, flux
  refusé (souvent une autre lecture en cours sur un compte à une connexion), format que la box ne sait pas décoder.

## Mises à jour automatiques

Même logique que LaunchOS, avec le même code adapté :
- **Où** : les publications du même dépôt GitHub, mais avec des tags `tv-vX.Y.Z` et un APK `launchos-tv-X.Y.Z.apk`.
  LaunchOS ignore ces tags, et LaunchOS TV ignore ceux de LaunchOS. Les versions TV ne sont jamais marquées
  « dernière version » sur GitHub.
- **Quand** : une vérification par jour en arrière-plan, plus une à l'ouverture de l'appli (au plus une par heure).
- **Comment** : téléchargement en HTTPS depuis GitHub seulement, taille vérifiée, empreinte SHA-256 comparée à celle
  publiée, puis l'APK doit être LaunchOS TV, de la version annoncée, plus récent, et **signé avec la même clé que
  l'appli installée**. Android demande ensuite la confirmation.
- **À l'écran** : « Mise à jour ! » avec un point rose dans le menu, un bandeau une fois par version, et l'écran
  Mises à jour (notes de version, Installer, Plus tard, historique).
- **Publication** : `.github/workflows/release-tv.yml`, déclenché par un tag `tv-vX.Y.Z` qui correspond à
  `mibox/tv/VERSION` et à une section de `mibox/tv/CHANGELOG.md`. Il refuse un APK qui n'est pas signé avec ta clé.
- Sur la box, Android doit autoriser LaunchOS TV à installer des applis (`REQUEST_INSTALL_PACKAGES`), comme pour
  LaunchOS.

## Tes identifiants

| Où | Ce qui s'y trouve |
|---|---|
| Sur la box, dans LaunchOS TV | Le compte, **chiffré** avec une clé AES gardée par le Keystore d'Android (elle ne peut pas sortir de la box). Les sauvegardes Android sont désactivées pour l'appli. |
| Dans le dépôt GitHub, l'APK, le Mac | **Rien.** Même l'adresse que tu m'as donnée n'est pas dans le dépôt. |
| Dans les journaux Android | Rien de l'appli : elle n'écrit aucun journal, et la version publiée retire aussi ceux du moteur vidéo. |
| Moi | Je ne lis pas les journaux ni les écrans de cette appli sur la box, comme pour le reste de ce que vous regardez. |

Une limite qu'aucune appli ne peut lever : le protocole Xtream met l'identifiant et le mot de passe dans chaque
adresse de flux, souvent en `http` non chiffré. Ton fournisseur et ton réseau les voient.

## Comment c'est construit

- Dossier `mibox/tv/`, projet Gradle à part. Java simple comme le launcher, Media3 1.4.1 (lecture, HLS,
  interface du lecteur) et RecyclerView. APK de 1,1 Mo.
- Signé avec **la même clé que LaunchOS** (empreinte `ea3231ba…cb50e7`) : les mises à jour s'installeront
  par-dessus, et plus tard le launcher pourra lire les favoris de l'appli de façon sûre.
- Autorisations : Internet seulement. Le `http` non chiffré est autorisé parce que la plupart des fournisseurs
  n'ont que ça.
- `mibox/tv/mock/xtream_mock.py` : faux serveur Xtream sur le Mac (compte demo / demo), avec des vidéos de test
  faites par ffmpeg. Il imite les variantes des vrais serveurs : nombres envoyés en texte, épisodes en tableau ou
  en liste, redirections.
- 36 tests unitaires : réponses Xtream, adresses, guide des programmes, et le code de mise à jour.

## Ce qui a été testé

Sur l'émulateur, contre le faux serveur (détail dans `02-plan-de-tests.md`, section C decies) :

| Test | Résultat |
|---|---|
| Compte accepté, refusé, gardé après réinstallation | Test partiel (émulateur) |
| Catégories, chaînes, affiches, fiche série | Test partiel (émulateur) |
| Direct, zapping CH+, film, épisode | Test partiel (émulateur ; son non vérifié) |
| Redirections, épisodes en liste | Test partiel (émulateur) |
| Serveur arrêté : messages et nouvelles tentatives | Test partiel (émulateur) |
| Ton serveur, la box, le son, un gros catalogue | Non testé |

## La suite

1. ~~Publier la V1 et l'installer sur la box~~ : fait le 6 novembre 2026 (SHA-256 et certificat vérifiés).
2. **0.2.0 par la mise à jour automatique** : la box doit la proposer d'elle-même (test TV-UPD-02).
3. **Ton test** : tu entres ton compte et tu me dis ce qui marche (tests TV-01 à TV-16). Je ne vois rien de ce qui
   passe à l'écran ; décris-moi juste ce que tu constates.
4. **Ensuite**, selon ton retour : grille complète du guide, favoris, reprise de lecture,
   recherche, replay si ton fournisseur le propose, rangées sur l'accueil de LaunchOS, touche de la télécommande
   pour ouvrir le direct, et mises à jour par le système de LaunchOS.
