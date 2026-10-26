"""CMU ASF/AMC reader + forward kinematics (numpy). Joint positions in metres, Y up (the capture frame)."""
import math
import numpy as np

INCH = 0.0254


def _rot(axis_order, angles_deg):
    """Static XYZ: R = Rz Ry Rx (x applied first), as the ASF/AMC convention."""
    a = {k: 0.0 for k in 'xyz'}
    for k, v in zip(axis_order, angles_deg):
        a[k] = math.radians(v)
    cx, sx = math.cos(a['x']), math.sin(a['x'])
    cy, sy = math.cos(a['y']), math.sin(a['y'])
    cz, sz = math.cos(a['z']), math.sin(a['z'])
    rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return rz @ ry @ rx


class Bone:
    def __init__(self):
        self.name = ''
        self.direction = np.zeros(3)
        self.length = 0.0
        self.axis = np.eye(3)
        self.dof = []
        self.parent = None
        self.children = []


class Skeleton:
    def __init__(self, path):
        self.bones = {}
        self.scale = 1.0
        root = Bone()
        root.name = 'root'
        root.dof = ['tx', 'ty', 'tz', 'rx', 'ry', 'rz']
        self.bones['root'] = root
        lines = [l.strip() for l in open(path, encoding='latin-1') if l.strip() and not l.startswith('#')]
        i = 0
        section = None
        while i < len(lines):
            l = lines[i]
            if l.startswith(':'):
                section = l.split()[0]
                if section == ':units':
                    i += 1
                    while not lines[i].startswith(':'):
                        p = lines[i].split()
                        if p[0] == 'length':
                            self.scale = INCH / float(p[1])
                        i += 1
                    continue
                if section == ':root':
                    i += 1
                    while not lines[i].startswith(':'):
                        p = lines[i].split()
                        if p[0] == 'orientation':
                            root.axis = _rot('xyz', [float(x) for x in p[1:4]])
                        i += 1
                    continue
                i += 1
                continue
            if section == ':bonedata' and l == 'begin':
                b = Bone()
                i += 1
                while lines[i] != 'end':
                    p = lines[i].split()
                    if p[0] == 'name':
                        b.name = p[1]
                    elif p[0] == 'direction':
                        b.direction = np.array([float(x) for x in p[1:4]])
                    elif p[0] == 'length':
                        b.length = float(p[1]) * self.scale
                    elif p[0] == 'axis':
                        b.axis = _rot(p[4].lower(), [float(x) for x in p[1:4]])
                    elif p[0] == 'dof':
                        b.dof = [x.lower() for x in p[1:]]
                    i += 1
                self.bones[b.name] = b
                i += 1
                continue
            if section == ':hierarchy':
                if l in ('begin', 'end'):
                    i += 1
                    continue
                p = l.split()
                for c in p[1:]:
                    self.bones[c].parent = self.bones[p[0]]
                    self.bones[p[0]].children.append(self.bones[c])
                i += 1
                continue
            i += 1
        self.order = []
        stack = [root]
        while stack:
            b = stack.pop(0)
            self.order.append(b)
            stack.extend(b.children)

    def local(self, frame):
        """frame: {bone: [values in dof order]} -> (root translation m, {bone: local rotation 3x3 about its parent})."""
        loc = {}
        t = None
        for b in self.order:
            vals = frame.get(b.name, [])
            if b.name == 'root':
                t = np.array(vals[0:3]) * self.scale
                loc['root'] = b.axis @ _rot('xyz', vals[3:6]) @ b.axis.T
                continue
            rot_axes = [d[1] for d in b.dof if d.startswith('r')]
            m = _rot(''.join(rot_axes), [v for d, v in zip(b.dof, vals) if d.startswith('r')]) if rot_axes else np.eye(3)
            loc[b.name] = b.axis @ m @ b.axis.T
        return t, loc

    def chain(self, t, loc):
        """Root translation + local rotations -> {bone: (start, end, rotation 3x3)} in metres."""
        out = {'root': (t, t, loc['root'])}
        for b in self.order[1:]:
            ps, pe, pg = out[b.parent.name]
            g = pg @ loc[b.name]
            out[b.name] = (pe, pe + g @ (b.direction * b.length), g)
        return out

    def fk(self, frame):
        """frame: {bone: [values in dof order]} -> {bone: (start, end, rotation 3x3)} in metres."""
        return self.chain(*self.local(frame))


def read_amc(path):
    frames = []
    cur = None
    for l in open(path, encoding='latin-1'):
        l = l.strip()
        if not l or l.startswith('#') or l.startswith(':'):
            continue
        p = l.split()
        if p[0].isdigit() and len(p) == 1:
            cur = {}
            frames.append(cur)
            continue
        cur[p[0]] = [float(x) for x in p[1:]]
    return frames


def positions(skel, frames, step=1):
    """[frame][bone] -> end position (root: its position), metres, Y up."""
    res = []
    for f in frames[::step]:
        o = skel.fk(f)
        res.append({k: v[1] for k, v in o.items()})
    return res
