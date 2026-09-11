(() => {for(const side of ['left','right'])Group.all.find(g=>g.name===side+'_lid_assembly').rotation=[0,0,0];Canvas.updateAll();return 'Closed pose restored';})()
