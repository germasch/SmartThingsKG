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


// FIXME: use / don't use parentheses for href

preferences(oauthPage: "pageDevices") {
	page(name: "pageMain")
	page(name: "pageDevices", title: "Select devices") {
		section("Allow ${textAppName()} to control the following devices.") {
     	 	input "switches", "capability.switch", title: "Choose Switches/Lights (on/off)", required: false, multiple: true
        	input "dimmers", "capability.switchLevel", title: "Choose Dimmers (on/off/level)", required: false, multiple: true
            input "coloredLights", "capability.actuator", title: "Choose Colored Lights (on/off/level/color)", required: false, multiple: true
	    }
    }
    page(name: "pageManageDevices")
    	page(name: "pageDeviceAliases")
    page(name: "pageManageGroups")
    	page(name: "pageRenameGroup")
    page(name: "pageAbout")
    page(name: "pageUninstall")
}

def pageMain() {
	// This gets called after selecting "Done" on pageManageGroups, so
    // we should to update state.groups here (as early as possible)
	updateGroupDevicesFromSettings()
//    updateDeviceNameEnabledFromSettings()
    
    dynamicPage(name: "pageMain", title: "${textAppName()}", install: true) {
    	section("Settings") {
	        href(name: "hrefDevices", title: "Select devices", required: false,
            	description: "",
//    			description: "tap to select devices that ${textAppName()} can control",
                page: "pageDevices")
            href(name: "hrefManageDevices", title: "Manage devices", required: false,
            	description: "",
//                description: "tap to manage your devices",
                page: "pageManageDevices")
            href(name: "hrefManageGroups", title: "Manage groups", required: false,
            	description: "",
//                description: "tap to manage your rooms/groups",
				page: "pageManageGroups")
        }
        section("About") {
			href(name: "hrefAbout", title: "About ${textAppName()}", required: false,
            	description: "", page: "pageAbout")
        }
        section("Uninstall") {
        	href(name: "hrefUninstall", title: "Tap to uninstall", required: false,
            	description: "", page: "pageUninstall")
        }
    }
}

def pageManageGroups() {
	// This gets called after selecting "Done" on pageRenameGroup, so
    // we need to update state.groups here (as early as possible)
    // FIXME, this is all based on device names being unique, which may not be true
    updateGroupNamesFromSettings()
    
	dynamicPage(name: "pageManageGroups", title: "Manage groups / rooms") {
    	// FIXME, this needs work for doing our own groups
        // we probably shouldn't allow changing the devices assigned to a ST group
        def allDevices = state.devices*.name.sort() // FIXME, sorting state.devices would be better

		state.groups.each { group ->
        	if (!group.id) {
            	return
            }
            // FIXME
            def name = group.name ? group.name : "<none>"
    		section(name) {
                def selectedDevices = group.devices.collect { deviceFromId(it).name }.sort()
                input name: "group-devices-${group.id}", type: "enum", title: "Devices", required: false,
                	options: allDevices, multiple: true, defaultValue: selectedDevices
				href name: "hrefRenameGroup", title: "Rename '$group.name'", required: false,
                	description: "", state: "complete",
                	page: "pageRenameGroup", params: [ id: group.id, name: group.name ]
        	}
        }
    }
}

def pageRenameGroup(params) {
	// FIXME, rename -> "edit" (?)
	//	log.debug "pageRenameGroup params: $params"
	dynamicPage(name: "pageRenameGroup") {
    	section("Edit Group '${params.name}'") {
			input name: "group-name-${params.id}", type: "text", title: "Group Name",
    	    	required: true, defaultValue: params.name
        }
    }
}

def pageManageDevices() {
	// FIXME, temp fixup
   	state.devices.each { dev ->
    	if (!dev.aliases) {
        	dev.aliases = [ dev.name ]
        }
	    if (dev.aliases[0] instanceof String) {
    		dev.aliases = dev.aliases.collect {
        		[ id: UUID.randomUUID().toString(), name: it ]
        	}
	    }
    }
    if (state.newAliasId) {
    	def newAliasName = settings."alias-name-${state.newAliasId}"
        if (newAliasName) {
        	log.debug "pageManageDevices: adding alias $newAliasName to " +
            	"${deviceFromId(state.newAliasDeviceId).name}"
        	deviceFromId(state.newAliasDeviceId).aliases <<
            	[ id: state.newAliasId, name: newAliasName ]
            state.newAliasId = null
        }
	    state.newAliasDeviceId = null
    }
    
    if (!state.newAliasId) {
    	state.newAliasId = UUID.randomUUID().toString()
    }
    
	dynamicPage(name: "pageManageDevices", title: "Manage devices") {
    	state.devices.each { dev ->
    		section(dev.name) {
            	def room = dev.groupId ? groupFromId(dev.groupId).name : "<not set>"
                def descr = dev.aliases*.name?.join("\n")
//                input name: "device-name-enabled-${dev.id}", type: "bool", title: "React to '${dev.name}'",
//                	required: true, defaultValue: true
                href name: "hrefDeviceAliases", title: "Edit Aliases", required: false,
                	description: descr, state: "complete",
                   	page: "pageDeviceAliases", params: [ id: dev.id, name: dev.name, newAliasId: state.newAliasId ]
            	paragraph "Room: ${room}"
            }
        }
    }
}

def pageDeviceAliases(params) {
	log.debug "pageDeviceAliases params: $params"
    def dev = deviceFromId(params.id)
    log.debug "pageDeviceAliases dev: $dev"
    state.newAliasDeviceId = params.id
	dynamicPage(name: "pageDeviceAliases") {
    	section("Aliases for '${params.name}'") {
        	dev.aliases.each {
				input name: "alias-name-${it.id}", type: "text", title: "Edit Alias",
    		    	required: false, defaultValue: it.name
            }
        }
        section {
        	input name: "alias-name-${params.newAliasId}", type: "text", title: "Add Alias",
            	required: false
        }
    }
}

// FIXME, doesn't need to be dynamic
def pageAbout() {
    dynamicPage(name: "pageAbout", title: "About ${textAppName()}") {
	   	section {
    		paragraph "${textAppName()} ${textAppVersion()}\n${textCopyright()}"
	    }
        section("Apache License") {
        	paragraph "${textLicense()}"
        }
        section("Instructions") {
        	paragraph "TODO"
        }
    }
}

def pageUninstall() {
    dynamicPage(name: "pageUninstall", uninstall: true) {
    	section("Uninstall") {
	    	paragraph "Use the button below to uninstall ${textAppName()}.\n" + 
    	    	"You will lose all your settings."
        }
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
    log.warn "initialize"
	updateDevices()
    updateGroups()
    
    // FIXME, those don't seem to actually work
    subscribe(location, "deviceCreated", deviceUpdatesHandler, [filterEvents: false])
    subscribe(location, "deviceDeleted", deviceUpdatesHandler, [filterEvents: false])
    subscribe(location, "deviceUpdated", deviceUpdatesHandler, [filterEvents: false])
    log.debug "done subscribing"
}

def deviceUpdatesHandler(evt) {
	// FIXME, do something
	log.debug "deviceUpdatesHandler: evt $evt"
}

def stDeviceFromId(deviceId) {
	return switches.find { it.id == deviceId }
}

def deviceFromId(deviceId) {
	return state.devices.find { it.id == deviceId }
}

def deviceName(deviceId) {
	return deviceFromId(deviceId).name
}

def groupFromId(groupId) {
	return state.groups.find {it.id == groupId }
}

def groupName(groupId) {
	return groupFromId(groupId).name
}

def updateGroupDevicesFromSettings() {
    state.groups.each { group ->
		def selectedDevices = settings."group-devices-${group.id}"
        if (selectedDevices) {
        	def selected = selectedDevices.collect { deviceName ->
               	state.devices.find { it.name == deviceName }
            }
            if (group.devices != selected?.id) {
            	def prevDevices = group.devices.collect { deviceFromId(it).name }
            	log.debug "updateGroupDevicesFromSettings: group ${group.name}: ${prevDevices} -> ${selected?.name}"
	            group.devices = selected?.id
            }
        }
    }
}

def updateGroupNamesFromSettings() {
	state.groups.each { group ->
        def name = settings."group-name-${group.id}"
        if (name && group.name != name) {
        	log.debug "updateGroupNamesFromSettings: group name ${group.name} -> ${name}"
	        group.name = name
        }
    }
}

// FIXME, gone
def updateDeviceNameEnabledFromSettings() {
	state.devices.each{ device ->
    	def deviceNameEnabled = settings."device-name-enabled-${deviceId}"
        if (device.nameEnabled != deviceNameEnabled) {
        	log.debug "updateDeviceNameEnabledFromSettings: device $device.name: $device.nameEnabled -> $deviceNameEnabled"
        	device.nameEnabled = deviceNameEnabled
        }
    }
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
//    state.devices.each { dev ->
//    	log.debug "DEVICE name: $dev.name id: $dev.id groupId: $dev.groupId"
//    }
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
    
    return prefix.join(" ")
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
		def group = state.groups.find { it.id == groupId }
        if (group) {
        	group.devices << dev.id
        } else {
	        state.groups << [id: groupId, devices: [dev.id]]
        }
	}

	// guess room names from the devices in the room
    state.groups.each { group ->
    	def inputName = settings."group-name-${group.id}"
    	if (inputName) {
        	if (group.name != inputName) {
	        	log.debug "updateGroups: updating ${group.name} -> ${inputName}, id ${group.id}"
    	    	group.name = inputName
            }
        } else {
        	if (!group.name) {
	    		def deviceNames = group.devices.collect { deviceFromId(it).name }
	        	group.name = guessRoomName(deviceNames)
                log.debug "updateGroups: guessed ${group.name}, id ${group.id}"
            }
        }
    }

	// debug log
   	 state.groups.each { group ->
		def deviceNames = group.devices.collect { deviceId -> stDeviceFromId(deviceId).displayName }
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
