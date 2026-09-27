// Run in Blockbench with the saved native-gun .bbmodel open. Captures the
// current default pose with one shared right-facing three-quarter camera.
// It restores the user's viewport and changes no geometry, UV, or texture.
(async () => {
    const fs = require('fs');
    const path = require('path');
    const sourceNames = {
        p9_01_v2_native: 'p9_01',
        blackridge_50: 'blackridge_50',
        br51_01: 'br51_01',
        hr55: 'hr55',
        silverwood_12_hybrid_hero_remaster_v1: 'silverwood_12',
    };
    const sourceName = path.parse(Project.save_path || '').name;
    const weapon = globalThis.AFL_INVENTORY_ID || sourceNames[sourceName] || sourceName;
    if (!/^[a-z0-9_]+$/.test(weapon) || !Project.save_path || !fs.existsSync(Project.save_path) ||
        (!Project.saved && Undo.history.length > 0)) {
        throw new Error('Open a native-gun source from disk and provide a valid weapon ID before capture');
    }
    const root = Group.all.find(group => group.name === 'root');
    const object = root && Canvas.scene.getObjectByName(root.uuid);
    const bounds = object && new THREE.Box3().setFromObject(object);
    if (!bounds || bounds.isEmpty()) throw new Error('Cannot find visible root model bounds');

    const preview = Preview.selected;
    const original = {
        position: preview.camera.position.toArray(),
        target: preview.controls.target.toArray(),
        projection: preview.isOrtho ? 'orthographic' : 'perspective',
        zoom: preview.camOrtho.zoom,
    };
    // Reference player-arm cubes are authoring aids, not exported gun art.
    const references = Cube.all.filter(cube => /arm_reference_(?:slim_)?cube/.test(cube.name))
        .map(cube => Canvas.scene.getObjectByName(cube.uuid))
        .filter(Boolean)
        .map(object => ({object, visible: object.visible}));
    const center = bounds.getCenter(new THREE.Vector3()).toArray();
    const cameraOffset = [512, 418.048, -512];
    const point = center.map((coordinate, index) => coordinate + cameraOffset[index]);
    const waitForDraw = () => new Promise(resolve => setTimeout(resolve, 150));
    const capture = () => new Promise(resolve => {
        references.forEach(({object}) => { object.visible = false; });
        Screencam.screenshotPreview(preview, {crop: false, width: 1024, height: 1024}, resolve);
    });
    const alphaBounds = async dataUrl => {
        const image = new Image();
        image.src = dataUrl;
        await image.decode();
        const canvas = document.createElement('canvas');
        canvas.width = image.width;
        canvas.height = image.height;
        const context = canvas.getContext('2d', {willReadFrequently: true});
        context.drawImage(image, 0, 0);
        const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
        let minX = canvas.width, minY = canvas.height, maxX = -1, maxY = -1;
        for (let y = 0; y < canvas.height; y++) for (let x = 0; x < canvas.width; x++) {
            if (!pixels[(y * canvas.width + x) * 4 + 3]) continue;
            minX = Math.min(minX, x); minY = Math.min(minY, y);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
        }
        if (maxX < minX) throw new Error('The inventory camera rendered no visible pixels');
        return {minX, minY, maxX, maxY, width: maxX - minX + 1, height: maxY - minY + 1,
                touchesEdge: minX === 0 || minY === 0 || maxX === canvas.width - 1 || maxY === canvas.height - 1};
    };
    try {
        preview.loadAnglePreset({position: point, target: center, projection: 'orthographic', zoom: 0.15});
        preview.controls.update(); Canvas.updateAll(); await waitForDraw();
        const initial = await alphaBounds(await capture());
        if (initial.touchesEdge) throw new Error('Initial camera clips the model');
        const zoom = 0.15 * 800 / Math.max(initial.width, initial.height);
        const capturedHeight = preview.canvas.height * Math.min(1, 1024 / preview.canvas.width,
                                                               1024 / preview.canvas.height);
        const worldPerPixel = (preview.camOrtho.top - preview.camOrtho.bottom) /
                              preview.camOrtho.zoom / capturedHeight;
        const right = new THREE.Vector3(1, 0, 0).applyQuaternion(preview.camera.quaternion);
        const up = new THREE.Vector3(0, 1, 0).applyQuaternion(preview.camera.quaternion);
        const shift = right.multiplyScalar(((initial.minX + initial.maxX) / 2 - 512) * worldPerPixel)
            .add(up.multiplyScalar(-((initial.minY + initial.maxY) / 2 - 512) * worldPerPixel));
        const centered = new THREE.Vector3(...center).add(shift).toArray();
        const centeredCamera = new THREE.Vector3(...point).add(shift).toArray();
        preview.loadAnglePreset({position: centeredCamera, target: centered, projection: 'orthographic', zoom});
        preview.controls.update(); Canvas.updateAll(); await waitForDraw();
        const dataUrl = await capture();
        const final = await alphaBounds(dataUrl);
        if (final.touchesEdge) throw new Error('Fitted camera clips the model');
        if (Math.abs((final.minX + final.maxX) / 2 - 512) > 15 ||
            Math.abs((final.minY + final.maxY) / 2 - 512) > 15 ||
            Math.abs(Math.max(final.width, final.height) - 800) > 80) {
            throw new Error('Fitted inventory render is not centered or sized correctly');
        }
        const output = globalThis.AFL_INVENTORY_CAPTURE_PATH ||
            path.join('D:/Minecraft Modding/Apocalypse First Light/src/main/blockbench/inventory_icons',
                      weapon + '_inventory.png');
        fs.mkdirSync(path.dirname(output), {recursive: true});
        fs.writeFileSync(output, Buffer.from(dataUrl.split(',')[1], 'base64'));
        return {weapon, output, camera: cameraOffset, projectedBounds: final, zoom};
    } finally {
        references.forEach(({object, visible}) => { object.visible = visible; });
        preview.loadAnglePreset(original);
        preview.controls.update(); Canvas.updateAll();
    }
})()
