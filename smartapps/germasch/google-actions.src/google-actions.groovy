/**
 *  Google Actions
 *
 *  Copyright 2016 Kai Germaschewski
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */

// TODO:
// update icons
// think about how to best name this app

definition(
    name: "${textAppName()}",
    namespace: "germasch",
    author: "Kai Germaschewski",
    description: "Interface with Actions on Google / Google Assistant",
    category: "Convenience",
    singleInstance: true,
    // FIXME icons
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth: true
)
{
	appSetting "API.AI client access token"
}

preferences(oauthPage: "pageDevices") {
    page(name: "pageMain", title: "${textAppName()}", install: true) {
    	section("Settings") {
	        href(name: "hrefDevices", title: "Select devices", required: false,
    			description: "tap to select devices that ${textAppName()} can control",
                page: "pageDevices")
            href(name: "hrefManageGroups", title: "Manage groups", required: false,
                description: "tap to manage your rooms/groups", page: "pageManageGroups")
            href(name: "hrefManageDevices", title: "Manage devices", required: false,
                description: "tap to manage your devices", page: "pageManageDevices")
        }
        section("About") {
			href(name: "hrefAbout", title: "About ${textAppName()}", required: false,
            	page: "pageAbout")
        }
    }
	page(name: "pageDevices", title: "Select devices") {
		section("Allow ${textAppName()} to control the following devices.") {
     	 	input "switches", "capability.switch", title: "Choose Switches/Lights (on/off)", required: false, multiple: true
        	input "dimmers", "capability.switchLevel", title: "Choose Dimmers (on/off/level)", required: false, multiple: true
            input "coloredLights", "capability.actuator", title: "Choose Colored Lights (on/off/level/color)", required: false, multiple: true
	    }
    }
    page(name: "pageManageGroups")
    page(name: "pageManageDevices")
    page(name: "pageAbout")
}

def pageManageGroups() {
	dynamicPage(name: "pageManageGroups", title: "Manage groups / rooms") {
    	// FIXME, do own groups
		state.groups.each { group ->
        	def name = group.name ? group.name : "<none>"
    		section(name) {
            	paragraph "name: $group.name"
                input name: "group.Name", type: "text", title: "Rename",
                	required: false, defaultValue: group.name
            }
        }
    }
}

def pageManageDevices() {
	dynamicPage(name: "pageManageDevices", title: "Manage devices") {
    	section {
        	state.devices.each { dev ->
            	paragraph "name: $dev.name group: $dev.groupId"
            }
        }
    }
}

// FIXME, doesn't need to be dynamic
def pageAbout() {
    dynamicPage(name: "pageAbout", title: "About ${textAppName()}", uninstall: true) {
	   	section {
    		paragraph "${textAppName()} ${textAppVersion()}\n${textCopyright()}"
	    }
        section("Apache License") {
        	paragraph "${textLicense()}"
        }
        section("Instructions") {
        	paragraph "TODO"
        }

//		log.debug "state $state"
//		createAccessToken()
//		log.debug "Creating new Access Token"
//		log.debug "state2 $state"

//        def params = [
//    		uri: "https://graph-na02-useast1.api.smartthings.com/api/groups",
//		    headers: [
//				"Authorization": 'Bearer ' + state.accessToken //"0179a105-4c0d-46fb-8a8e-e24f8839e0b2"
//		    ],
//	    ]
    
//	    httpGet(params) { response ->
//			log.debug "response data: ${response.data}"
//        	if (response.data.status.code < 200 || response.data.status.code >= 300) {
//	        	log.error "session: unexpected status $response.data.status.code"
//			}
//	    }
        
//        revokeAccessToken()
//        state.accessToken = null
    }
}


mappings {
	path("/action")            { action: [ POST: "actionREST"  ] }
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
	// TODO: subscribe to attributes, devices, locations, etc.
    log.warn("initialize")

	updateDevices()
    updateGroups()
}

def updateDevices() {
	// FIXME: want to store things like synonyms, but then of course we can't just
    // start over from an empty list
    // would still have to delete orphaned devices from this list
	state.devices = []
    switches.each { dev ->
		state.devices << [ id: dev.id, name: dev.displayName, groupId: dev.device.groupId ]
    }
    
    // debug log
    state.devices.each { dev ->
    	log.debug "DEVICE name: $dev.name id: $dev.id groupId: $dev.groupId"
    }
}

def guessRoomName(strs) {
	def exclude_list = ["light", "lights", "lamp", "lamps"]

	//log.debug "guessRoomName: $strs"
	if (!strs) {
    	return null
	}
    def prefix = null
    strs.each { str ->
    	def words = str.tokenize(" ")
    	if (!prefix) {
        	prefix = []
        	for (def i = 0; i < words.size(); i++) {
            	if (exclude_list.contains(words[i].toLowerCase())) {
                	break
                }
            	prefix << words[i]
            }
        } else {
        	for (def i = 0; i < Math.min(prefix.size(), words.size()); i++) {
                if (prefix[i] != words[i]) {
                	prefix = prefix.subList(0, i)
                    break
                }
            }
        }
    }
    
    if (!prefix) {
    	return null
    }
    
    def prefix_str = ""
    prefix.each { word ->
    	prefix_str += word
        if (word != prefix.last()) {
        	prefix_str += " "
        }
	}
//    log.debug "guessRoomName: $prefix_str"
    return prefix_str
}

def updateGroups() {
	if (!state.groups) {
    	state.groups = []
    }
	// For all the Smartthings groups (ie., the ones that have an id),
    // clear out the list of devices because we'll re-add these
	state.groups.each { group ->
    	if (group.id) {
        	group.devices = []
        }
    }
    // Create groups for Smartthings groups that we don't have an entry for yet,
    // and build a list of devices in each group
 	switches.each { dev ->
		def groupId = dev.device.groupId
		def group = state.groups.find { g -> g.id == groupId }
        if (group) {
        	group.devices << dev.id
        } else {
	        state.groups << [id: groupId, devices: [dev.id]]
        }
	}

	// guess room names from the devices in the room
    state.groups.each { group ->
        if (!group.name) {
	    	def deviceNames = group.devices.collect { deviceId -> deviceFromId(deviceId).displayName }
	        group.name = guessRoomName(deviceNames)
        }
    }

	// debug log
   	 state.groups.each { group ->
		def deviceNames = group.devices.collect { deviceId -> deviceFromId(deviceId).displayName }
    	log.debug "GROUP name: $group.name groupId: $group.id devices: $deviceNames"
    }


}

// FIXME, use async http API
def apiAiRequest(endpoint, body) {
    def APIAI_HOSTNAME = "api.api.ai"
	def APIAI_ENDPOINT = "/v1/"

    def params = [
    	uri: "https://" + APIAI_HOSTNAME + APIAI_ENDPOINT + endpoint,
	    headers: [
			"Authorization": 'Bearer ' + appSettings."API.AI client access token",
			"api-request-source": 'groovy'
	    ],
        body: body
    ]
    
    httpPostJson(params) { response ->
		log.debug "response data: ${response.data}"
        if (response.data.status.code < 200 || response.data.status.code >= 300) {
        	log.error "session: unexpected status $response.data.status.code"
		}
    }
}

def newSession(sessionId) {
	//log.debug("newSession sessionId: $sessionId")
    def entities = [[name: "st_switch", devices: switches],
    	            [name: "st_dimmer", devices: dimmers]]
    def userEntities = entities.collect { entity ->
        [
            sessionId: sessionId,
        	name: entity.name,
        	extend: false,
            entries: entity.devices.collect {
        		[value: it.displayName, synonyms: [it.displayName]]
        	}
     	]
    }
    
    //log.debug "newSession: userEntities $userEntities"
    apiAiRequest("userEntities", userEntities)
}

def actionREST() {
	log.debug "actionREST: $request.JSON"
    def context_aog = request.JSON.result.contexts.find {
    	it.name == "_actions_on_google_"
    }
    if (!context_aog) {
    	newSession(request.JSON.sessionId)
    } else if (!context_aog.parameters.st_endpoint_uri) {
    	log.warn "_actions_on_google_ context found, but no st_endpoint_uri!"
    	newSession(request.JSON.sessionId)
    }

	def result = request.JSON.result
    log.info "actionREST: ${result?.action}"
    switch (result?.action) {
        case "welcome": return welcomeREST(result)
		case "on-off": return onoffREST(result)
    	case "list-switches": return listSwitchesREST()
    }
    httpError(404, "action not found")
}

def makeResponse(speech) {
	def resp = [ speech: speech ]
    log.info "makeResponse: $resp"
    return resp
}

def welcomeREST(result) {
	def speech = "Welcome to SmartThings at your $location.name! How can I help you?" 
    return makeResponse(speech)
}

def onoffREST(result) {
    def device_names = result.parameters.devices
    def onoff = result.parameters."on-off"
	// FIXME check args (e.g. empty device_names)
    def devices = device_names.collect { name ->
	  // log.debug "name $name switches $switches"
      switches.find {
        // log.debug "it: $it.displayName $name"
        name.equalsIgnoreCase(it.displayName)
      }
    }
    log.debug "onoffREST: devices: $devices onoff: $onoff"
    if (onoff == "on") {
    	devices.each { it.on() }
   	} else {
    	devices.each { it.off() }
    }
    def devices_speech = ""
    device_names.each { 
    	if (it == device_names.last() && it != device_names.first()) {
		   	devices_speech += "and "
        }
        devices_speech += "the ${it} " 
    }
	
    return makeResponse("Turning ${onoff} ${devices_speech}.")
}

def deviceFromId(deviceId) {
	return switches.find { it.id == deviceId }
}

def listSwitchesREST() {
	log.debug "listSwitchesREST: $switches"
	def speech = "Here are your switches. "

	switches.each {
//		log.debug "switch: ${it.displayName} ${it.currentValue('switch')} supported ${it.supportedAttributes} capabilities ${it.capabilities}"
//		log.debug "properties ${it.properties} device ${it.device} device.properties ${it.device.properties}"
//		log.debug "group: ${it.device.groupId}"
//	    def attrs = it.getSupportedAttributes()
//        attrs.each { attr ->
//        	log.debug "attr: $attr getValues ${attr.getValues()}"
//        }
		speech += it.displayName + " is " + it.currentValue("switch") + ". ";
	}

	return makeResponse(speech)
}

// TODO: implement event handlers

// ============================================================
// CONSTANTS
// 
// need to be implemented as functions

private def textAppName() {
	"Google Actions"
}

private def textAppVersion() {
	"v0.0.1"
}

private def textCopyright() {
	"Copyright © 2016 Kai Germaschewski"
}

private def textLicense() {
	"Licensed under the Apache License, Version 2.0 (the 'License'); " +
	"you may not use this file except in compliance with the License. " +
	"You may obtain a copy of the License at" +
	"\n\n" +
	"    http://www.apache.org/licenses/LICENSE-2.0" +
	"\n\n" +
	"Unless required by applicable law or agreed to in writing, software " +
	"distributed under the License is distributed on an 'AS IS' BASIS, " +
	"WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. " +
	"See the License for the specific language governing permissions and " +
	"limitations under the License."
}
