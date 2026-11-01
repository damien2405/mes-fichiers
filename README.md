# Mes fichiers+

Gestionnaire de fichiers Android inspiré de « Mes fichiers » de Samsung, avec un client FTP/FTPS
intégré : navigation dans les dossiers du serveur, miniatures et visionneuse plein écran pour les
PNG / JPEG (et WebP, GIF, BMP, HEIC).

## Fonctions

- Accueil façon Mes fichiers : catégories (Téléchargements, Docs, Images, Audio, Vidéos, Fichiers d'installation), stockage interne, serveurs réseau
- Explorateur du stockage interne : tri, recherche, sélection multiple, partager, renommer, supprimer
- Serveurs FTP / FTPS (explicite ou implicite), mode passif ou actif, mot de passe chiffré dans l'Android Keystore
- Explorateur FTP : miniatures, visionneuse avec zoom et balayage, téléchargement vers « Téléchargements », renommer, supprimer
- Cache disque des images déjà vues (600 Mo maximum, nettoyage automatique)

## Installer l'APK

Chaque push sur `main` lance le workflow *Build APK* (GitHub Actions) qui publie une release
avec `MesFichiers.apk`. Ouvrir la dernière release depuis le téléphone, télécharger l'APK et l'installer.

## Premier lancement

1. Ouvrir l'appli, toucher « Espace de stockage réseau » puis `+`
2. Renseigner adresse, port, identifiants, puis « Tester la connexion »
3. Pour parcourir le stockage du téléphone, accorder « Accès à tous les fichiers » quand l'appli le demande

## Compiler en local

```
./gradlew assembleRelease
```

L'APK est signé avec `app/mesfichiers.jks` (clé personnelle, mot de passe dans `app/build.gradle.kts`).
Si le dépôt devient public, déplacer cette clé dans les secrets GitHub.
