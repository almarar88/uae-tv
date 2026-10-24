import re, os, subprocess, time
D = '/tmp/src/j/'; os.makedirs(D, exist_ok=True)
def get(u):
    fn = D + re.sub(r'[^A-Za-z0-9]', '_', u)[-150:]
    if not os.path.exists(fn) or os.path.getsize(fn) < 500:
        for a in range(3):
            subprocess.run(['curl', '-s', '-m', '60', 'https://r.jina.ai/' + u, '-o', fn])
            if os.path.exists(fn) and os.path.getsize(fn) > 500: break
            time.sleep(3)
    return open(fn, encoding='utf-8', errors='replace').read()
def poet(pid, start=0):
    t = get(f'http://www.7awaya.com/poetPage.do?poetId={pid}' + (f'&start={start}' if start else ''))
    return t
PX = r'!\[Image \d+\]\([^)]*pixel\.gif\)'
def poem(pid):
    u = f'http://www.7awaya.com/poemPage.do?poemId={pid}'; t = get(u)
    title = (re.search(r'القصيدة\s*:\s*\*\*(.+?)\*\*', t) or [None, ''])[1].strip()
    poetn = (re.search(r'الشاعر:\s*\[([^\]]+)\]', t) or [None, ''])[1].strip()
    pages = int((re.search(r'عدد الصفحات\s*:\s*(\d+)', t) or [None, '0'])[1])
    rows = re.findall(r'(?:^|\s)(\d+) ([^!\n]+?) ' + PX + PX + r'([^!\n]+?) ' + PX, t)
    return {'url': u, 'title': title, 'poet': poetn, 'pages': pages, 'n': [int(r[0]) for r in rows], 'verses': [[r[1].strip(), r[2].strip()] for r in rows], 'raw': t}
