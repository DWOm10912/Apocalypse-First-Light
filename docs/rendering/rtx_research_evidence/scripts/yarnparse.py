import os, sys, re, json, pickle
def parse(root):
    # returns classes: named -> inter ; inter -> named ; methods: (inter_cls, inter_m or name, desc_inter) etc.
    named2int = {}; int2named = {}
    meths = {}  # inter class -> list of (inter_name, named_name, desc_inter)
    fields = {}
    for dp, dn, fn in os.walk(os.path.join(root, 'mappings')):
        for f in fn:
            if not f.endswith('.mapping'): continue
            stack = []
            for line in open(os.path.join(dp, f), encoding='utf-8'):
                if not line.strip(): continue
                ind = len(line) - len(line.lstrip('\t'))
                parts = line.strip().split(' ')
                kind = parts[0]
                if kind == 'CLASS':
                    stack = stack[:ind]
                    obf = parts[1]
                    nm = parts[2] if len(parts) > 2 and not parts[2].startswith('ACC') else None
                    if ind == 0:
                        full_obf = obf; full_nm = nm or obf
                    else:
                        po, pn = stack[ind-1]
                        full_obf = obf if '$' in obf or '/' in obf else po + '$' + obf
                        if '/' not in obf: full_obf = po + '$' + obf
                        full_nm = pn + '$' + (nm or obf)
                    stack.append((full_obf, full_nm))
                    named2int[full_nm] = full_obf; int2named[full_obf] = full_nm
                elif kind == 'METHOD':
                    co, cn = stack[ind-1]
                    if len(parts) == 4: io, nm, desc = parts[1], parts[2], parts[3]
                    else: io, nm, desc = parts[1], parts[1], parts[2]
                    meths.setdefault(co, []).append((io, nm, desc))
                elif kind == 'FIELD':
                    co, cn = stack[ind-1]
                    if len(parts) == 4: io, nm, desc = parts[1], parts[2], parts[3]
                    else: io, nm, desc = parts[1], parts[1], parts[2]
                    fields.setdefault(co, []).append((io, nm, desc))
    return named2int, int2named, meths, fields
if __name__ == '__main__':
    d = parse(sys.argv[1])
    pickle.dump(d, open(sys.argv[2], 'wb'))
    print(len(d[0]), 'classes', sum(len(v) for v in d[2].values()), 'methods')
