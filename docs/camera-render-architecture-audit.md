# Audit architecture ciblé — gestion des caméras et rendu

## 1) Résumé exécutif

Le runtime a une **intention multicam/FBO** claire (SOA caméra, boucle `for camIndex`, targets par caméra), mais l’implémentation effective reste **majoritairement mono-caméra** côté post-process et pilotage. Le point clé est que la complexité actuelle vient moins du SOA en lui-même que du **mélange des niveaux d’abstraction** (caméra métier, caméra renderer, ressources GL) dans une même structure et des **branchements “future-ready” partiellement branchés**.

Conclusion courte : **SOA caméra utile comme backend renderer**, mais **overkill comme modèle central exposé partout** tant que le runtime tourne en 1 caméra active dans la plupart des cas.

## 2) Distinction des responsabilités

- **Caméra concept métier / API dev** : portée par `CameraSettingsComponent` (+ `CameraFxComponent`) sur des entités ECS. C’est le bon niveau pour l’éditeur et les scripts de gameplay.
- **Caméra entrée renderer** : projection de ces composants vers un état runtime compact (`CameraStateSOA`) via `RenderCameraSyncSystem`.
- **Ressources techniques internes** : FBO/texture/depth dans `CameraRenderTargets`, plus dispatch post-process plein écran.

Le design visé est bon, mais l’implémentation actuelle lie encore trop fortement ces trois couches.

## 3) Réponse explicite aux questions

### Q1. Le SOA caméra apporte-t-il un bénéfice réel ici ?

**Bénéfice réel mais limité aujourd’hui.**

- Oui, il apporte une base propre pour itérer sur des slots caméra et brancher le renderer sans allocations (`CameraRenderTargets` indexé par caméra, `RenderSubmitSystem` par `camIndex`).
- Mais le coût dominant du pipeline est côté GPU (draw, state changes, FBO bind/clear, post-process), pas la lecture de quelques champs caméra en AoS vs SoA.
- Avec une capacité faible (`setCapacity(4)`), le gain cache/branch du SOA est mécaniquement marginal.

Donc : **utile structurellement pour le futur**, **pas un levier perf majeur au présent**.

### Q2. Remplacer le modèle principal par des caméras normales/objets normaux : impact perf ?

**Faible à négligeable** dans ce runtime actuel.

- Le submit est dominé par le parcours draw list, switches shader/blend et draw calls.
- Le coût d’accès caméra (quelques tableaux) est très faible vs coût GL.
- À 1–4 caméras, un modèle “objet caméra runtime” n’aurait vraisemblablement pas d’impact mesurable significatif.

### Q3. Quelles parties doivent absolument rester pour multicam/FBO futur ?

À conserver :

1. **Contrat “renderer consomme des caméras indexées”** (qu’il soit SOA ou façade).
2. **`CameraRenderTargets` par caméra** (lifecycle resize/create/dispose).
3. **Boucle de rendu par caméra dans `RenderSubmitSystem`**.
4. **Découplage ECS caméra → état renderer** (sync system ou équivalent).
5. **Point d’extension post-process par caméra** (chaîne de passes, ping-pong FBO).

### Q4. Quelles parties compliquent inutilement aujourd’hui ?

1. **SOA statique global (`capacity` static)** : rend l’état moins local/isolable et plus fragile en multi-instance.
2. **`PostProcessDispatchSystem` câblé cam0** alors que le reste suggère du multicam.
3. **`postFxChainId` caméra non réellement alimenté/exploité** (champ présent, pipeline de chaîne absent).
4. **`advancedRendering` gate global** : coupe des chemins entiers et crée deux modes conceptuels au lieu d’un pipeline cohérent activé par caméra.
5. **`fxRegistry` injecté mais inutilisé dans le dispatch actuel**.

### Q5. Où sont les couplages implicites mono-caméra ?

- `PostProcessDispatchSystem` traite explicitement **uniquement `cam0`** (`enabled[0]`, `getColorRegion(0)`, `entityId[0]`).
- Boot engine initialise surtout **caméra 0** (`enableCamera(0)`, `useOffscreen[0]`, ambient/layer mask sur slot 0).
- Le flag scène `mainCameraOffscreen` est mono-cam conceptuellement.
- Un seul `OrthographicCamera worldCamera` partagé (pas de projection/viewport par caméra logique dans la passe submit).

### Q6. Trajectoire de simplification recommandée

- **Conserver un backend compact pour le renderer**, mais sortir le SOA du rôle de “modèle principal”.
- Introduire une **façade `RenderCameraSet`** (itération + getters), avec implémentation SOA interne.
- Refondre `PostProcessDispatchSystem` en boucle par caméra active (pas cam0 hardcodé), même si V1 fait un simple blit.
- Garder `CameraRenderTargets` mais piloté par des descripteurs caméra clairs (offscreen/postfx requis).
- Reporter les optimisations micro-perf SOA tant que le pipeline multicam réel n’est pas actif.

## 4) Points positifs

- Intention d’architecture multicam déjà présente (loop sur `camIndex` dans submit).
- Gestion explicite du cycle de vie FBO (resize invalidation, allocation à la demande, dispose).
- Synchronisation ECS → rendu déjà séparée (`RenderCameraSyncSystem`).
- Possibilité de layer mask par caméra déjà en place côté submit.

## 5) Problèmes / risques

1. **Mismatch architecture vs exécution réelle** : design multicam, exécution postFX mono-cam.
2. **Dette de cohérence** : champs SOA (postFx chain, handles) présents mais partiellement utilisés.
3. **Risque d’erreurs futures** : extensions multicam risquent de casser car certains systèmes supposent cam0.
4. **Lisibilité/coût cognitif** : trop de concepts “futurs” non branchés augmente la complexité perçue pour les contributeurs.

## 6) Ce qui est réellement nécessaire pour le futur multicam/FBO

Minimum vital :

- interface caméra renderer stable (index + activation + mask + target policy),
- targets par caméra robustes,
- submit par caméra,
- post-process par caméra (même simple),
- séparation nette entre config métier caméra et ressources GL.

## 7) Ce qui peut être simplifié maintenant

- Cacher `CameraStateSOA` derrière une façade (`RenderCameraSet`) et éviter les accès bruts depuis tous les systèmes.
- Supprimer/neutraliser les champs non utilisés tant qu’ils ne sont pas branchés (ou les brancher vraiment).
- Unifier la logique “offscreen requis ?” dans une seule fonction (éviter duplication submit/targets/post).
- Réduire la logique conditionnelle `advancedRendering` au strict nécessaire.

## 8) Verdict explicite

> “Le SOA caméra est-il un vrai besoin, ou un overkill à ce stade ?”

**Verdict : overkill partiel à ce stade en tant que modèle central ; besoin raisonnable en tant qu’implémentation interne du renderer.**

Autrement dit : le SOA n’est pas “mauvais”, mais son exposition actuelle amplifie la complexité sans gain perf proportionnel dans le runtime actuel.

## 9) Recommandation finale

✅ **Garder le SOA mais le cacher derrière une API plus simple.**

C’est le meilleur compromis entre simplification immédiate et compatibilité future multicam/FBO.

## 10) Plan de refactor minimal, progressif

1. **Étape 1 — Façade caméra renderer**
   - Créer `RenderCameraSet` (itération active, getters/setters ciblés).
   - Remplacer les accès directs aux tableaux dans les systèmes critiques.
2. **Étape 2 — Dé-hardcoder cam0 dans post-process**
   - Boucler sur caméras actives.
   - Blit/FX par caméra avec politique explicite de composition.
3. **Étape 3 — Politique offscreen unifiée**
   - Extraire `needsOffscreen(cam)` partagé submit/targets/post.
4. **Étape 4 — Nettoyage des champs et flags**
   - Brancher réellement `postFxChainId` + `fxRegistry` ou retirer temporairement.
   - Réduire `advancedRendering` à un rôle d’activation pipeline global sans divergence de modèle.
5. **Étape 5 — Préparation multicam réelle**
   - Support viewport/projection par caméra renderer.
   - Définir mode composition final (split screen, overlay, ordre de caméras).
