(() => {const g=Group.all.find(g=>g.name==='lid_assembly');g.visibility=!g.visibility;g.forEachChild(c=>{c.visibility=g.visibility;});Canvas.updateAll();return {lidVisible:g.visibility};})()
