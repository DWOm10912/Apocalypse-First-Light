/* Run inside Blockbench after creating an empty Free Model project.
 * Set globalThis.aflVendingVariant to "intact" or "broken" before evaluation.
 * Builds editable cube-only assets; no runtime resources are generated.
 */
(() => {
  const variant = globalThis.aflVendingVariant || 'intact';
  if (!['intact', 'broken'].includes(variant)) throw new Error('Unknown variant');
  if (Outliner.elements.length) throw new Error('Use an empty project');
  Project.name = 'afl_vending_machine_' + variant;
  Project.texture_width = Project.texture_height = 128;
  Project.box_uv = false;

  const canvas = document.createElement('canvas');
  canvas.width = canvas.height = 128;
  const ctx = canvas.getContext('2d');
  const colors = {
    shell: [43, 47, 51], edge: [65, 71, 76], inner: [61, 69, 75],
    shelf: [116, 128, 136], rubber: [24, 29, 33], silver: [151, 164, 173],
    lamp: [226, 233, 226], screen: [27, 45, 55], key: [122, 137, 145],
    dark: [18, 23, 27], blue: [70, 107, 129], led: [145, 193, 196],
    seam: [33, 38, 42], highlight: [82, 91, 98], bin: [39, 46, 51],
    liner: [79, 89, 96], tag: [164, 177, 183], warm: [185, 193, 185],
    glass_edge: [135, 183, 205], slot: [11, 17, 21], trim: [99, 110, 117]
  };
  const tiles = {};
  Object.entries(colors).forEach(([name, rgb], i) => {
    const x = (i % 8) * 16, y = Math.floor(i / 8) * 16;
    tiles[name] = [x + 1, y + 1, x + 15, y + 15];
    for (let j = 0; j < 16; j++) {
      const lift = j < 2 ? 5 : j < 7 ? 2 : j > 13 ? -4 : 0;
      ctx.fillStyle = `rgb(${rgb.map(v => v + lift).join(',')})`;
      ctx.fillRect(x, y + j, 16, 1);
    }
  });
  function glass(x, broken) {
    ctx.fillStyle = 'rgba(115,164,187,0.16)'; ctx.fillRect(x, 48, 48, 80);
    ctx.fillStyle = 'rgba(183,218,231,0.16)';
    ctx.beginPath(); ctx.moveTo(x + 2, 48); ctx.lineTo(x + 8, 48);
    ctx.lineTo(x + 36, 128); ctx.lineTo(x + 30, 128); ctx.closePath(); ctx.fill();
    ctx.fillStyle = 'rgba(190,223,234,0.22)';
    ctx.fillRect(x, 48, 1, 80); ctx.fillRect(x + 47, 48, 1, 80);
    ctx.fillRect(x, 48, 48, 1); ctx.fillRect(x, 127, 48, 1);
    if (broken) {
      const lines = [[[1,8],[7,13],[3,22]], [[40,1],[34,8],[39,15]],
        [[47,52],[42,59],[45,68]], [[2,63],[8,70],[6,79]],
        [[18,0],[21,7],[16,12]], [[27,79],[24,72],[29,68]]];
      ctx.strokeStyle = 'rgba(190,220,231,0.64)'; ctx.lineWidth = 1;
      lines.forEach(points => {ctx.beginPath(); points.forEach(([u,v],i) =>
        i ? ctx.lineTo(x+u,48+v) : ctx.moveTo(x+u,48+v)); ctx.stroke();});
      const low=[3.3,2.2,3.9,1.4,2.0,1.0,2.8,1.7,3.5,4.5];
      const high=[14.1,15.5,16.9,15.8,17.5,16.1,17.3,15.3,16.2,13.8];
      const contour=[], width=10.07, height=18.9, step=(width-.55-.6)/10;
      for(let i=0;i<10;i++) {
        const a=.55+i*step;
        contour.push([a,Math.max(.3,low[i]-1.0)],[a+step*.4,low[i]],[a+step,Math.max(.3,low[i]-.7)]);
      }
      for(let i=9;i>=0;i--) {
        const a=.55+i*step;
        contour.push([a+step,high[i]+.75],[a+step*.55,high[i]],[a,high[i]+1.0]);
      }
      ctx.beginPath(); contour.forEach(([u,v],i)=>{
        const px=x+u/width*48,py=48+(1-v/height)*80;
        i?ctx.lineTo(px,py):ctx.moveTo(px,py);
      }); ctx.closePath();
      ctx.globalCompositeOperation='destination-out'; ctx.fillStyle='#ffffff';ctx.fill();
      ctx.globalCompositeOperation='source-over';ctx.strokeStyle='rgba(184,220,232,0.65)';
      ctx.lineWidth=.8;ctx.stroke();
    }
  }
  glass(0, false); glass(48, true);
  const texture = new Texture({name: 'afl_vending_machine.png', render_sides: 'double'});
  texture.fromDataURL(canvas.toDataURL('image/png')).add(false);
  globalThis.aflVendingTexturePNG = canvas.toDataURL('image/png');
  const root = new Group({name:'machine_root',origin:[0,0,0]}).init();
  function group(name,parent=root,origin=[0,0,0]) {
    return new Group({name,origin}).addTo(parent).init();
  }
  function cube(name,from,to,material,parent,extra={}) {
    const c = new Cube({name,from,to,box_uv:false,autouv:0,color:0,...extra}).addTo(parent).init();
    for (const f of Object.values(c.faces)) {f.texture=texture.uuid; f.uv=tiles[material].slice();}
    return c;
  }
  const body = group('body_shell');
  const base = group('base_assembly', body);
  cube('recessed_bottom_plinth',[-7.4,.35,-7.2],[7.4,1.25,7.3],'rubber',base);
  for (const x of [-6.55,6.55]) for (const z of [-6.3,6.3])
    cube('rubber_foot_'+x+'_'+z,[x-.65,0,z-.65],[x+.65,.65,z+.65],'rubber',base);
  cube('base_floor',[-7.7,1.1,-7.3],[7.7,1.55,7.65],'edge',base);
  cube('left_outer_shell',[-7.8,1.25,-7.1],[-7.1,31.2,7.6],'shell',body);
  cube('right_outer_shell',[7.1,1.25,-7.1],[7.8,31.2,7.6],'shell',body);
  cube('rear_shell',[-7.1,1.25,7.05],[7.1,31.2,7.7],'shell',body);
  cube('top_shell',[-7.8,31.2,-7.1],[7.8,32,7.7],'shell',body);
  cube('left_front_corner',[-7.8,1.3,-7.65],[-7.25,31.3,-7.1],'edge',body);
  cube('right_front_corner',[7.25,1.3,-7.65],[7.8,31.3,-7.1],'edge',body);
  cube('left_top_chamfer_step',[-7.65,31.7,-7.15],[-7.1,31.92,7.5],'highlight',body);
  cube('rear_service_panel',[-6.75,2.1,7.7],[6.75,27.7,7.79],'seam',body);
  cube('rear_service_inset',[-6.5,2.35,7.79],[6.5,27.45,7.83],'shell',body);
  const vents = group('ventilation',body);
  for (const y of [3.0,3.7,4.4,5.1,5.8]) {
    cube('rear_lower_vent_'+y,[-5.8,y,7.83],[4.8,y+.3,7.87],'slot',vents);
    cube('left_low_vent_'+y,[-7.84,y,2.5],[-7.8,y+.25,5.8],'slot',vents);
  }
  for (let i=0;i<3;i++) cube('rear_upper_vent_'+i,[-5.8,28.3+i*.65,7.7],[5.8,28.55+i*.65,7.75],'slot',vents);
  for (const x of [-6.5,6.3]) for (const y of [2.5,27.1])
    cube('rear_panel_fastener_'+x+'_'+y,[x,y,7.83],[x+.16,y+.16,7.89],'highlight',vents);

  const head=group('top_sign_box');
  cube('header_housing',[-7.22,27.5,-7.35],[7.22,31.65,-5.75],'rubber',head);
  cube('header_top_rail',[-7.15,31.1,-7.65],[7.15,31.6,-7.3],'edge',head);
  cube('header_bottom_rail',[-7.15,27.6,-7.65],[7.15,28.08,-7.3],'edge',head);
  cube('header_left_rail',[-7.15,28.08,-7.65],[-6.65,31.1,-7.3],'edge',head);
  cube('header_right_rail',[6.65,28.08,-7.65],[7.15,31.1,-7.3],'edge',head);
  cube('plain_opal_header',[-6.63,28.15,-7.42],[6.63,31.03,-7.26],'lamp',head);
  cube('header_lower_shadow',[-6.55,28.15,-7.44],[6.55,28.38,-7.42],'warm',head);

  const inner=group('interior_shell');
  cube('back_liner',[-6.95,6.7,5.8],[4.47,27.5,6.08],'bin',inner);
  cube('left_liner',[-7.05,6.7,-6.3],[-6.8,27.5,5.8],'inner',inner);
  cube('right_partition',[4.45,6.7,-6.3],[4.8,27.5,6.2],'inner',inner);
  cube('interior_ceiling',[-6.8,27.2,-6.2],[4.45,27.5,5.8],'liner',inner);
  cube('left_light_channel',[-6.8,7.6,-5.9],[-6.52,27,-5.58],'edge',inner);
  cube('left_diffused_light',[-6.8,7.7,-5.95],[-6.55,26.9,-5.9],'lamp',inner);
  cube('right_light_channel',[4.12,7.6,-5.9],[4.4,27,-5.58],'edge',inner);
  cube('right_diffused_light',[4.15,7.7,-5.95],[4.4,26.9,-5.9],'lamp',inner);
  const rows=group('interior_rows');
  const markers=group('display_layout');
  const centers=[-4.85,-1.25,2.35];
  for(let row=0;row<4;row++) {
    const y=7.65+row*4.7, g=group('interior_row_0'+(row+1),rows);
    cube('row_'+row+'_tray',[-6.48,y,-5.8],[4.0,y+.28,5.6],'inner',g);
    cube('row_'+row+'_front_rail',[-6.5,y-.12,-6.12],[4.02,y+.34,-5.8],'shelf',g);
    cube('row_'+row+'_price_track',[-6.35,y-.02,-6.17],[3.85,y+.18,-6.12],'edge',g);
    for(let lane=0;lane<3;lane++) {
      const x=centers[lane], laneGroup=group('lane_r'+(row+1)+'_c'+(lane+1),g);
      cube('lane_'+row+'_'+lane+'_bed',[x-1.38,y+.28,-5.3],[x+1.38,y+.35,5.25],'liner',laneGroup);
      cube('lane_'+row+'_'+lane+'_left_guide',[x-1.45,y+.35,-5.1],[x-1.33,y+.72,4.9],'edge',laneGroup);
      cube('lane_'+row+'_'+lane+'_right_guide',[x+1.33,y+.35,-5.1],[x+1.45,y+.72,4.9],'edge',laneGroup);
      cube('lane_'+row+'_'+lane+'_tag',[x-.56,y+.01,-6.2],[x+.56,y+.15,-6.17],'tag',laneGroup);
      const coils=group('delivery_loop_r'+(row+1)+'_c'+(lane+1),laneGroup);
      for(let turn=0;turn<2;turn++) {
        const z=-.4+turn*2.6, p='loop_'+row+'_'+lane+'_'+turn;
        cube(p+'_bottom',[x-.99,y+.43,z],[x+.99,y+.56,z+.13],'shelf',coils);
        cube(p+'_left',[x-.99,y+.56,z],[x-.86,y+2.02,z+.13],'shelf',coils);
        cube(p+'_top',[x-.86,y+1.89,z],[x+.99,y+2.02,z+.13],'shelf',coils);
        cube(p+'_right',[x+.86,y+.56,z],[x+.99,y+1.89,z+.13],'shelf',coils);
      }
      cube('loop_spine_'+row+'_'+lane,[x+.82,y+.4,-.27],[x+.95,y+.53,2.2],'edge',coils);
      group('display_r'+(row+1)+'_c'+(lane+1),markers,[x,y+.35,-3.55]);
    }
  }

  const door=group('glass_door',root,[-7.05,17.6,-7.3]);
  const frame=group('front_frame',door);
  cube('window_left_frame',[-7.2,6.9,-7.67],[-6.65,27.52,-6.7],'edge',frame);
  cube('window_right_frame',[4.02,6.9,-7.67],[4.55,27.52,-6.7],'edge',frame);
  cube('window_top_frame',[-6.65,26.98,-7.67],[4.02,27.52,-6.7],'edge',frame);
  cube('window_bottom_frame',[-6.65,6.9,-7.67],[4.02,7.48,-6.7],'edge',frame);
  cube('left_window_gasket',[-6.65,7.48,-7.45],[-6.37,26.98,-6.95],'rubber',frame);
  cube('right_window_gasket',[3.74,7.48,-7.45],[4.02,26.98,-6.95],'rubber',frame);
  cube('top_window_gasket',[-6.37,26.7,-7.45],[3.74,26.98,-6.95],'rubber',frame);
  cube('bottom_window_gasket',[-6.37,7.48,-7.45],[3.74,7.76,-6.95],'rubber',frame);
  for(const y of [9.0,24.8]) cube('door_hinge_'+y,[-7.3,y,-7.5],[-7.2,y+1.25,-6.9],'highlight',frame);
  cube('door_lock_plate',[4.08,16.5,-7.75],[4.43,17.1,-7.67],'silver',frame);
  cube('door_lock_slot',[4.225,16.66,-7.79],[4.285,16.94,-7.75],'slot',frame);
  const glassGroup=group(variant==='intact'?'glass_panel':'broken_glass_parts',door);
  const gx=-6.35, gy=7.78, gw=10.07, gh=18.9;
  function glassPiece(name,x1,y1,x2,y2) {
    const c=cube(name,[gx+x1,gy+y1,-7.24],[gx+x2,gy+y2,-7.15],'glass_edge',glassGroup);
    const u=variant==='intact'?0:48;
    c.faces.north.uv=[u+x1/gw*48,48+(1-y2/gh)*80,u+x2/gw*48,48+(1-y1/gh)*80];
    c.faces.south.uv=[u+x2/gw*48,48+(1-y2/gh)*80,u+x1/gw*48,48+(1-y1/gh)*80];
    for(const side of ['east','west','up','down']) c.faces[side].uv=[120,120,121,121];
    c.render_order='in_front';
    return c;
  }
  if(variant==='intact') glassPiece('intact_glass',0,0,gw,gh);
  else {
    const left=.55,right=.6;
    glassPiece('left_retained_edge',0,0,left,gh);
    glassPiece('right_retained_edge',gw-right,0,gw,gh);
    const low=[3.3,2.2,3.9,1.4,2.0,1.0,2.8,1.7,3.5,4.5];
    const high=[14.1,15.5,16.9,15.8,17.5,16.1,17.3,15.3,16.2,13.8];
    const step=(gw-left-right)/10;
    for(let i=0;i<10;i++) {
      const a=left+i*step,b=left+(i+1)*step;
      glassPiece('bottom_shard_'+(i+1),a,0,b,low[i]);
      glassPiece('top_shard_'+(i+1),a,high[i],b,gh);
    }
  }

  const control=group('control_panel');
  cube('control_column',[4.64,1.55,-7.4],[7.22,27.48,-5.1],'shell',control);
  cube('control_recess',[4.91,11.1,-7.49],[6.94,21.48,-7.4],'rubber',control);
  cube('display_bezel',[5.07,19.45,-7.65],[6.81,21.1,-7.49],'edge',control);
  cube('status_screen',[5.25,19.67,-7.69],[6.63,20.88,-7.65],'screen',control);
  for(let i=0;i<3;i++) cube('screen_status_bar_'+i,[5.43+i*.32,20.02,-7.72],[5.63+i*.32,20.43,-7.69],'led',control);
  const buttons=group('keypad',control);
  for(let r=0;r<4;r++) for(let c=0;c<3;c++) {
    const x=5.13+c*.55,y=18.55-r*.58;
    cube('key_'+r+'_'+c,[x,y,-7.74],[x+.4,y+.38,-7.49],'key',buttons);
  }
  cube('payment_bezel',[5.1,13.8,-7.63],[6.78,15.72,-7.49],'edge',control);
  cube('payment_face',[5.25,13.96,-7.67],[6.63,15.55,-7.63],'rubber',control);
  cube('coin_slot',[5.5,14.15,-7.7],[5.63,15.3,-7.67],'slot',control);
  cube('coin_slot_side',[5.66,14.15,-7.72],[5.8,15.3,-7.67],'silver',control);
  cube('contactless_pad',[6.02,14.15,-7.7],[6.45,15.28,-7.67],'blue',control);
  cube('return_slot_surround',[5.16,11.7,-7.68],[6.77,13.0,-7.49],'edge',control);
  cube('return_slot_hollow',[5.37,11.9,-7.72],[6.56,12.73,-7.68],'slot',control);
  cube('return_slot_floor',[5.37,11.9,-7.77],[6.56,12.08,-7.72],'highlight',control);

  const pickup=group('pickup_bin');
  cube('pickup_upper_panel',[-7.2,5.65,-7.42],[4.55,6.86,-6.3],'shell',pickup);
  cube('pickup_lower_panel',[-7.2,1.5,-7.42],[4.55,2.38,-6.3],'shell',pickup);
  cube('pickup_left_panel',[-7.2,2.38,-7.42],[-5.85,5.65,-6.3],'shell',pickup);
  cube('pickup_right_panel',[3.2,2.38,-7.42],[4.55,5.65,-6.3],'shell',pickup);
  cube('pickup_back',[-5.85,2.38,-3.5],[3.2,5.65,-3.25],'dark',pickup);
  cube('pickup_inner_floor',[-5.85,2.38,-7.1],[3.2,2.65,-3.5],'bin',pickup);
  cube('pickup_inner_ceiling',[-5.85,5.4,-7.1],[3.2,5.65,-3.5],'dark',pickup);
  cube('pickup_left_wall',[-5.85,2.65,-7.1],[-5.6,5.4,-3.5],'bin',pickup);
  cube('pickup_right_wall',[2.95,2.65,-7.1],[3.2,5.4,-3.5],'bin',pickup);
  const rim=group('pickup_surround',pickup);
  cube('pickup_top_rim',[-6.04,5.3,-7.71],[3.39,5.82,-7.35],'trim',rim);
  cube('pickup_bottom_rim',[-6.04,2.22,-7.71],[3.39,2.74,-7.35],'trim',rim);
  cube('pickup_left_rim',[-6.04,2.74,-7.71],[-5.55,5.3,-7.35],'trim',rim);
  cube('pickup_right_rim',[2.9,2.74,-7.71],[3.39,5.3,-7.35],'trim',rim);
  const hatch=group('pickup_hatch',pickup,[-1.3,5.3,-5.8]);
  cube('recessed_hinged_flap',[-5.52,2.98,-5.86],[2.87,5.26,-5.68],'dark',hatch);
  cube('flap_lower_edge',[-5.52,2.98,-5.92],[2.87,3.12,-5.86],'edge',hatch);
  // Front is -Z. Mirror X so the payment column reads on the viewer's right.
  for (const c of Cube.all) {
    const x1=c.from[0], x2=c.to[0]; c.from[0]=-x2; c.to[0]=-x1;
    c.origin[0]=-c.origin[0];
    for (const side of ['north','south']) {
      const uv=c.faces[side].uv; [uv[0],uv[2]]=[uv[2],uv[0]];
    }
  }
  for (const g of Group.all) g.origin[0]=-g.origin[0];
  const bounds=[0,1,2].map(a=>[Math.min(...Cube.all.map(c=>c.from[a])),Math.max(...Cube.all.map(c=>c.to[a]))]);
  const offset=[-(bounds[0][0]+bounds[0][1])/2,0,-(bounds[2][0]+bounds[2][1])/2];
  for(const c of Cube.all) for(const a of [0,2]) {
    c.from[a]=+(c.from[a]+offset[a]).toFixed(5);
    c.to[a]=+(c.to[a]+offset[a]).toFixed(5);
    c.origin[a]=+(c.origin[a]+offset[a]).toFixed(5);
  }
  for(const g of Group.all) if(g!==root) for(const a of [0,2]) g.origin[a]=+(g.origin[a]+offset[a]).toFixed(5);
  Canvas.updateAll();
  return JSON.stringify({variant,cubes:Cube.all.length,groups:Group.all.length,
    texture: texture.uuid,dimensions:bounds.map(b=>+(b[1]-b[0]).toFixed(5)),front:'north / -Z'});
})();
