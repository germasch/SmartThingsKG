/**
 *  SmartThings-Chromecast
 *
 *  Copyright 2017 Kai Germaschewski
 *
 *  This file is part of SmartThings-Cast
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
 */
 
include "asynchttp_v1"

definition(
    name: "Chromecast Manager",
    namespace: "germasch",
    author: "Kai Germaschewski",
    description: "Integrates Chromecast devices",
    category: "Convenience",
    singleInstance: true,
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	page(name: "pageMain")
}

def pageMain() {
    dynamicPage(name: "pageMain", title: "Chromecast", install: true) {
		section("Settings") {
    		input "restHost", "text", title: "URI of REST Service", required: true
		}
    	section("Chromecast") {
        	input "castName", "text", title: "Name of Cast Device", required: true
    		input "castHost", "text", title: "Hostname/IP of Cast Device", required: true
	        input "castPort", "text", title: "Port of Cast Device", required: false
	    }
	}
}

def installed() {
	log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"

	unsubscribe()
	initialize()
}

def initialize() {
	log.debug "initialize"
    
	state.devices = [
    	[
        	host: castHost,
            port: castPort,
        	dni: castHost + (castPort ? (":" + castPort) : ""),
        	name: castName,
		]            
    ]

	log.debug "devices $state.devices"

	// FIXME, always starting over 
	childDevices.each { deleteChildDevice(it.deviceNetworkId) }
	state.devices.each { device ->
    	def existingDevice = getChildDevice(device.dni)
        if (!existingDevice) {
        	def childDevice = addChildDevice(app.namespace, "Chromecast", device.dni, null,
            	[name: "Chromecast", label: device.name, completedSetup: true])
        }
    }
}

def playMedia(child, media) {
	log.debug "playMedia $child.device.deviceNetworkId $media"
    def device = state.devices.find { it.dni == child.device.deviceNetworkId }

	def body = [
    	host: device.host,
        port: device.port,
    	media: media
    ]
    def params = [
    	uri: settings.restHost + "/play",
        body: body
    ]
    
    asynchttp_v1.post(responseHandler, params)
}

def responseHandler(response, data) {
	log.debug "response.data: ${response?.data}"
    if (response.hasError()) {
		log.error "responseHandler: ${response.getErrorMessage()} (${response?.data})"
    }
}
