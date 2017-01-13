/**
 *  Chromecast Manager Smartapp
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
	    page(name: "pageDiscovery")
	    page(name: "pageDiscoveryFailed")
	    page(name: "pageSettings")
        page(name: "pageChromecast")
        page(name: "pageReset")
}

def pageMain() {
    app.updateSetting("selectedHue", null)
    state.refreshCount = 0
    def devices = getDevices()
	def selectedDevices = settings.selectedDevices ?: [:]
    def restSetUp = settings.restHost
    def install = selectedDevices && restSetUp
    // FIXME, uninstall
    
    dynamicPage(name: "pageMain", title: "", install: install) {
	    section("Chromecast Discovery") {
        	href "pageDiscovery", title: "Discovery", 
            	description: "Tap here to discover and select Chromecasts",
            	state: selectedDevices ? "complete" : "incomplete"
        }
        section("Settings") {
        	href "pageSettings", title: "Webservice Settings",
            	description: "Tap here to configure the webservice server",
                state: restSetUp ? "complete" : "incomplete"
        }
        section("Configure Chromecasts", hideWhenEmpty: true) {
           	selectedDevices.each { uuid ->
               	def device = devices[uuid]
                if (!device) {
                	log.warn "pageMain(): uuid ${uuid} not found"
                    return
                }
               	href "pageChromecast", title: "${device.name}",
					description: "", params: [ uuid: uuid ],
                    state: settings."remoteWebSocket-${uuid}" ? "complete" : "incomplete"
            }
        }
        section("Dev: Reset") {
        	href "pageReset", title: "Reset", description: "", state: "incomplete"
        }
    }
}

def pageDiscovery() {
    int refreshCount = state.refreshCount ?: 0
	state.refreshCount = refreshCount + 1
	def refreshInterval = 3

	def options = [:]
	getVerifiedDevices().each { uuid, device ->
		options[uuid] = device.name
	}

	def numFound = options.size()
	if (numFound == 0) {
		if (state.refreshCount > 60) {
			// three minutes have passed, give up for now
			state.devices = [:]
			app.updateSetting("selectedDevices", "")
			state.refreshCount = 0
			return pageDiscoveryFailed()
		}
	}

	ssdpSubscribe()

	if ((refreshCount % 5) == 0) {
		// discovery request every 15 seconds
		ssdpDiscover()
	} else {
		// device description request every 3 seconds, except when
	    // doing the ssdpDiscover
		verifyDevices()
	}

	dynamicPage(name:"pageDiscovery", title:"Discovery Started!", 
    	refreshInterval: refreshInterval) {
		section("Please wait while we discover your Chromecasts. Discovery can take a little while, in particular if you have multiple Chromecast devices. Select your device below once discovered.") {
			input "selectedDevices", "enum", required:false, title:"Select Chromecasts (${numFound} found)", multiple: true,
            	options: options, submitOnChange: true
		}
	}
}

def pageDiscoveryFailed() {
	dynamicPage(name: "pageDiscoveryFailed", title: "Discovery Failed") {
		section("Failed to discover any Chromecasts. Please confirm that you " +
			"have a Chromecast connected to the same network as your SmartThings " +
    	    "Hub, and that it has power.") {
            paragraph "Press <Back> to try discovery again."
		}
	}
}

def pageSettings() {
	dynamicPage(name: "pageSettings", title: "Webservice Settings") {
    	section("SmartThings needs a webservice that forwards the communication " +
            "from SmartThings to the Chromecast and vice versa. Please enter its URI " + 
            "below, e.g.: https://pure-atoll-25146.herokuapp.com") {
    		input "restHost", "text", title: "URI of REST Service", required: true
        }
    }
}

def pageChromecast(params) {
	//log.debug "params $params"
    def uuid = params.uuid
    def device = getDevices()[uuid]
    //log.debug "device $device"
    
    dynamicPage(name: "pageChromecast", title: "Configure Chromecast \"$device.name\"") {
    	section("Information") {
           	paragraph "Model Name: ${device.modelName}\n" +
				"Local IP: ${device.ip}\n" +
                "Local Port: ${device.port}"
//        	paragraph "${device.modelName}", title: "Model Name"
//            paragraph "${device.ip}", title: "Local IP"
//            paragraph "${device.port}", title: "Local Port"
//            input "localWebSocket-${uuid}", "number", title: "Local Websocket",
//            	required: true, defaultValue: device.port + 1
        }
        section("Remote Access") {
        	input "remoteIP-${uuid}", "text", title: "Remote IP", required: true
            input "remoteWebSocket-${uuid}", "number", title: "Remote Websocket",
            	required: true, defaultValue: device.port + 1
        }
    }
}

def pageReset() {
	state.remove("devices")
    app.updateSetting("selectedDevices", "")
    
	dynamicPage(name: "pageReset", title: "Reset") {
    	section() {
        	paragraph "List of discovered/selected devices has been reset."
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

	addDevices()
//    state.devices = [:]
//	state._devices = [
//    	[
//        	host: castHost,
//            port: castPort,
//        	dni: castHost + (castPort ? (":" + castPort) : ""),
//        	name: castName,
//		]            
//    ]

	// FIXME, always starting over 
//	childDevices.each { deleteChildDevice(it.deviceNetworkId) }

//	state._devices.each { device ->
//    	def existingDevice = getChildDevice(device.dni)
//       	if (existingDevice) log.warn "existingDevice $existingDevice"
//        if (!existingDevice) {
//        	def childDevice = addChildDevice(app.namespace, "Chromecast", device.dni, null,
//            	[name: "Chromecast", label: device.name, completedSetup: true])
//        }
//    }
}

def addDevices() {
	def devices = getVerifiedDevices()
    devices.each { uuid, device ->
    	if (!(uuid in settings.selectedDevices)) {
        	return
        }

		log.debug "addDevices $uuid $device"
        def child = getChildDevice(uuid)
        log.debug "addDevice child $child"
        if (!child) {
        	addChildDevice(app.namespace, "chromecast", uuid, null,
                [name: device.modelName, label: device.name, completedSetup: true])
        } else {
        	// FIXME needs update?
        }
    }
}

def getDevices() {
	state.devices = state.devices ?: [:]
}

def ssdpDiscover() {
    sendHubCommand(new physicalgraph.device.HubAction("lan discovery urn:dial-multiscreen-org:service:dial:1",
    	physicalgraph.device.Protocol.LAN))
    log.debug "ssdpDiscover() sent"
}

def ssdpHandler(evt) {
	def description = evt.description
	// log.trace "Location: $description"

	def hub = evt?.hubId
	def parsedEvent = parseLanMessage(description)

    def uuid = parsedEvent?.ssdpUSN?.toString()?.tokenize(':')[1]
	def ip = convertHexToIP(parsedEvent.networkAddress)
	def port = convertHexToInt(parsedEvent.deviceAddress)
    def ssdpPath = parsedEvent.ssdpPath

	def devices = getDevices()
	def device = devices[uuid]
	if (!device) {
		log.info "ssdpHandler: adding device ${uuid}"
		devices[uuid] = [ip: ip, port: port, ssdpPath: ssdpPath, mac: parsedEvent.mac]
	} else {
		// log.debug "ssdpHandler: updating device ${uuid}"
        def changed = false
        if (device.ip != ip) {
        	log.info "IP changed: ${device.ip} -> ${ip} (uuid ${uuid})"
            device.ip = ip
            changed = true
        }
        if (device.port != port) {
        	log.info "Port changed: ${device.port} -> ${port} (uuid ${uuid})"
            device.port = port
            changed = true
        }
        if (device.ssdpPath != ssdpPath) {
        	log.info "ssdpPath changed: ${device.ssdpPath} -> ${ssdpPath} (uuid ${uuid})"
            device.ssdpPath = ssdpPath
            changed = true
        }
        // to implement this, need to also handle updating of child device (?)
        assert !changed
	}
}

def verifyDevices() {
	def devices = getDevices().findAll { !it.value.verifyStatus }
	devices.each { uuid, device ->
		sendHubCommand(new physicalgraph.device.HubAction([
			method: "GET",
			path: device.ssdpPath,
			headers: [
				HOST: device.ip + ":" + device.port,
			]], null, [callback: "verifyHandler"]))
	}
}

def verifyHandler(hubResponse) {
	def body = hubResponse.xml
    if (!body?.device?.UDN) {
    	log.warn "verifyHandler: no UDN"
    	return
    }
	//log.debug "descriptionHandler ${body?.device?.modelName} ${body?.device?.UDN}"
	def devices = getDevices()
    def uuid = body.device.UDN.text().tokenize(":")[1]
	def device = devices[uuid]
    if (!device) {
		log.warn "verifyHandler: got a description from a device that doesn't exist"
        return
    }

    def modelName = body.device.modelName?.text()
	if (!modelName || 
    	!(modelName in ["Eureka Dongle", "Chromecast Audio", "Google Home"])) {
        log.info "verifyHandler: disregarding unknown device model ${body?.device?.modelName}"
        device.verifyStatus = "other"
        return
    }

	def name = body.device.friendlyName.text()
	device << [name: name, modelName: modelName, verifyStatus: "verified"]
    log.info "verifyHandler: verified device $name"
}

def getVerifiedDevices() {
	getDevices().findAll { it.value.verifyStatus == "verified" }
}

void ssdpSubscribe() {
    subscribe(location, "ssdpTerm.urn:dial-multiscreen-org:service:dial:1", ssdpHandler)
}

private convertHexToIP(hex) {
	[convertHexToInt(hex[0..1]),convertHexToInt(hex[2..3]),convertHexToInt(hex[4..5]),convertHexToInt(hex[6..7])].join(".")
}

private convertHexToInt(hex) {
	Integer.parseInt(hex, 16)
}

def playMedia(child, media) {
    def uuid = child.device.deviceNetworkId
	log.debug "playMedia uuid $uuid $media"
    //def device = getDevices()[uuid]
    
	def body = [
    	host: settings."remoteIP-${uuid}",
        port: settings."remoteWebSocket-${uuid}",
    	media: media
    ]
    def params = [
    	uri: settings.restHost + "/playMedia",
        body: body
    ]
    log.debug "params: $params"

	asynchttp_v1.post(responseHandler, params,
    	[uuid: uuid, event: [status: "playing"]])
}

def stop(child) {
    def uuid = child.device.deviceNetworkId
	log.debug "stop uuid $uuid"
    
	def body = [
    	host: settings."remoteIP-${uuid}",
        port: settings."remoteWebSocket-${uuid}"
    ]
    def params = [
    	uri: settings.restHost + "/stop",
        body: body
    ]
    log.debug "params: $params"

	asynchttp_v1.post(responseHandler, params,
    	[uuid: uuid, event: [status: "stopped"]])
}

def doPause(child) {
    def uuid = child.device.deviceNetworkId
	log.debug "pause uuid $uuid"
    
	def body = [
    	host: settings."remoteIP-${uuid}",
        port: settings."remoteWebSocket-${uuid}"
    ]
    def params = [
    	uri: settings.restHost + "/pause",
        body: body
    ]
    log.debug "params: $params"

	asynchttp_v1.post(responseHandler, params,
    	[uuid: uuid, event: [status: "paused"]])
}

def play(child) {
    def uuid = child.device.deviceNetworkId
	log.debug "play uuid $uuid"
    
	def body = [
    	host: settings."remoteIP-${uuid}",
        port: settings."remoteWebSocket-${uuid}"
    ]
    def params = [
    	uri: settings.restHost + "/play",
        body: body
    ]
    log.debug "params: $params"

	asynchttp_v1.post(responseHandler, params,
    	[uuid: uuid, event: [status: "playing"]])
}

def setVolume(child, volume) {
    def uuid = child.device.deviceNetworkId
	log.debug "setVolume uuid $uuid"
    
	def body = [
    	host: settings."remoteIP-${uuid}",
        port: settings."remoteWebSocket-${uuid}",
        volume: volume
    ]
    def params = [
    	uri: settings.restHost + "/volume",
        body: body
    ]
    log.debug "params: $params"

	asynchttp_v1.post(responseHandler, params, [uuid: uuid])
}

def refresh(child) {
    def uuid = child.device.deviceNetworkId
	log.debug "refresh uuid $uuid"
    
	def body = [
    	host: settings."remoteIP-${uuid}",
        port: settings."remoteWebSocket-${uuid}",
    ]
    def params = [
    	uri: settings.restHost + "/status",
        body: body
    ]
	asynchttp_v1.post(responseHandler, params, [uuid: uuid])
}

def responseHandler(resp, data) {
	//log.debug "response.status: ${resp.status} data ${data}"
    if (resp.hasError()) {
		log.warn "responseHandler: ${resp.getErrorMessage()}"
        return
    }

	log.debug "responseHandler: ${resp.data}"
	def child = getChildDevice(data.uuid)

	def app = resp.json?.status?.applications
    if (app) {
	    child.generateEvent([statusText: app[0].displayName + "\n" + app[0].statusText])

		def playerState = resp.json?.mediaStatus?.playerState
		if (playerState) {
    		def status = playerState.toLowerCase()
	        if (status == "idle") {
	        	status = "stopped"
	        }
		    child.generateEvent([status: status])
	    }
	} else {
    	child.generateEvent([statusText: "Chromecast is idle"])
    	child.generateEvent([status: "stopped"])
    }
    def volume = resp.json?.status?.volume
    if (volume) {
    	child.generateEvent([
        	mute: volume.muted ? "muted" : "unmuted",
        	level: (volume.level * 100.0).toInteger()
    	])
    }
    
}
