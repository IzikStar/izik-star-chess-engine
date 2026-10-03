rows=[]
for l in open('timing.txt'):
    if not l.startswith('P'): continue
    p=l.split(); n=int(p[1]); d={int(k[1:]):int(v) for k,v in (x.split('=') for x in p[3:])}
    rows.append((n,d))
def band(n): return 'mid(>12)' if n>12 else ('9-12' if n>8 else '<=8')
for b in ['mid(>12)','9-12','<=8']:
    R=[d for n,d in rows if band(n)==b]
    print(b,len(R))
    for dep in range(4,10):
        v=sorted(40000 if x.get(dep) is None else x[dep] for x in R)
        if v: print(f"  d{dep}: median {v[len(v)//2]} p90 {v[min(len(v)-1,int(len(v)*0.9))]} max {v[-1]}  >5s {sum(t>5000 for t in v)}/{len(v)}")
