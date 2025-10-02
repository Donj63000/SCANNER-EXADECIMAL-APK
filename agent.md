# agent.md — Contrat d’agent (Codex) pour *Photo Clarity*

> Objectif produit  
> Appli Android professionnelle qui permet de **prendre 2 photos** ou d’en **sélectionner 2**, puis d’indiquer **quelle photo est la plus nette** et **à combien de %**. Focus sur simplicité, fiabilité et rapidité.

---

## 1) Périmètre & Contraintes (incontournables)

- **Stack** : Kotlin, Jetpack **Compose (Material3)**, **CameraX**.  
- **Algorithmes** : mesurer la netteté via **Variance du Laplacien** (principal) + **Tenengrad/Sobel** (fallback).  
- **OS** : `minSdk=26`, `targetSdk=36`, `compileSdk=36`.  
- **Build** : AGP `8.13.0`, Gradle `8.13`, JDK `17`.  
- **Outils autorisés** : **GitHub** (repo + PR) et **CLI Gradle**. **Aucun IDE**.  
- **Réseau** : aucune dépendance réseau pour la comparaison.  
- **Langue UI** : FR.

> Ne **modifie pas** les versions majeures ci‑dessus sans instruction explicite.  
> Tous les commits doivent faire passer **build + lint + tests**.

---

## 2) Architecture du dépôt

```

photo-clarity/
├─ app/                         # application Android (Compose + CameraX)
│  └─ src/main/java/com/photo/clarity/
│     ├─ MainActivity.kt
│     ├─ ui/CompareScreen.kt
│     └─ camera/CameraCapture.kt
├─ imagequality/                # module pur Kotlin pour les algos de netteté
│  └─ src/main/java/com/photo/clarity/iq/Clarity.kt
├─ .github/workflows/android-ci.yml
├─ build.gradle.kts / settings.gradle.kts
├─ README.md
└─ agent.md                     # CE DOCUMENT

```

---

## 3) Définition de l’Interface (UI v0) — **obligatoire pour M1**

**Écran unique : “Comparer la clarté de deux photos”**

```

[Titre] Comparer la clarté de deux photos

[Carte Photo A]            [Carte Photo B]
┌─────────────────────┐    ┌─────────────────────┐
│ (Aucune image)      │    │ (Aucune image)      │
└─────────────────────┘    └─────────────────────┘
[Prendre]  [Effacer]        [Prendre]  [Effacer]

[Comparer]  (désactivé tant que A ou B manquante)

[Résultat]

* Si calculé : “A est plus nette à 62% (B 38%)”
* Afficher aussi un badge sous chaque vignette : “A 62% / B 38%”

````

**Règles UX :**
- Le bouton **Comparer** n’est activé que si **A et B** existent.  
- En capture, demander la permission **CAMERA** si nécessaire.  
- Les actions **Prendre** ouvrent un aperçu CameraX (caméra arrière) et font un **save** en JPEG dans `Pictures/Clarity`.  
- Les actions **Effacer** remettent l’emplacement à vide et masquent le résultat.  
- Tous les libellés sont en **français**.  
- Pas d’animations complexes pour M1 ; priorité à la lisibilité.

**Accessibilité :**
- `contentDescription` sur les images.  
- Contrastes par défaut Material3 ; tailles de police adaptatives.

---

## 4) Spécification algorithmes (module `imagequality`)

- **Entrée** : deux bitmaps (A, B).  
- **Pré‑traitement** : downscale côté appli (≈ max 1024 px de large), conversion **ARGB → niveaux de gris** (pondération luma).  
- **Score principal** : **Variance du Laplacien** 3×3 (5 points).  
- **Fallback** : **Tenengrad** (Sobel), utilisé si les deux scores Laplacien sont quasi nuls.  
- **Pourcentage** : **normalisation relative** (A,B) telle que `pA + pB = 100`.  
- **Sortie UI** : un texte clair + deux badges sous A et B.

> Le module `imagequality` est **pur Kotlin** (pas d’Android SDK), testé unitairement.

---

## 5) Définition du Fini (Definition of Done)

1. **Build** OK : `./gradlew :app:assembleDebug`.  
2. **Lint** OK : `./gradlew :app:lintDebug`.  
3. **Tests** OK :  
   - Unit tests sur `imagequality` : au moins 3 cas (image nette vs floue, motifs synthétiques, égalité).  
4. **Comportement** :
   - Permission CAMERA demandée au premier besoin.  
   - Capture A et B : les vignettes se mettent à jour.  
   - Bouton **Comparer** produit un **résultat en %** stable et compréhensible.  
5. **CI** GitHub passe (workflow `android-ci.yml`).  
6. **Docs** : `README.md` et `agent.md` à jour si des choix techniques évoluent.

---

## 6) Tâches initiales (Backlog M1)

- [ ] Implémenter l’UI v0 (Compose/Material3), y compris badges % sous A et B.  
- [ ] Intégrer CameraX (aperçu + capture JPEG, caméra arrière).  
- [ ] Implémenter `Clarity.varianceOfLaplacian(...)`, `Clarity.tenengrad(...)`, `relativePercentages(...)`.  
- [ ] Câbler le pipeline : downscale → gray → scores → % → rendu.  
- [ ] Écrire **tests unitaires** (module `imagequality`) avec images synthétiques.  
- [ ] Mettre en place messages d’erreur utilisateur simples (ex: “Capture échouée”).  
- [ ] Vérifier performance (comparaison < 200 ms sur images 1024 px).  
- [ ] Passer **lint** et **CI**.

---

## 7) Commandes (sans IDE)

```bash
# Build Debug
./gradlew :app:assembleDebug

# Lint
./gradlew :app:lintDebug

# Tests unitaires (module algos)
./gradlew :imagequality:testDebugUnitTest
````

> En local, si nécessaire : `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

---

## 8) Règles de code & style

* **Kotlin** : null‑safety stricte, immutabilité par défaut, éviter l’alloc dans les boucles chaudes.
* **Compose** : états via `remember`/`mutableStateOf`, remonter l’état au plus haut nécessaire, UI **stateless** quand possible.
* **CameraX** : lier/délier les use cases correctement, privilégier `CAPTURE_MODE_MINIMIZE_LATENCY`.
* **Nommer** : `com.photo.clarity.*` ; fichiers en PascalCase ; fonctions pures dans `imagequality`.
* **Erreurs** : catch ciblés, message utilisateur clair, logs minimalistes.
* **I18n** : tous les strings dans `strings.xml` (prochaine itération).

---

## 9) Sécurité, vie privée, perf

* Aucune donnée envoyée en ligne.
* Les images restent sur l’appareil (dossier `Pictures/Clarity`).
* Downscale avant calculs pour limiter la mémoire ; pas de copies inutiles de buffers.

---

## 10) Git & PR

* **Branche** : `feat/…`, `fix/…`, `chore/…`.
* **Conventional Commits** recommendé (`feat:`, `fix:`, `docs:`, etc.).
* Chaque PR doit prouver : build + lint + tests **verts**.
* Décrire **ce qui change** et **pourquoi** ; joindre captures d’écran si UI.

---

## 11) Pièges connus

* OOM sur grands bitmaps : **toujours** downscaler et réutiliser des buffers si possible.
* Variance du Laplacien proche de 0 sur scènes uniformes : basculer vers **Tenengrad**.
* Permissions : gérer le refus (griser “Prendre”).
* Différences d’orientation EXIF : afficher correctement les vignettes.

---

## 12) Checklists

**Avant de merger :**

* [ ] Build, Lint, Tests OK localement.
* [ ] Résultat lisible (ex: “A 62% / B 38%”).
* [ ] Aucun crash si permission refusée.
* [ ] Mémoire stable (pas de fuite visible en capture multiple).

**Qualité algo :**

* [ ] Cas “image floue vs nette” ⇒ nette > 55%.
* [ ] Cas “égales” ⇒ 45–55% pour chaque (tolérance).
* [ ] Cas “bruit” ⇒ fallback Tenengrad cohérent.

---

## 13) Communication & MAJ de ce fichier

* Si une décision technique évolue, **mettre à jour `agent.md`** dans la même PR.
* Toute ambiguïté : **ouvrir une issue** avec proposition et impact.

```

---

### Résumé pour Codex
1) Crée `agent.md` avec le contenu ci‑dessus.  
2) Commit : `docs: add agent.md`.  
3) Respecte **strictement** ce contrat lors des prochaines PR (UI v0, CameraX, algos, tests, CI).

Si tu veux, je te fournis aussi les **templates d’issue et de PR** adaptés à ce contrat.
```
