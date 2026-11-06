"""Writes the A36 line icons (24 dp grid, 1.5 dp stroke, round caps) into a res/drawable dir."""
import os
import sys

out = sys.argv[1]
HDR = '''<?xml version="1.0" encoding="utf-8"?>
<!-- {c} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
'''


def path(d):
    return f'''    <path
        android:pathData="{d}"
        android:strokeWidth="1.5"
        android:strokeColor="#FF000000"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
'''


ICONS = {
    'core_ui_ic_today': ('«Сегодня»: sunrise over the horizon (A36 §5). Line icon, 1.5 dp, round caps.', [
        'M7.5,15.5A4.5,4.5 0,0 1,16.5,15.5', 'M3,15.5H21', 'M7,19H17',
        'M12,6.5V8.5', 'M5.4,9.4L6.8,10.8', 'M18.6,9.4L17.2,10.8']),
    'core_ui_ic_mentor': ('«Наставник»: speech bubble (A36 §5). Line icon, 1.5 dp, round caps.', [
        'M6,4.75H18A2.25,2.25 0,0 1,20.25,7V14A2.25,2.25 0,0 1,18,16.25H12L8,19.5V16.25H6'
        'A2.25,2.25 0,0 1,3.75,14V7A2.25,2.25 0,0 1,6,4.75Z',
        'M8,9H16', 'M8,12H13']),
    'core_ui_ic_week': ('«Неделя»: portico, four columns under a pediment (A36 §5). Line icon, 1.5 dp.', [
        'M3.5,8.5L12,4L20.5,8.5Z', 'M4,10.75H20', 'M6.25,10.75V17.5', 'M10.1,10.75V17.5', 'M13.9,10.75V17.5',
        'M17.75,10.75V17.5', 'M4.5,17.5H19.5', 'M3,20.25H21']),
    'core_ui_ic_me': ('«Я»: bust in a calm line (A36 §5). Line icon, 1.5 dp, round caps.', [
        'M12,4.25A3.75,3.75 0,1 1,12,11.75A3.75,3.75 0,1 1,12,4.25Z', 'M4.75,20.25A7.25,7.25 0,0 1,19.25,20.25']),
    'core_ui_ic_sos': ('SOS: lifebuoy in a sober line (A36 §5, §11 rule 7). Also the quick settings tile icon.', [
        'M12,3A9,9 0,1 1,12,21A9,9 0,1 1,12,3Z', 'M12,8A4,4 0,1 1,12,16A4,4 0,1 1,12,8Z',
        'M5.64,5.64L9.17,9.17', 'M18.36,5.64L14.83,9.17', 'M5.64,18.36L9.17,14.83', 'M18.36,18.36L14.83,14.83']),
    'core_ui_ic_call': ('Phone handset in a line (A36 §5), SOS call buttons.', [
        'M8.6,3.75H6.2A2,2 0,0 0,4.2,5.9C4.75,13.6 10.4,19.25 18.1,19.8A2,2 0,0 0,20.25,17.8V15.4'
        'A1,1 0,0 0,19.55,14.45L16.4,13.4A1,1 0,0 0,15.35,13.7L14,15.3C11.55,14.1 9.9,12.45 8.7,10'
        'L10.3,8.65A1,1 0,0 0,10.6,7.6L9.55,4.45A1,1 0,0 0,8.6,3.75Z']),
    'core_ui_ic_close': ('Close (A36 §5 line style).', ['M6,6L18,18', 'M18,6L6,18']),
}
for name, (c, ds) in ICONS.items():
    with open(os.path.join(out, name + '.xml'), 'w') as f:
        f.write(HDR.format(c=c) + ''.join(path(d) for d in ds) + '</vector>\n')
    print(name)

# preview sheet
cells = []
for i, (name, (c, ds)) in enumerate(ICONS.items()):
    g = ''.join(f'<path d="{d}" fill="none" stroke="#EDE5D3" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>' for d in ds)
    cells.append(f'<g transform="translate({i * 32 + 4},4)">{g}</g>')
with open(os.path.join(sys.argv[2], 'preview_line_icons.svg'), 'w') as f:
    f.write(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {len(ICONS) * 32 + 4} 32"><rect width="100%" height="100%" fill="#0B0E15"/>{"".join(cells)}</svg>')
