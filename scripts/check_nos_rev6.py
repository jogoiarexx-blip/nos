from pathlib import Path
from xml.etree import ElementTree as ET
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / 'app/src/main/res'
CORE = ROOT / 'app/src/main/java/app/getknit/knit'
for file in RES.rglob('*.xml'):
    ET.parse(file)
strings = {}
for name in ('values', 'values-pt-rBR'):
    root = ET.parse(RES/name/'strings.xml').getroot()
    keys = [x.attrib['name'] for x in root if x.tag == 'string']
    assert len(keys) == len(set(keys)), f'duplicate resource key in {name}'
    strings[name] = set(keys)
for code in ['nos_live_title','nos_live_open','nos_live_join','nos_live_connected','nos_tab_messages','nos_tab_radio']:
    assert all(code in x for x in strings.values()), code
assert 'CHANGE_WIFI_MULTICAST_STATE' in (ROOT/'app/src/main/AndroidManifest.xml').read_text()
nav = (CORE/'ui/KnitApp.kt').read_text()
assert 'LanRadioScreen(' in nav and 'Routes.RADIO_LAN_LIVE' in nav
hub = (CORE/'ui/radiohub/RadioHubScreen.kt').read_text()
assert 'nos_open_live_radio' in hub and 'onOpenLiveRadio' in hub
chat = (CORE/'ui/chatlist/ChatListScreen.kt').read_text()
assert 'nos_tab_messages' in chat and 'nos_tab_radio' in chat
assert 'GroupPttScreen(' in nav and 'Routes.GROUP_PTT' in nav
assert 'sendChat(' in (CORE/'ui/radiohub/GroupPttViewModel.kt').read_text()
engine = (CORE/'ui/radiohub/LanRadioEngine.kt').read_text()
for snippet in ('MulticastSocket(', 'AudioRecord(', 'AudioTrack(', 'LanRadioWire.Kind.AUDIO', 'DatagramPacket('):
    assert snippet in engine, snippet
img = Image.open(RES/'drawable-nodpi/nos_wordmark.png')
assert img.mode == 'RGBA' and img.getchannel('A').getextrema() == (0,255)
for scale in ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'):
    for part in ('ic_launcher.png','ic_launcher_round.png','ic_launcher_foreground.png'):
        assert (RES/f'mipmap-{scale}'/part).is_file()
print('REV6 static verification: resources, navigation, audio path, original recordings and NÓS logo OK')
