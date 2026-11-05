#!/usr/bin/env python3
"""Fast REV4 source/resource smoke checks. Not a substitute for Android build or device tests."""
from pathlib import Path
from xml.etree import ElementTree

root = Path(__file__).resolve().parent.parent
res = root / 'app/src/main/res'
sources = root / 'app/src/main/java/app/getknit/knit/ui'

def resource_names(locale):
    tree = ElementTree.parse(res / locale / 'strings.xml')
    keys = [n.attrib['name'] for n in tree.getroot() if n.tag == 'string' and n.attrib.get('name', '').startswith('radio_')]
    assert len(keys) == len(set(keys)), f'duplicate radio resources: {locale}'
    return set(keys)

en = resource_names('values')
pt = resource_names('values-pt-rBR')
assert en == pt, f'radio string mismatch: {en ^ pt}'
for f in (sources / 'radiohub/RadioHubScreen.kt', sources / 'radiohub/RadioGroupPolicy.kt', sources / 'contacts/ContactsScreen.kt', sources / 'contacts/GroupPickerPolicy.kt', sources / 'KnitApp.kt'):
    assert f.is_file(), f
hub = (sources / 'radiohub/RadioHubScreen.kt').read_text()
nav = (sources / 'KnitApp.kt').read_text()
contacts = (sources / 'contacts/ContactsScreen.kt').read_text()
assert 'radioGroupThreads(state.conversations)' in hub
assert 'onOpenConversation(group.id)' in hub
assert 'onCallPeer(peer.id)' in hub
assert 'RADIO_GROUP_CONTACTS' in nav
assert 'groupOnly = true' in nav
assert 'groupPickerCanConfirm(selected.size, groupOnly)' in contacts
assert 'onCreateGroup(selected.toList())' in contacts
print(f'PASS: {len(en)} translated radio strings, group-only navigation, group and peer actions')
