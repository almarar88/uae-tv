#!/usr/bin/env python3
"""Builds app/src/main/assets/web/data/poetry.json from pages actually fetched (cached in /tmp/src).
Verses are copied verbatim. The only normalisation is typographic: kashida (ـ) used for justification by
the DCT encyclopedia is removed, and footnote markers ((1), «1», bare digits) used by Al Bayan are removed.
Nothing is written, completed or corrected by hand. Every hemistich is re-checked against its source text."""
import re, json, sys, html, os
sys.path.insert(0, '/tmp/src'); import dct
S = '/tmp/src/'
def T(u): return dct.get(u)
def txt(u):
    h = re.sub(r'<script.*?</script>|<style.*?</style>', '', T(u), flags=re.S)
    t = html.unescape(re.sub(r'<br\s*/?>', '\n', h)); t = re.sub(r'<[^>]+>', '\n', t)
    return [l.strip() for l in t.split('\n') if l.strip()]
def kash(s): return re.sub(r'\s+-$', '', re.sub(r'\s+', ' ', s.replace('ـ', '')).strip())
def nofn(s): return re.sub(r'\s+', ' ', re.sub(r'\(\d+\)|«\d+»|\d+', ' ', s)).strip()
def pairs(lines): return [[lines[i], lines[i + 1]] for i in range(0, len(lines) - 1, 2)]
def between(lines, start, n):
    i = lines.index(start); return lines[i:i + n]

BAYAN_K = 'https://www.albayan.ae/five-senses/heritage/2017-04-27-1.2928472'
BAYAN_Z = 'https://www.albayan.ae/five-senses/culture/2018-02-25-1.3195693'
BAYAN_KH = 'https://www.albayan.ae/five-senses/heritage/2017-02-23-1.2866722'
BAYAN_KH_BIO = 'https://www.albayan.ae/five-senses/culture/2017-01-08-1.2820080'
BAYAN_O = 'https://www.albayan.ae/five-senses/heritage/2018-04-28-1.3249354'
EY_Z = 'https://www.emaratalyoum.com/life/culture/2023-10-16-1.1791402'
ARCH_K = 'https://archive.org/details/ahmadalkindi'
H7 = 'http://www.7awaya.com/poemPage.do?poemId='
SRC = {'dct': 'الموسوعة الشعرية — دائرة الثقافة والسياحة أبوظبي', 'bayan': 'صحيفة البيان', 'ey': 'صحيفة الإمارات اليوم',
       '7awaya': 'حوايا — واحة الشعر الشعبي', 'archive': 'أرشيف الإنترنت (archive.org)'}
def dctpoem(path):
    d = dct.poem(path); return [[kash(a), kash(b)] for a, b in d['verses']], d['url']
def seven(pid): return [l.split('|') for l in open(S + f'7a_{pid}.txt', encoding='utf-8').read().strip().split('\n')]
checks = []
def poem(title, verses, src, url, wisdom, note=None, check=None):
    if check: checks.append((check, verses, url))
    p = {'title': title, 'verses': verses, 'source': SRC[src], 'url': url, 'wisdom': wisdom}
    if note: p['note'] = note
    return p

# ── Ahmed bin Ali Al Kindi Al Marar
kl = txt(BAYAN_K); i = kl.index('شعر: أحمد بن علي الكندي')
kv = pairs([nofn(l) for l in kl[i + 1:i + 27]])
# ── Ousha bint Khalifa Al Suwaidi
o1, u1 = dctpoem('/poems/199634-%D9%85%D9%86-%D8%B9%D8%A7%D8%B4%D8%B1-%D8%A7%D9%84%D8%A3%D9%8A%D8%A7%D9%85-%D9%85%D8%A7-%D9%86%D8%A7%D9%84-%D9%85%D8%B7%D9%84%D9%88%D8%A8')
o2, u2 = dctpoem('/poems/199631-%D8%A7%D9%84%D8%B4%D8%B9%D8%B1-%D9%88%D8%A7%D9%84%D9%81%D8%B5%D8%A7%D8%AD%D9%87-%D9%85%D9%86-%D8%A7%D9%81%D9%86%D9%88%D9%86-%D8%A7%D9%84%D8%B9%D8%B1%D8%A8')
o3, u3 = dctpoem('/poems/199824-%D9%8A%D9%88%D9%85-%D8%A7%D9%84%D8%AF%D8%AC%D9%89-%D9%88%D8%A7%D9%84%D9%84%D9%8A%D9%84-%D8%AC%D9%86%D9%8A')
# ── Sheikh Zayed (excerpts quoted in Al Bayan)
zl = txt(BAYAN_Z)
z1 = pairs([kash(x) for x in zl[zl.index('بوصيـك جانِكْ رجْل تِعْلَـمْ'):][:4]])
z2 = pairs(zl[zl.index('احذر ولا تشتط تندم'):][:8])
z3 = [[l] for l in zl[zl.index('حايز سمت ومعاني وحامي كل شرفه'):][:6]]
# ── Sheikh Mohammed bin Rashid
m1, mu = dctpoem('/poems/198728-%D8%B2%D9%84%D8%B2%D9%84-%D8%A7%D9%84%D8%A3%D8%B1%D8%B6-%D9%85%D8%B5%D8%A7%D8%A8-%D8%A7%D9%84%D8%B9%D8%B1%D8%A8')
# ── Rashid Al Khidr
hl = txt(BAYAN_KH); i = hl.index('شعر: راشد بن سالم الخضر')
hv = [nofn(l) for l in hl[i + 1:i + 22] if 'Ⅶ' not in l]
hv = pairs(hv[:16])
# ── Mubarak bin Hamad Al Oqaili (Nabati, Al Bayan)
ol = txt(BAYAN_O); i = ol.index('شعر: مبارك بن حمد العقيلي')
ov = pairs([nofn(l) for l in ol[i + 1:i + 31]])

POETS = [
 {'id': 'kindi', 'name': 'أحمد بن علي الكندي المرر', 'short': 'الكندي',
  'bio': 'اشتهر باسم الكندي وهو اسمه العائلي، وينتمي لقبيلة المرر من قبائل بني ياس. كانت الربابة رفيقته عبر الشاشة والإذاعة، وظلّت القصائد الغرامية الطابع المميز لشعره.',
  'bioSource': SRC['archive'] + ' — ديوان أحمد بن علي الكندي', 'bioUrl': ARCH_K,
  'poems': [poem('من تقنع النفس', seven(14644), '7awaya', H7 + '14644', [[4], [6], [9]]),
            poem('حمام ناح', kv, 'bayan', BAYAN_K, [[7]], note='من زاوية «من عيون الشعر الإماراتي»', check=('bayan', BAYAN_K))]},
 {'id': 'ousha', 'name': 'عوشة بنت خليفة السويدي', 'short': 'فتاة العرب',
  'bio': 'لقبها «فتاة العرب»، وكانت تنشر قصائدها باسم مستعار هو «فتاة الخليج». وفي عام 1989م أهداها الشيخ محمد بن راشد آل مكتوم ديواناً كتب فيه أبياتاً سمّاها فيها «فتاة العرب»، فصار لقبها.',
  'bioSource': SRC['dct'], 'bioUrl': 'https://poetry.dct.gov.ae/poets/8440',
  'poems': [poem('من عاشر الأيام ما نال مطلوب', o1, 'dct', u1, [[0, 1], [2]], check=('dct', u1)),
            poem('الشعر والفصاحه من افنون العرب', o2, 'dct', u2, [[0]], check=('dct', u2)),
            poem('يوم الدّجى واللّيل جنّي', o3, 'dct', u3, [], check=('dct', u3))]},
 {'id': 'zayed', 'name': 'الشيخ زايد بن سلطان آل نهيان', 'short': 'زايد',
  'bio': 'صدر ديوانه بأعماله الشعرية الكاملة عن لجنة إدارة المهرجانات والبرامج الثقافية والتراثية في أبوظبي، ويضم 71 قصيدة ذاتية و78 قصيدة متبادلة مع شعراء آخرين.',
  'bioSource': SRC['ey'], 'bioUrl': EY_Z,
  'poems': [poem('بوصيك', z1, 'bayan', BAYAN_Z, [[0, 1]], note='أبيات مقتبسة في مقال للبيان عن قصائد الحكمة عند زايد', check=('bayan', BAYAN_Z)),
            poem('احذر ولا تشتط', z2, 'bayan', BAYAN_Z, [[0, 1], [2, 3]], note='أبيات مقتبسة في المقال نفسه', check=('bayan', BAYAN_Z)),
            poem('حايز سمت ومعاني', z3, 'bayan', BAYAN_Z, [[0], [5]], note='أبيات مقتبسة في المقال نفسه', check=('bayan', BAYAN_Z))]},
 {'id': 'mbr', 'name': 'الشيخ محمد بن راشد آل مكتوم', 'short': 'محمد بن راشد', 'bio': None,
  'poems': [poem('زلزَل الأرضَ مُصابُ العَرَبِ', m1, 'dct', mu, [[9]], note='في رثاء الشيخ زايد', check=('dct', mu))]},
 {'id': 'majidi', 'name': 'الماجدي بن ظاهر', 'short': 'ابن ظاهر',
  'bio': 'عاش في بوادي الإمارات متنقلاً، ويعتز به أهل الإمارات لما حوته قصائده من الحكمة والوصف. دُفن في منطقة الخران في بادية رأس الخيمة.',
  'bioSource': SRC['dct'], 'bioUrl': 'https://poetry.dct.gov.ae/poets/8438',
  'poems': [poem('وفي الناس أجناس بخيل أو جيد', seven(10183), '7awaya', H7 + '10183', [[0], [2]])]},
 {'id': 'khidr', 'name': 'راشد بن سالم الخضر', 'short': 'راشد الخضر',
  'bio': 'اسمه راشد بن سالم بن جبران السويدي، وُلد في عجمان عام 1905 وتوفي عام 1980. بدأ نظم الشعر في الثانية عشرة، وكتب في النبطي والفصيح.',
  'bioSource': SRC['bayan'], 'bioUrl': BAYAN_KH_BIO,
  'poems': [poem('قصيد', hv, 'bayan', BAYAN_KH, [[2]], note='من زاوية «من عيون الشعر الإماراتي»', check=('bayan', BAYAN_KH))]},
 {'id': 'oqaili', 'name': 'مبارك بن حمد العقيلي', 'short': 'العقيلي',
  'bio': 'من شعراء الإمارات وفحول عصره، جمع بين الإبداع في الشعر الفصيح والعامي. وُلد بالأحساء وهاجر صغيراً إلى دبي فاستقر فيها، ومنها انطلقت شهرته.',
  'bioSource': SRC['dct'], 'bioUrl': 'https://poetry.dct.gov.ae/poets/2781',
  'poems': [poem('يقول العقيلي في رسوم المثايل', ov, 'bayan', BAYAN_O, [[12], [14], [9]], note='من زاوية «من عيون الشعر الإماراتي»', check=('bayan', BAYAN_O))]},
]
# verification: every hemistich must appear in the fetched source text (after the typographic normalisation)
bad = 0
for (kind, url), verses, _ in checks:
    raw = ' '.join(txt(url)); norm = kash(nofn(raw)) if kind == 'bayan' else kash(raw)
    for v in verses:
        for h in v:
            if h not in norm: print('NOT FOUND', url, h); bad += 1
for p in POETS:
    for q in p['poems']:
        for w in q['wisdom']:
            for k in w: assert 0 <= k < len(q['verses']), (p['id'], q['title'], k)
        assert all(1 <= len(v) <= 2 and all(x.strip() for x in v) for v in q['verses']), q['title']
if bad: sys.exit(f'{bad} hemistichs not found in sources')
out = {'note': 'كل بيت منقول حرفياً من المصدر المذكور. حُذفت فقط الكشيدة (ـ) وأرقام الحواشي وشرطة زائدة في آخر الشطر.', 'built': '2026-10-24', 'poets': POETS}
dst = os.path.join(os.path.dirname(__file__), '../../app/src/main/assets/web/data/poetry.json')
os.makedirs(os.path.dirname(dst), exist_ok=True); json.dump(out, open(dst, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
for p in POETS: print(p['short'], sum(len(q['verses']) for q in p['poems']), [len(q['verses']) for q in p['poems']])
# WebView loads file:///android_asset, where fetch() of JSON is blocked; ship the same data as a script too.
open(dst[:-5] + '.js', 'w', encoding='utf-8').write('// Generated by tools/poetry/build.py from data/poetry.json — do not edit by hand.\nwindow.POETRY_DATA=' + json.dumps(out, ensure_ascii=False) + ';\n')
