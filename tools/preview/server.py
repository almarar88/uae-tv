#!/usr/bin/env python3
"""Desktop preview of the Majlis TV web UI at 1920x1080 with a mock Android bridge.
Uses REAL data: Open-Meteo, TheSportsDB, alquran.cloud, the app's own PrayerCalc (via jar),
and the real AI endpoint (key read from env TV_AI_API_KEY server-side, never sent to the page)."""
import http.server, json, os, subprocess, urllib.request, urllib.parse, functools
WEB = os.path.join(os.path.dirname(__file__), '../../app/src/main/assets/web')
QURAN = os.path.join(os.path.dirname(__file__), '../../app/src/main/assets/quran-uthmani.txt')
JAR = os.environ.get('PRAYER_JAR', '/tmp/prayercli.jar')
UA = {'User-Agent': 'MajlisTV-preview/1.0', 'Accept': 'application/json'}
cache = {}

def ai(system, messages):
    k = os.environ['TV_AI_API_KEY']
    model = os.environ.get('AI_MODEL', 'gemini-flash-lite-latest')
    body = {'contents': [{'role': 'model' if m['role'] == 'assistant' else 'user', 'parts': [{'text': m['text']}]} for m in messages],
            'systemInstruction': {'parts': [{'text': system}]}, 'generationConfig': {'temperature': 0.6, 'maxOutputTokens': 1200}}
    r = urllib.request.Request(f'https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent', json.dumps(body).encode(),
                               {'Content-Type': 'application/json', 'x-goog-api-key': k})
    j = json.load(urllib.request.urlopen(r, timeout=90))
    return ''.join(p.get('text', '') for p in j['candidates'][0]['content']['parts']).strip()

class H(http.server.SimpleHTTPRequestHandler):
    def __init__(s, *a, **kw): super().__init__(*a, directory=WEB, **kw)
    def log_message(s, *a): pass
    def send(s, code, data, ct='application/json; charset=utf-8'):
        b = data.encode() if isinstance(data, str) else data
        s.send_response(code); s.send_header('Content-Type', ct); s.send_header('Content-Length', len(b)); s.end_headers(); s.wfile.write(b)
    def do_GET(s):
        u = urllib.parse.urlparse(s.path); q = dict(urllib.parse.parse_qsl(u.query))
        if u.path in ('/', '/index.html'):
            h = open(os.path.join(WEB, 'index.html'), encoding='utf-8').read().replace('<script src="photos.js">', '<script src="/__mock.js"></script>\n<script src="photos.js">')
            return s.send(200, h, 'text/html; charset=utf-8')
        if u.path == '/__mock.js': return s.send(200, open(os.path.join(os.path.dirname(__file__), 'mock.js'), encoding='utf-8').read(), 'text/javascript')
        if u.path == '/__get':
            try:
                if q['url'] not in cache: cache[q['url']] = urllib.request.urlopen(urllib.request.Request(q['url'], headers=UA), timeout=30).read().decode()
                return s.send(200, cache[q['url']])
            except Exception as e: return s.send(502, str(e))
        if u.path == '/__prayer':
            out = subprocess.run(['java', '-jar', JAR, q['city'], q['y'], q['m'], q['d']], capture_output=True, text=True).stdout.strip()
            return s.send(200, out)
        if u.path == '/__sura':
            n = q['n'] + '|'
            vs = [l.split('|', 2)[2] for l in open(QURAN, encoding='utf-8').read().splitlines() if l.startswith(n)]
            return s.send(200, json.dumps(vs, ensure_ascii=False))
        return super().do_GET()
    def do_POST(s):
        if s.path == '/__ai':
            j = json.loads(s.rfile.read(int(s.headers['Content-Length'])))
            try: return s.send(200, json.dumps({'ok': True, 'text': ai(j['system'], j['messages'])}, ensure_ascii=False))
            except Exception as e:
                msg = getattr(e, 'code', '') ; return s.send(200, json.dumps({'ok': False, 'text': f'AI error {msg}'}))
        s.send(404, '')

http.server.ThreadingHTTPServer(('127.0.0.1', int(os.environ.get('PORT', 8765))), H).serve_forever()
