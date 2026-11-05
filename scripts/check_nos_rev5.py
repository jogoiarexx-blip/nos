"""Static safety gates for NÓS REV5; not a replacement for Android compilation or device tests."""
from pathlib import Path
import xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
res=root/'app/src/main/res'
java=root/'app/src/main/java/app/getknit/knit'
for relative in ('values/strings.xml','values-pt-rBR/strings.xml'):
    doc=ET.parse(res/relative).getroot()
    names=[node.attrib['name'] for node in doc if node.tag=='string' and 'name' in node.attrib]
    assert len(names)==len(set(names)), f'duplicate names in {relative}'
    assert next(node.text for node in doc if node.tag=='string' and node.attrib.get('name')=='app_name')=='NÓS'
    for name in ('nos_group_title','nos_group_disclaimer','nos_group_floor_local','nos_group_hold',
                 'nos_group_recorded','nos_group_queued','nos_group_send_error'):
        assert name in names, (relative,name)
for name in ('ic_launcher.xml','ic_launcher_round.xml'):
    x=(res/'mipmap-anydpi'/name).read_text()
    assert 'nos_monochrome' in x and 'ic_launcher_foreground' in x
for name in ('nos_wordmark.png','nos_monochrome.png'):
    assert (res/'drawable-nodpi'/name).stat().st_size>1000
for density in ('mdpi','hdpi','xhdpi','xxhdpi','xxxhdpi'):
    for name in ('ic_launcher.png','ic_launcher_round.png','ic_launcher_foreground.png'):
        assert (res/f'mipmap-{density}'/name).stat().st_size>100
navigation=(java/'ui/KnitApp.kt').read_text()
assert 'Routes.GROUP_PTT' in navigation and 'GroupPttScreen(' in navigation
hub=(java/'ui/radiohub/RadioHubScreen.kt').read_text()
assert 'nos_group_fast_' in hub and 'radio_group_open_' in hub
screen=(java/'ui/radiohub/GroupPttScreen.kt').read_text()
assert 'tryAwaitRelease' in screen and 'onOpenRecorded(groupId)' in screen
vm=(java/'ui/radiohub/GroupPttViewModel.kt').read_text()
for required in ('attachments.ingestVoice(bytes)', 'mesh.sendChat(', 'current.toGroupInfo()',
                 'GroupPttFloor()', 'recorder.stop()', 'recorder.cancel()'):
    assert required in vm, required
assert 'R.string.nos_group_floor_local' in screen
assert 'GroupPttViewModel(' in (java/'di/UiModule.kt').read_text()
print('PASS: NÓS identity, UI routing, dual audio paths, bounded PTT core, icons, DI and locale strings')
