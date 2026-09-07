// Run in Blockbench's local scripting context. Exact mechanical 80% resampling,
// not a new render: preserve the original canvas, aspect ratio and transparency.
(function () {
    const fs = require('fs');
    const root = 'D:/Minecraft Modding/Apocalypse First Light/';
    const original = root + 'build/p9-animation-v1-before/p9_01_inventory.png';
    const output = root + 'src/main/resources/assets/apocalypse_firstlight/textures/item/p9_01_inventory.png';
    const img = new Image();
    img.onload = () => {
        const canvas = document.createElement('canvas');
        canvas.width = img.naturalWidth;
        canvas.height = img.naturalHeight;
        const ctx = canvas.getContext('2d');
        ctx.imageSmoothingEnabled = true;
        ctx.imageSmoothingQuality = 'high';
        const w = Math.round(canvas.width * .8), h = Math.round(canvas.height * .8);
        ctx.drawImage(img, Math.floor((canvas.width - w) / 2), Math.floor((canvas.height - h) / 2), w, h);
        fs.writeFileSync(output, Buffer.from(canvas.toDataURL('image/png').split(',')[1], 'base64'));
    };
    img.src = 'data:image/png;base64,' + fs.readFileSync(original).toString('base64');
})();
