// Renders the wiki item pictures listed in design/wiki/icons/jobs.json (written by wiki/scripts/icons.mjs jobs).
// Run inside Blockbench (desktop) with an offscreen view named "icon" (1024 x 1024) from the Blockbench MCP:
//   eval(require('fs').readFileSync('<repo>/tools/blockbench/wiki_icons.js', 'utf8'));
//   await __aflWikiIcons.run({ from: 0, count: 10 })
// Every job opens its model in a fresh project (closing the others without saving), points an orthographic camera
// along the Minecraft item display rotation, fits the visible geometry and writes a transparent PNG to job.out.
// raw/manifest.json records the source signature of each render so icons.mjs can tell stale renders apart.
(function () {
  const fs = require('fs'), path = require('path')
  const REPO = 'D:/Minecraft Modding/Apocalypse First Light/'
  const JOBS = REPO + 'design/wiki/icons/jobs.json'
  const MANIFEST = REPO + 'design/wiki/icons/raw/manifest.json'
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms))
  const lib = {}

  lib.preview = () => Preview.all.find((p) => p.id === 'mcp_offscreen_icon')
  lib.closeAll = async () => { for (const p of [...ModelProject.all]) { p.saved = true; await p.close(true) } }
  lib.editMode = () => { if (Modes.options.edit && !Modes.edit) Modes.options.edit.select() }

  // Java models: load as if the file sat in models/block/, then point relative texture paths at the assets folder.
  lib.loadJava = async (assets, json) => {
    await lib.closeAll()
    loadModelFile({ path: assets + '/models/block/__wiki_icon.json', content: JSON.stringify(json), name: '__wiki_icon.json' })
    await sleep(400)
    for (const t of Texture.all) {
      const p = (t.path || '').split('\\').join('/')
      if (p && !/^[A-Za-z]:\//.test(p)) t.fromPath(assets + '/' + p)
    }
    await sleep(400)
    lib.editMode()
  }

  lib.loadBB = async (file) => {
    await lib.closeAll()
    loadModelFile({ path: file, content: fs.readFileSync(file, 'utf8'), name: path.basename(file) })
    await sleep(1200)
    lib.editMode()
  }

  // forge:obj: OBJ in block units (x16 to pixels), one Blockbench mesh per OBJ object, textures from the .mtl.
  lib.loadObj = async (job) => {
    await lib.closeAll()
    newProject(Formats.free)
    await sleep(200)
    const texByMtl = {}, texByFile = {}
    for (const [mtl, file] of Object.entries(job.textures || {})) {
      if (!file) continue
      texByMtl[mtl] = texByFile[file] || (texByFile[file] = new Texture().fromPath(file).add(false))
    }
    await sleep(500)
    const first = Object.values(texByMtl)[0]
    const V = [], VT = []
    let mesh = null, verts = null, cur = first
    const meshes = []
    const flush = () => { if (mesh) meshes.push(mesh) }
    const start = (name) => { flush(); mesh = new Mesh({ name: name || 'part', vertices: {} }); verts = {} }
    for (const raw of fs.readFileSync(job.file, 'utf8').split(/\r?\n/)) {
      const line = raw.trim()
      if (!line || line[0] === '#') continue
      const [k, ...a] = line.split(/\s+/)
      if (k === 'v') V.push(a.map(Number))
      else if (k === 'vt') VT.push(a.map(Number))
      else if (k === 'o' || k === 'g') start(a.join(' '))
      else if (k === 'usemtl') cur = texByMtl[a.join(' ')] || texByMtl['*'] || first
      else if (k === 'f') {
        if (!mesh) start('part')
        const pts = a.map((s) => s.split('/').map((n) => parseInt(n, 10)))
        const tex = cur
        const uvW = (tex && tex.uv_width) || Project.texture_width, uvH = (tex && tex.uv_height) || Project.texture_height
        const key = (vi) => {
          if (verts[vi] === undefined) { const p = V[vi - 1]; verts[vi] = mesh.addVertices([p[0] * 16, p[1] * 16, p[2] * 16])[0] }
          return verts[vi]
        }
        const poly = pts.map(([vi, ti]) => {
          const k2 = key(vi)
          const t = VT[ti - 1] || [0, 0]
          const v = job.flipV ? 1 - t[1] : t[1]
          return [k2, [t[0] * uvW, v * uvH]]
        })
        // Blockbench faces take 3 or 4 vertices: fan larger polygons into triangles
        const groups = poly.length <= 4 ? [poly] : poly.slice(1, -1).map((_, i) => [poly[0], poly[i + 1], poly[i + 2]])
        for (const g of groups) {
          const uv = {}
          for (const [k2, u] of g) uv[k2] = u
          mesh.addFaces(new MeshFace(mesh, { vertices: g.map((x) => x[0]), uv, texture: tex ? tex.uuid : false }))
        }
      }
    }
    flush()
    for (const m of meshes) m.init()
    Canvas.updateAll()
    await sleep(300)
    lib.editMode()
  }

  // Flat item sprite: one 1 px deep cube per opaque pixel, as item/generated looks in the hand.
  lib.loadExtrude = async (assets, job) => {
    const img = new Image()
    img.src = 'data:image/png;base64,' + fs.readFileSync(job.texture).toString('base64')
    await img.decode()
    const w = img.width, h = Math.min(img.height, img.width) // animated sprites: first frame
    const c = document.createElement('canvas'); c.width = w; c.height = h
    const g = c.getContext('2d'); g.drawImage(img, 0, 0)
    const d = g.getImageData(0, 0, w, h).data
    const s = 16 / w
    const ref = 'apocalypse_firstlight:' + path.relative(assets + '/textures', job.texture).split('\\').join('/').replace(/\.png$/, '')
    const elements = []
    for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
      if (d[(y * w + x) * 4 + 3] < 128) continue
      const uv = [x * s, y * s, (x + 1) * s, (y + 1) * s]
      const f = (u) => ({ uv: u, texture: '#l' })
      elements.push({ from: [x * s, 16 - (y + 1) * s, 7.5], to: [(x + 1) * s, 16 - y * s, 8.5],
        faces: { north: f([uv[2], uv[1], uv[0], uv[3]]), south: f(uv), east: f(uv), west: f(uv), up: f(uv), down: f(uv) } })
    }
    await lib.loadJava(assets, { textures: { l: ref, particle: ref }, elements })
  }

  lib.hide = (re) => {
    if (!re) return 0
    const rx = new RegExp(re, 'i'); let n = 0
    for (const gr of Group.all) if (rx.test(gr.name)) { gr.visibility = false; n++ }
    for (const e of Outliner.elements) if (rx.test(e.name)) { e.visibility = false; n++ }
    Canvas.updateVisibility()
    return n
  }

  const hiddenByParent = (e) => { let p = e.parent; while (p && p !== 'root') { if (p.visibility === false) return true; p = p.parent } return false }
  lib.visibleBox = () => {
    const box = new THREE.Box3()
    for (const e of Outliner.elements) {
      if (!e.mesh || e.visibility === false || hiddenByParent(e)) continue
      e.mesh.updateMatrixWorld(true)
      box.expandByObject(e.mesh)
    }
    return box
  }

  // Minecraft item display: model rotated by rot (XYZ degrees) and seen along -Z. The camera turns by the inverse.
  lib.shoot = (out, rot, fill) => {
    const p = lib.preview()
    if (!p) throw new Error('no offscreen view "icon"; create it with the MCP first')
    if (!p.isOrtho || p.camera !== p.camOrtho) p.setProjectionMode(true)
    if (typeof unselectAllElements === 'function') unselectAllElements()
    if (typeof updateSelection === 'function') updateSelection()
    const D = Math.PI / 180
    const q = new THREE.Quaternion().setFromEuler(new THREE.Euler(rot[0] * D, rot[1] * D, rot[2] * D, 'XYZ')).invert()
    const dir = new THREE.Vector3(0, 0, 1).applyQuaternion(q)
    const up = new THREE.Vector3(0, 1, 0).applyQuaternion(q)
    const right = new THREE.Vector3().crossVectors(up, dir).normalize()
    const box = lib.visibleBox()
    if (box.isEmpty()) throw new Error('nothing visible')
    const c = box.getCenter(new THREE.Vector3())
    let w = 0, h = 0
    for (let i = 0; i < 8; i++) {
      const v = new THREE.Vector3(i & 1 ? box.max.x : box.min.x, i & 2 ? box.max.y : box.min.y, i & 4 ? box.max.z : box.min.z).sub(c)
      w = Math.max(w, Math.abs(v.dot(right))); h = Math.max(h, Math.abs(v.dot(up)))
    }
    const cam = p.camOrtho
    const dist = box.getSize(new THREE.Vector3()).length() * 2 + 50
    cam.position.copy(c).addScaledVector(dir, dist)
    cam.up.copy(up)
    if (p.controls) p.controls.target.copy(c)
    cam.near = 0.1; cam.far = dist * 4
    cam.zoom = 1; cam.updateProjectionMatrix()
    cam.zoom = Math.min((cam.right - cam.left) * (fill || 0.9) / (2 * w), (cam.top - cam.bottom) * (fill || 0.9) / (2 * h))
    cam.updateProjectionMatrix()
    cam.lookAt(c)
    // The frustum can change under us after a project switch: if the model touches the frame, zoom out and retry.
    const touchesEdge = () => {
      const k = document.createElement('canvas'); k.width = p.canvas.width; k.height = p.canvas.height
      const g = k.getContext('2d'); g.drawImage(p.canvas, 0, 0)
      const W = k.width, H = k.height, d = g.getImageData(0, 0, W, H).data
      const a = (x, y) => d[(y * W + x) * 4 + 3] > 8
      for (let i = 0; i < W; i++) if (a(i, 1) || a(i, H - 2)) return true
      for (let j = 0; j < H; j++) if (a(1, j) || a(W - 2, j)) return true
      return false
    }
    for (let tries = 0; tries < 6; tries++) {
      Canvas.withoutGizmos(() => { p.render() })
      if (!touchesEdge()) break
      cam.zoom *= 0.8; cam.updateProjectionMatrix()
    }
    const url = p.canvas.toDataURL('image/png')
    fs.mkdirSync(path.dirname(out), { recursive: true })
    fs.writeFileSync(out, Buffer.from(url.split(',')[1], 'base64'))
  }

  lib.runJob = async (assets, job) => {
    if (job.kind === 'java') await lib.loadJava(assets, job.model)
    else if (job.kind === 'bbmodel') await lib.loadBB(job.file)
    else if (job.kind === 'obj') await lib.loadObj(job)
    else if (job.kind === 'extrude') await lib.loadExtrude(assets, job)
    else throw new Error('unknown kind ' + job.kind)
    lib.hide(job.hide)
    await sleep(300)
    lib.shoot(job.out, job.rot)
    await sleep(100)
    lib.shoot(job.out, job.rot) // the first frame after a project switch can use a stale bounding box
  }

  lib.run = async (opts) => {
    opts = opts || {}
    const { assets, jobs } = JSON.parse(fs.readFileSync(JOBS, 'utf8'))
    const pick = jobs.filter((j) => !opts.ids || opts.ids.includes(j.id)).slice(opts.from || 0, (opts.from || 0) + (opts.count || 1e9))
    const manifest = fs.existsSync(MANIFEST) ? JSON.parse(fs.readFileSync(MANIFEST, 'utf8')) : {}
    const done = [], failed = []
    for (const job of pick) {
      try { await lib.runJob(assets, job); manifest[job.id] = job.sig; done.push(job.id) } catch (e) { failed.push(job.id + ': ' + e.message) }
    }
    fs.mkdirSync(path.dirname(MANIFEST), { recursive: true })
    const sorted = Object.fromEntries(Object.keys(manifest).sort().map((k) => [k, manifest[k]]))
    fs.writeFileSync(MANIFEST, JSON.stringify(sorted, null, 1) + '\n')
    await lib.closeAll()
    return { total: jobs.length, done, failed }
  }

  window.__aflWikiIcons = lib
  return Object.keys(lib)
})()
