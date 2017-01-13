/**
 *  Chromecast Device Handler
 *
 *  Copyright 2017 Kai Germaschewski
 *
 *  This file is part of SmartThings-Chromecast
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http:/b/www.gnu.org/licenses/>.
 *
 */
 
metadata {
	definition (name: "Chromecast", namespace: "germasch", author: "Kai Germaschewski") {
		capability "Actuator"
//      capability "Sensor"
//		capability "Switch"
		capability "Music Player"
		capability "Speech Synthesis"
		capability "Refresh"
//		capability "Polling"
        // Health Check?

		attribute "statusText", "string"

        command "testTTS"
        command "testPlayTrack"
    }

	tiles(scale:2) {
    	multiAttributeTile(name: "mediaplayer", type: "mediaPlayer", width:6, height:4) {
			tileAttribute("device.status", key: "PRIMARY_CONTROL") {
				attributeState("stopped", label: "Stopped", defaultState: true)
				attributeState("playing", label: "Playing")
				attributeState("paused", label: "Paused")
				attributeState("startingPlay", label: "-> Play")
				attributeState("startingPause", label: "-> Pause")
			}
            tileAttribute("device.status", key: "MEDIA_STATUS") {
				attributeState("stopped", label: "Stopped")
				attributeState("playing", label: "Playing", action: "music Player.pause", nextState:"startingPause")
				attributeState("paused", label: "Paused", action: "music Player.play", nextState: "startingPlay")
				attributeState("startingPlay", label: "-> Play", action: "music Player.pause", nextState: "startingPause")
				attributeState("startingPause", label: "-> Pause", action: "music Player.play", nextState: "startingPlay")
			}
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState("level", action: "music Player.setLevel")
			}
            tileAttribute ("device.mute", key: "MEDIA_MUTED") {
				attributeState("unmuted", action: "music Player.mute", nextState: "muted", defaultState: true)
				attributeState("muted", action: "music Player.unmute", nextState: "unmuted")
//              attributeState("muting", action: "music Player.unmute", nextState: "unmuting")
//				attributeState("unmuting", action: "music Player.mute", nextState: "muting")
			}
			tileAttribute("device.statusText", key: "MARQUEE") {
            	attributeState("statusText", label:"${currentValue}", defaultState: true)
	        }
        }
	    standardTile("status", "device.status", width: 2, height: 2, canChangeIcon: true) {
        	// FIXME
            state "stopped", label:'Stopped', icon:"st.Electronics.electronics16", backgroundColor:"#ffffff"
            state "playing", label:'Playing', icon:"st.Electronics.electronics16", backgroundColor:"#79b821", action:"Music Player.pause", nextState: "startingPause"
            state "paused" , label:'Paused' , icon:"st.Electronics.electronics16", backgroundColor:"#ffa81e", action:"Music Player.play", nextState: "startingPlay"
            state "startingPlay", label:'-> Play' , icon:"st.Electronics.electronics16", backgroundColor:"#ffa81e", action:"Music Player.pause", nextState: "startingPause"
            state "startingPause", label:'-> Pause', icon:"st.Electronics.electronics16", backgroundColor:"#79b821", action:"Music Player.play", nextState: "startingPlay"
        }
        standardTile("stop", "device.status", width: 2, height: 2, inactiveLabel: false) {
            state "default", label: "Stop", action: "Music Player.stop"
        }
        standardTile("refresh", "device.status", width: 2, height: 2, inactiveLabel: false, decoration: "flat") {
            state "default", icon:"st.secondary.refresh", backgroundColor:"#FFFFFF", action:"refresh.refresh", defaultState:true
        }
        standardTile("testPlayTrack", "device.status", width: 2, height: 2, inactiveLabel: false ) {
            state "default", label: "Test", action: "testPlayTrack"
        }
        standardTile("testTTS", "device.status", width: 2, height: 2, inactiveLabel: false) {
            state "default", label: "TTS", action: "testTTS"
        }
        
        main("status")
        details(["mediaplayer", "stop", "refresh", "testPlayTrack", "testTTS"])
	}

	simulator {
		// TODO: define status and reply messages here
	}
}

def installed() {
	log.debug "installed()"
	sendEvent([name:'status', value:'stopped', displayed:false])
}

// parse events into attributes
def parse(String description) {
	log.error "Parsing '${description}'"
}

def generateEvent(results) {
	log.debug "generateEvent $results"
	results.each { name, value ->
    	sendEvent(name: name, value: value)
	}
}

// capability "Music Player"

def mute() {
	parent.setVolume(this, [muted: true])
}

def unmute() {
	parent.setVolume(this, [muted: false])
}

def setLevel(level) {
	parent.setVolume(this, [level: level/100.0 ])
}

def stop() {
	parent.stop(this)
}

def pause() {
	parent.doPause(this)
}

def play() {
	parent.play(this)
}

def playTrack(uri) {
	// FIXME, audio/mpeg isn't always right
	def media = [
		contentId: uri,
		contentType: "audio/mpeg",
		streamType: "BUFFERED"
    ]
    // FIXME, set title if we can?
    parent.playMedia(this, media)
}

//def nextTrack() {
//}

//def previousTrack() {
//}

//def restoreTrack() {
//}

//def resumeTrack() {
//}

//def setTrack() {
//}

// capability "Refresh"

def refresh() {
	parent.refresh(this)
}

// capability "Speech Synthesis"

def speak(text) {
    log.debug "speak: $text"
    
   	def speech = textToSpeech(text)
    log.debug "speech: $speech"
    
    def media = [
		contentId: speech.uri,
		contentType: "audio/mpeg",
		streamType: "LIVE"
    ]
    // FIXME, show text as well    
    parent.playMedia(this, media)
}

def testTTS() {
    log.debug "testTTS()"
    def text = "Hi there, how are you?"
	speak(text)
}

def testPlayTrack() {
	log.debug "testPlayTrack()"
	def uri = "http://fishercat.sr.unh.edu/Vaeltaja_-_Sommerregen.mp3"
	playTrack(uri)
}

