from collections import Counter, defaultdict, deque
from .anvil_reader import chunks, blocks

def artificial(name):
    return any(word in name for word in ('concrete','glass','bricks','polished','slab','stairs','iron','trapdoor','door','carpet','lantern','lamp','sign','fence','wall','quartz','terracotta','planks'))

def discover(world,metadata,radius=128,y_min=0,y_max=192,dimension='overworld'):
    if not 1<=radius<=256 or not -64<=y_min<=y_max<=319: raise ValueError('SCAN_LIMIT: radius<=256, -64<=Y<=319')
    sx,_,sz=metadata['spawn'];bounds=[sx-radius,y_min,sz-radius,sx+radius,y_max,sz+radius]
    cells={};versions=Counter();scanned=0
    for cx,cz,root in chunks(world,dimension,bounds):
        scanned+=1;versions[root.get('DataVersion','unknown')]+=1
        stats={'bounds':[10**9,10**9,10**9,-10**9,-10**9,-10**9],'palette':Counter(),'hist':Counter(),'count':0}
        for x,y,z,state in blocks(cx,cz,root,bounds):
            if not artificial(state['Name']): continue
            stats['count']+=1;stats['palette'][state['Name']]+=1;stats['hist'][y]+=1
            for i,v in enumerate((x,y,z)):stats['bounds'][i]=min(stats['bounds'][i],v);stats['bounds'][i+3]=max(stats['bounds'][i+3],v)
        if stats['count']>=16: cells[cx,cz]=stats
    candidates=[];remaining=set(cells)
    while remaining:
        seed=min(remaining);remaining.remove(seed);queue=deque([seed]);group=[]
        while queue:
            cell=queue.popleft();group.append(cells[cell]);x,z=cell
            for other in ((x-1,z),(x+1,z),(x,z-1),(x,z+1)):
                if other in remaining:remaining.remove(other);queue.append(other)
        bounds=[min(s['bounds'][i] for s in group) for i in range(3)]+[max(s['bounds'][i] for s in group) for i in range(3,6)]
        palette=Counter();hist=Counter()
        for s in group:palette.update(s['palette']);hist.update(s['hist'])
        count=sum(s['count'] for s in group);size=[bounds[i+3]-bounds[i]+1 for i in range(3)];volume=size[0]*size[1]*size[2]
        if count<128 or size[1]<5: continue
        candidates.append({'bounds':bounds,'size':size,'artificial_blocks':count,'artificial_density':count/volume,'palette_top_30':palette.most_common(30),'artificial_y_histogram':dict(sorted(hist.items())),'confidence':'LOW_HEURISTIC_REQUIRES_REVIEW','note':'Connected 16x16 cells with >=16 artificial blocks. Adjacent buildings/landscaping may merge; not an automatic extraction.'})
    candidates.sort(key=lambda x:x['artificial_blocks'],reverse=True)
    for i,c in enumerate(candidates):c['candidate_id']=f'candidate_{i+1:02}'
    return {'scan_bounds':[sx-radius,y_min,sz-radius,sx+radius,y_max,sz+radius],'chunks_scanned':scanned,'chunk_data_versions':dict(versions),'candidates':candidates,'selection_required':True,'status':'NO_STRUCTURE_CANDIDATE' if not candidates else 'MULTIPLE_CANDIDATES' if len(candidates)>1 else 'CANDIDATE_REVIEW_REQUIRED'}
