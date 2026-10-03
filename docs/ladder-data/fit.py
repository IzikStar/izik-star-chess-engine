import math,re,sys
games=[]  # (a,b,score,n)
def load(f,tag):
    for l in open(f):
        if l.startswith('R '):
            p=l.split(); a,b=p[1].split(':'); s,n=p[3].split('/')
            a=a if not a.startswith('E') else a+tag; b=b if not b.startswith('E') else b+tag
            games.append((a,b,float(s),int(n)))
load('low.txt','@0');load('anchor1.txt','@100');load('batch2.txt','@500');load('batch3.txt','@500')
import os
if os.path.exists('batch4.txt'): load('batch4.txt','@500')
players=sorted({x for g in games for x in g[:2]})
fixed={p:float(p[1:].split('@')[0]) for p in players if p.endswith('@500')}
off100=0.0
r={p:fixed.get(p,1500.0) for p in players}
def rating(p):
    if p.endswith('@100'): return float(p[1:].split('@')[0])+off100
    return r[p]
def ll():
    t=0
    for a,b,s,n in games:
        e=1/(1+10**((rating(b)-rating(a))/400)); e=min(max(e,1e-6),1-1e-6)
        t+= s*math.log(e)+(n-s)*math.log(1-e)
    return t
# prior to keep 0% scores finite: virtual half draw per pair
games=[(a,b,s*(n)/(n+1)+0.5*1/(n+1)*1, n) if False else (a,b,(s+0.5)/(n+1)*n,n) for a,b,s,n in games]
step=50
for it in range(400):
    improved=False
    for p in [x for x in players if x not in fixed and not x.endswith('@100')]+['OFF']:
        for d in (step,-step):
            base=ll()
            if p=='OFF': off100+=d
            else: r[p]+=d
            if ll()>base+1e-9: improved=True
            else:
                if p=='OFF': off100-=d
                else: r[p]-=d
    if not improved:
        step/=2
        if step<1: break
for p in sorted([x for x in players if '@' not in x], key=lambda x:r[x]): print(p, round(r[p]))
print('SF@100 offset', round(off100))
