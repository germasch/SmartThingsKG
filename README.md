
# KG's SmartThings GitHub Repo

This is forked from the SmartThings Public GitHub Repo
and adds the following:

## Chromecast Manager Smartapp and Chromecast Device Type Handler (alpha)

This app/DTH allows to add Chromecasts (and Google Homes) as SmartThings Devices, which provides basic playback control (pause/play, stop, set volume, mute), as well as playing either a given media file, or using text-to-speech to have the Chromecast to output arbitrary speech.

Since SmartThings does not provide the necessary capabilities to talk to Chromecasts directly, this DTH talks to an intermediary app in the cloud, which itself then opens a websocket to the Chromecast. In order for the cloud app to reach to be able to reach your Chromecast, you have to poke a hole into your firewall / NAT, which is certainly iffy from a security perspective.

## Google Actions (pre-alpha)

Google Actions: Integrates SmartThings using Actions for Google with
the Google Assistant (using conversational actions).

