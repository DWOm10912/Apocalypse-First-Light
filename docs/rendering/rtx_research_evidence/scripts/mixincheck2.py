import os, re, sys, pickle, collections
S = sys.argv[1]; MIX = sys.argv[2]
Y = {v: pickle.load(open(f'{S}/map/y{v}.pkl','rb')) for v in ('1.21.1','1.20.1')}
def tiny(p):
    o2i = {}; rows=[]
    for line in open(p):
        t=line.rstrip('\n').split('\t'); rows.append(t)
        if t[0]=='CLASS': o2i[t[1]]=t[2]
    def mapdesc(d): return re.sub(r'L([^;]+);', lambda m: 'L'+o2i.get(m.group(1),m.group(1))+';', d)
    meth=collections.defaultdict(dict); cls=set(o2i.values())
    for t in rows:
        if t[0]=='METHOD': meth[o2i.get(t[1],t[1])].setdefault(t[4],set()).add(mapdesc(t[2]))
    return cls, meth
T = {v: tiny(f'{S}/map/intermediary/mappings/{v}.tiny') for v in ('1.21.1','1.20.1')}
def named(v, d):
    i2n = Y[v][1]
    return re.sub(r'L([^;]+);', lambda m: 'L'+i2n.get(m.group(1),m.group(1)).split('/')[-1]+';', d)
for f in sorted(os.listdir(MIX)):
    if not f.endswith('.java'): continue
    src=open(os.path.join(MIX,f)).read()
    imports={i.split('.')[-1]:i for i in re.findall(r'^import\s+([\w.]+);',src,re.M)}
    m=re.search(r'@Mixin\(\s*(?:value\s*=\s*)?(?:targets\s*=\s*"([^"]+)"|([\w.]+)\.class)',src)
    if m.group(1): fq=m.group(1).replace('.','/')
    else:
        simple=m.group(2); outer=simple.split('.')[0]; rest=simple.split('.')[1:]
        base=imports.get(outer); fq=(base.replace('.','/') if base else outer)+''.join('$'+r for r in rest)
    ic = Y['1.21.1'][0].get(fq, fq)
    present = ic in T['1.20.1'][0]
    print(f'## {f}  -> {fq} [{ic}]  class@1.20.1={"YES" if present else "NO"}')
    names=[]
    for mm in re.findall(r'method\s*=\s*(\{[^}]*\}|"[^"]*")',src): names+=re.findall(r'"([^"]+)"',mm)
    for nm in sorted(set(names)):
        base=nm.split('(')[0]
        if base in ('<init>','<clinit>'): print(f'   {nm}: ctor'); continue
        if base.startswith('method_'): ints={base}
        else:
            ints={io for io,n,d in Y['1.21.1'][2].get(ic,[]) if n==base}
            if not ints and base in T['1.21.1'][1].get(ic,{}): ints={base}  # unobfuscated
        if not ints: print(f'   {nm}: UNRESOLVED(inherited/lambda?)'); continue
        for i in sorted(ints):
            d21=T['1.21.1'][1].get(ic,{}).get(i,set()); d20=T['1.20.1'][1].get(ic,{}).get(i,set())
            if not d20: st='MISSING in 1.20.1'
            elif d20==d21: st='SAME signature'
            else: st='SIGNATURE CHANGED'
            print(f'   {nm} [{i}]: {st}')
            if st=='SIGNATURE CHANGED':
                print(f'        1.21.1: {[named("1.21.1",x) for x in d21]}')
                print(f'        1.20.1: {[named("1.20.1",x) for x in d20]}')
