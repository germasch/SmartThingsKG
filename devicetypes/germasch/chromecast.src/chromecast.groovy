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
//		capability "Actuator"
//		capability "Switch"
		capability "Music Player"
		capability "Speech Synthesis"
//		capability "Refresh"
//		capability "Polling"
        
        command "testTTS"
        command "testPlayTrack"
    }

	tiles {
        standardTile("testPlayTrack", "device.status", width: 1, height: 1, inactiveLabel: false ) {
            state "default", label: "Play", action: "testPlayTrack"
        }
        standardTile("testTTS", "device.status", width: 1, height: 1, inactiveLabel: false) {
            state "default", label: "TTS", action: "testTTS"
        }
        
        // main()...
        details(["testPlayTrack", "testTTS"])
	}

	simulator {
		// TODO: define status and reply messages here
	}
}

// parse events into attributes
def parse(String description) {
	log.debug "Parsing '${description}'"

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

def speak(text) {
    log.debug "speak: $text"
    
   	def speech = textToSpeech(text)
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
    def text = "Good night, Yin!"
	speak(text)
}

def testPlayTrack() {
	log.debug "testPlayTrack()"
	def uri = "http://fishercat.sr.unh.edu/Vaeltaja_-_Sommerregen.mp3"
	playTrack(uri)
}

