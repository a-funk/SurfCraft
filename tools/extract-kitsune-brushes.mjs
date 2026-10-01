// Extracts the surf_kitsune collision brushes around the CS:S reference captures so the Java physics
// can replay them. Reads the user's own map through the surf repo's BSP reader and writes to
// local-content/ (gitignored: map geometry is third-party content and is never committed).
//   node tools/extract-kitsune-brushes.mjs [/path/to/surf/repo]
import { createHash } from 'node:crypto';
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const surf = resolve(process.argv[2] ?? join(import.meta.dirname, '../../surf'));
const { readSourceBsp, bspBrushVertices, SourceContents } = await import(pathToFileURL(join(surf, 'lib/index.js')).href);
const bytes = readFileSync(join(surf, 'local-content/maps/surf_kitsune.bsp'));
const sha = createHash('sha256').update(bytes).digest('hex');
if (sha !== '1c0d3c7b3c08581180da3efc4512068b03023340ffd744eb0ab86c7ec214e77f') throw new Error(`unexpected surf_kitsune.bsp ${sha}`);
const bsp = readSourceBsp(bytes);

// Capture trajectories (Source units) padded by the 32x32x62 hull plus one tick of travel.
const regions = {
  ramp: { min: [7600, -3800, 3800], max: [8200, -2300, 4700] },
  wall: { min: [300, -1400, 0], max: [700, -400, 400] },
};
const overlaps = (aMin, aMax, bMin, bMax) => aMin.every((v, i) => v <= bMax[i] && aMax[i] >= bMin[i]);
const bounds = brush => {
  const v = bspBrushVertices(bsp, brush), min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  // bspBrushVertices returns Three.js metres (x, y-up, -z); convert back to Source units.
  for (let i = 0; i < v.length; i += 3) {
    const p = [v[i] / 0.0254, -v[i + 2] / 0.0254, v[i + 1] / 0.0254];
    for (let k = 0; k < 3; k++) { min[k] = Math.min(min[k], p[k]); max[k] = Math.max(max[k], p[k]); }
  }
  return { min, max };
};
const out = join(import.meta.dirname, '../local-content/css-reference');
mkdirSync(out, { recursive: true });
for (const [name, region] of Object.entries(regions)) {
  const brushes = [], warnings = [], entityBrushes = [];
  for (const index of bsp.models[0].brushes) {
    const brush = bsp.brushes[index];
    const solid = brush.contents & (SourceContents.Solid | SourceContents.Window | SourceContents.Grate | SourceContents.Moveable);
    if (!solid && !(brush.contents & SourceContents.PlayerClip)) continue;
    const b = bounds(brush);
    if (!overlaps(b.min, b.max, region.min, region.max)) continue;
    brushes.push({ index, contents: brush.contents, planes: brush.sides.map(s => {
      const p = bsp.planes[s.plane]; return [p.normal.x, p.normal.y, p.normal.z, p.distance];
    }) });
  }
  for (const d of bsp.displacements) {
    const v = d.vertices; let hit = false;
    for (let i = 0; i < v.length && !hit; i += 3) hit = overlaps([v[i] / 0.0254, -v[i + 2] / 0.0254, v[i + 1] / 0.0254], [v[i] / 0.0254, -v[i + 2] / 0.0254, v[i + 1] / 0.0254], region.min, region.max);
    if (hit) warnings.push(`displacement ${d.index} intersects the region`);
  }
  for (const entity of bsp.entities) {
    const get = k => entity.find(e => e.key === k)?.value;
    const model = get('model'), cls = get('classname');
    if (!model?.startsWith('*')) continue;
    const m = bsp.models[Number(model.slice(1))], origin = (get('origin') ?? '0 0 0').split(/\s+/).map(Number);
    const min = [m.min.x + origin[0], m.min.y + origin[1], m.min.z + origin[2]], max = [m.max.x + origin[0], m.max.y + origin[1], m.max.z + origin[2]];
    if (!overlaps(min, max, region.min, region.max)) continue;
    warnings.push(`${cls} ${model} intersects the region`);
    // Raw brush planes, moved to the entity origin. A func_brush with solidbsp=0 really collides through its
    // compiled VPhysics hull, which can differ from these planes by about half a unit.
    if (cls === 'func_brush') entityBrushes.push({ classname: cls, model, solidbsp: get('solidbsp'), brushes: m.brushes.map(index => ({ index,
      contents: bsp.brushes[index].contents, planes: bsp.brushes[index].sides.map(s => {
        const p = bsp.planes[s.plane]; return [p.normal.x, p.normal.y, p.normal.z, p.distance + p.normal.x * origin[0] + p.normal.y * origin[1] + p.normal.z * origin[2]];
      }) })) });
  }
  for (const p of bsp.staticProps) if (overlaps([p.origin.x, p.origin.y, p.origin.z], [p.origin.x, p.origin.y, p.origin.z], region.min, region.max)) warnings.push(`static prop ${p.model} at the region`);
  writeFileSync(join(out, `kitsune-${name}.json`), JSON.stringify({ map: 'surf_kitsune.bsp', sha256: sha, region, warnings, brushes, entityBrushes }));
  console.log(`${name}: ${brushes.length} brushes; ${warnings.length ? warnings.join('; ') : 'no entities, displacements or props'}`);
}
