import Ajv2020 from 'ajv/dist/2020.js';
import { readFileSync, existsSync, readdirSync } from 'node:fs';
import { resolve, join, dirname, basename, extname } from 'node:path';
import { fileURLToPath } from 'node:url';

const schemaRoot = dirname(fileURLToPath(import.meta.url));
const base = 'https://pixscape.games/schemas/pixscape/';
const ajv = new Ajv2020({ allErrors: true, strict: false });
const schemas = new Map();
const failures = [];
let checked = 0;

function walk(dir) {
  for (const item of readdirSync(dir, { withFileTypes: true })) {
    if (item.isDirectory()) {
      if (!['node_modules', '.npm-cache'].includes(item.name)) walk(join(dir, item.name));
    } else if (item.name.endsWith('.schema.json')) {
      const path = join(dir, item.name);
      const schema = JSON.parse(readFileSync(path, 'utf8'));
      const relative = path.slice(schemaRoot.length + 1).replaceAll('\\', '/');
      if (schema.$id !== base + relative) throw new Error(`${relative}: $id must match public path`);
      if (!ajv.validateSchema(schema)) throw new Error(`${relative}: invalid schema ${ajv.errorsText()}`);
      schemas.set(relative, schema);
      ajv.addSchema(schema);
    }
  }
}
walk(schemaRoot);
for (const name of schemas.keys()) ajv.getSchema(base + name); // compile and resolve all refs locally

function error(where, message) { failures.push(`${where}: ${message}`); }
function readJson(path) {
  try { return JSON.parse(readFileSync(path, 'utf8')); }
  catch (e) { error(path, `invalid or missing JSON: ${e.message}`); return null; }
}
function check(name, value, where) {
  if (value === null) return;
  const valid = ajv.getSchema(base + name);
  if (!valid(value)) error(where, ajv.errorsText(valid.errors, { separator: '; ' }));
  checked++;
}
function requiredFile(path, why) {
  if (!existsSync(path)) error(path, `missing ${why}`);
  return existsSync(path);
}
function safe(root, relative) {
  const result = resolve(root, relative);
  if (!(result === resolve(root) || result.startsWith(resolve(root) + '\\') || result.startsWith(resolve(root) + '/'))) {
    error(relative, 'path escapes export root');
    return null;
  }
  return result;
}
function singleAssetName(value, directory, extension) {
  if (typeof value !== 'string') return null;
  let name = value.trim().replaceAll('\\', '/');
  if (name.startsWith(directory + '/')) name = name.slice(directory.length + 1);
  if (!name || name.includes('/') || name.includes(':') || name === '..') return null;
  if (!name.endsWith(extension)) name += extension;
  return name;
}
function atlasPages(path) {
  if (!requiredFile(path, 'atlas descriptor')) return new Set();
  const lines = readFileSync(path, 'utf8').split(/\r?\n/);
  const ids = new Set();
  // TextureAtlas page headers are filename lines immediately followed by `size:`.
  for (let i = 0; i + 1 < lines.length; i++) {
    if (/^[^\s:][^:]*$/.test(lines[i]) && /^size:\s*\d+\s*,\s*\d+/.test(lines[i + 1].trim())) {
      requiredFile(join(dirname(path), lines[i].trim()), 'atlas page');
    }
    const region = lines[i].trim().match(/__a([0-9]+)$/);
    if (region) ids.add(Number(region[1]));
  }
  return ids;
}
function validateScene(scene, path) {
  check('scene/3.schema.json', scene, path);
  const dependencies = { assetIds: new Set(), effects: new Set() };
  if (!scene?.componentIdentifiers || !scene?.archetypes || !scene?.entities) return dependencies;
  const componentTypes = schemas.get('components/3.schema.json');
  const byAlias = new Map();
  for (const [fqcn, alias] of Object.entries(scene.componentIdentifiers)) {
    if (byAlias.has(alias)) error(path, `duplicate Artemis alias ${alias}`);
    byAlias.set(alias, fqcn);
  }
  for (const [id, aliases] of Object.entries(scene.archetypes)) {
    if (!Array.isArray(aliases)) continue;
    if (new Set(aliases).size !== aliases.length) error(path, `archetype ${id} repeats an alias`);
    for (const alias of aliases) {
      if (!byAlias.has(alias)) error(path, `archetype ${id} has unresolved alias ${alias}`);
    }
  }
  for (const [id, entity] of Object.entries(scene.entities)) {
    const where = `${path} entity ${id}`;
    const inherited = scene.archetypes[String(entity.archetype)];
    if (!Array.isArray(inherited)) { error(where, `missing archetype ${entity.archetype}`); continue; }
    const effective = new Set(inherited);
    for (const alias of Object.keys(entity.components || {})) effective.add(alias);
    const names = new Set();
    for (const alias of effective) {
      const fqcn = byAlias.get(alias);
      if (!fqcn) { error(where, `unresolved Artemis alias ${alias}`); continue; }
      const name = componentTypes['x-pixscape-components'][fqcn];
      if (!name) { error(where, `unsupported exported component type ${fqcn}`); continue; }
      names.add(name);
      if (Object.hasOwn(entity.components || {}, alias)) {
        const value = entity.components[alias];
        check('components/3.schema.json#/$defs/' + name, value, `${where} ${fqcn}`);
        if (name === 'AssetRefComponent' && value?.assetId > 0) dependencies.assetIds.add(value.assetId);
        if (name === 'TiledLayerComponent') {
          const tiles = value?.tileAssetIds;
          for (const id of (tiles?.items || []).slice(0, tiles?.size || 0)) {
            if (id > 0) dependencies.assetIds.add(id);
          }
        }
        if (name === 'ParticleEmitterComponent' && value?.effectPath) dependencies.effects.add(value.effectPath);
      }
    }
    if (names.has('LayerComponent') && names.has('EntityIndexComponent')) {
      error(where, 'LayerComponent and EntityIndexComponent coexist (including archetype-only components)');
    }
  }
  return dependencies;
}
function validateGameObject(asset, path) {
  check('game-object/3.schema.json', asset, path);
  if (!Array.isArray(asset?.entities)) return;
  const ids = new Set();
  let roots = 0;
  for (const entity of asset.entities) {
    const id = entity.sourceEntityId ?? 0;
    if (ids.has(id)) error(path, `duplicate Game Object sourceEntityId ${id}`);
    ids.add(id);
    if ((entity.parentSourceEntityId ?? -1) === -1) roots++;
    if (Object.hasOwn(entity.entityIndex || {}, 'layerIndex')) error(path, 'Game Object definition contains entityIndex.layerIndex');
    if (Object.hasOwn(entity, 'LayerComponent')) error(path, 'Game Object definition contains LayerComponent');
  }
  const root = asset.entities.find(entity => (entity.sourceEntityId ?? 0) === asset.rootSourceEntityId);
  if (!root || roots !== 1 || (root.parentSourceEntityId ?? -1) !== -1 || root.gameObject == null) {
    error(path, 'definition must have one marked root matching rootSourceEntityId');
  } else if ((root.transform?.x ?? 0) !== 0 || (root.transform?.y ?? 0) !== 0 || (root.entityIndex?.zIndex ?? 0) !== 0) {
    error(path, 'definition root must have x=y=0 and local zIndex=0');
  }
  for (const entity of asset.entities) {
    const parent = entity.parentSourceEntityId ?? -1;
    if (parent !== -1 && !ids.has(parent)) {
      error(path, `missing parentSourceEntityId ${parent}`);
    }
  }
}
function validateExport(root) {
  const projectPath = join(root, 'project.json');
  const project = readJson(projectPath);
  check('project/1.schema.json', project, projectPath);
  if (!project?.scenes) return;
  const optional = [
    ['animations.json', 'animations/unversioned.schema.json'],
    ['tiled-animations.json', 'tiled-animations/1.schema.json'],
    ['tileset-profiles.json', 'tileset-profiles/1.schema.json'],
  ];
  for (const [file, schema] of optional) {
    const path = join(root, file);
    if (existsSync(path)) check(schema, readJson(path), path);
  }
  const sceneDir = project.scenesDir || 'scenes';
  const atlasDir = project.atlasesDir || 'atlases';
  for (const [key, meta] of Object.entries(project.scenes)) {
    if (!meta?.file || !meta.file.trim()) { error(key, 'scene file is missing'); continue; }
    const normalizedFile = basename(meta.file.replaceAll('\\', '/'));
    const scenePath = safe(root, join(sceneDir, normalizedFile));
    if (!scenePath) continue;
    const dependencies = validateScene(readJson(scenePath), scenePath);
    const tag = basename(normalizedFile, extname(normalizedFile));
    const atlas = safe(root, join(atlasDir, tag + '.atlas'));
    const regions = atlas ? atlasPages(atlas) : new Set();
    const available = meta.runtimeAvailability || {};
    for (const id of [...(available.sprites || []), ...(available.tiledTiles || [])]) {
      if (id > 0) dependencies.assetIds.add(id);
    }
    for (const id of available.gameObjects || []) {
      const name = singleAssetName(id, 'gameobjects', '.gameobject');
      if (!name) { error(key, `invalid Game Object ID ${id}`); continue; }
      const file = safe(root, join(project.gameObjectsDir || 'gameobjects', name));
      if (file) {
        const asset = readJson(file);
        validateGameObject(asset, file);
        for (const entity of asset?.entities || []) {
          if (entity.assetRef?.assetId > 0) dependencies.assetIds.add(entity.assetRef.assetId);
        }
      }
    }
    for (const id of dependencies.assetIds) {
      if (!regions.has(id)) error(scenePath, `asset ID ${id} has no region in ${atlas}`);
    }
    for (const effect of new Set([...(available.particles || []), ...dependencies.effects])) {
      const file = safe(root, join(project.effectsDir || 'effects', effect));
      if (file) requiredFile(file, 'declared particle effect');
    }
    if (meta.defaultHudScreenId) {
      const name = singleAssetName(meta.defaultHudScreenId, 'hud', '.hudscreen');
      if (!name) { error(key, `invalid HUD screen ID ${meta.defaultHudScreenId}`); continue; }
      const screenPath = safe(root, join('hud', name));
      if (!screenPath) continue;
      const screen = readJson(screenPath);
      check('hud-screen/1.schema.json', screen, screenPath);
      if (screen?.documentId) {
        const documentPath = safe(root, screen.documentId);
        if (documentPath) check('hud-document/2.schema.json', readJson(documentPath), documentPath);
      }
      const hudAtlas = safe(root, join(atlasDir, 'hud', tag, 'hud.atlas'));
      if (hudAtlas) atlasPages(hudAtlas);
    }
  }
  const gameObjectsDir = safe(root, project.gameObjectsDir || 'gameobjects');
  if (gameObjectsDir && existsSync(gameObjectsDir)) {
    for (const name of readdirSync(gameObjectsDir).filter(name => name.endsWith('.gameobject'))) {
      const file = join(gameObjectsDir, name);
      validateGameObject(readJson(file), file);
    }
  }
}

function invalidSelfTest() {
  let cases = 0;
  function mustReject(label, run) {
    const before = failures.length;
    run();
    if (failures.length === before) throw new Error(`Invalid fixture was accepted: ${label}`);
    failures.length = before;
    cases++;
  }
  mustReject('wrong project version', () => check('project/1.schema.json',
    { projectFileName: 'x', version: '2', scenes: {} }, 'invalid project'));
  mustReject('obsolete Layer.type', () => check('components/3.schema.json#/$defs/LayerComponent',
    { type: 0 }, 'invalid Layer'));
  mustReject('null primitive transform scale', () => check('components/3.schema.json#/$defs/TransformComponent',
    { scaleX: null }, 'invalid transform'));
  mustReject('NaN outside physics parallax', () => check('components/3.schema.json#/$defs/TransformComponent',
    { x: 'NaN' }, 'invalid transform'));
  mustReject('IntArray encoded as a plain array', () => check('components/3.schema.json#/$defs/AnimationComponent',
    { animationAssetIds: [1] }, 'invalid animation'));
  mustReject('unresolved alias', () => validateScene({ metadata: { version: 1 },
    componentIdentifiers: {}, archetypes: { 1: ['missing'] },
    entities: { 0: { archetype: 1, components: {} } } }, 'invalid scene'));
  const identifiers = {
    'games.pixscape.runtime.component.LayerComponent': 'layer',
    'games.pixscape.runtime.component.EntityIndexComponent': 'index',
  };
  mustReject('archetype-only Layer/content conflict', () => validateScene({
    metadata: { version: 1 }, componentIdentifiers: identifiers,
    archetypes: { 1: ['layer', 'index'] }, entities: { 0: { archetype: 1, components: {} } },
  }, 'invalid scene'));
  mustReject('Game Object root placement', () => validateGameObject({ schemaVersion: 3,
    rootSourceEntityId: 1, entities: [{ sourceEntityId: 1, parentSourceEntityId: -1,
      transform: { x: 5, y: 0, scaleX: 1, scaleY: 1 }, entityIndex: { zIndex: 0 },
      gameObject: {} }] }, 'invalid Game Object'));
  console.log(`Rejected ${cases} representative invalid fixtures.`);
}

const args = process.argv.slice(2);
if (args.includes('--self-test')) invalidSelfTest();
else for (const raw of args) validateExport(resolve(raw));
if (args.length === 0) {
  console.log(`Compiled ${schemas.size} Draft 2020-12 schemas with local references.`);
} else if (!args.includes('--self-test')) {
  console.log(`Compiled ${schemas.size} schemas; validated ${checked} documents and component payloads.`);
}
if (failures.length) {
  for (const failure of failures) console.error(failure);
  process.exitCode = 1;
}
