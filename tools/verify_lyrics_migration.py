import pathlib, re, sqlite3
source = (pathlib.Path(__file__).resolve().parents[1] / 'app/src/main/java/com/example/data/LyricsDatabase.kt').read_text(encoding='utf-8')
commands = re.findall(r'db.execSQL\("((?:ALTER TABLE|UPDATE) cached_lyrics[^\"]+)"\)', source)
assert len(commands) == 9
db = sqlite3.connect(':memory:')
db.execute('CREATE TABLE cached_lyrics (id TEXT PRIMARY KEY NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, lyricsJson TEXT NOT NULL, bpm INTEGER NOT NULL, hexColorsJson TEXT NOT NULL, genre TEXT, timestamp INTEGER NOT NULL)')
row = ('saved-1', '夜に駆ける', 'ヨアソビ', '[{"timeSec":1,"text":"original"}]', 120, '[]', 'Manual Edit', 123456)
db.execute('INSERT INTO cached_lyrics VALUES (?,?,?,?,?,?,?,?)', row)
for sql in commands: db.execute(sql)
assert db.execute('SELECT id,title,artist,lyricsJson,bpm,hexColorsJson,genre,timestamp FROM cached_lyrics').fetchone() == row
assert db.execute('SELECT manuallySelected FROM cached_lyrics').fetchone() == (1,)
columns = {r[1]: (r[2], r[3], r[4]) for r in db.execute('PRAGMA table_info(cached_lyrics)')}
assert columns['durationMs'] == ('INTEGER', 1, '0')
assert columns['synced'] == ('INTEGER', 1, '0')
assert columns['manuallySelected'] == ('INTEGER', 1, '0')
db.execute("UPDATE cached_lyrics SET provider='netease', providerId='123', manuallySelected=1, synced=1")
assert db.execute('SELECT provider,providerId,manuallySelected,synced FROM cached_lyrics').fetchone() == ('netease', '123', 1, 1)
print('PASS: all 9 migration statements, saved lyrics preserved, known manual edits pinned, schema defaults and selection round trip')

