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

include "asynchttp_v1"

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
    if (false) {
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
    }
    
    // handle edited, deleted and added aliases
    if (state.aliasDeviceId) {
		def device = deviceFromId(state.aliasDeviceId)

		device.aliases.removeAll { alias ->
			def aliasName = settings."alias-name-${alias.id}"
			!aliasName
        }

       	device.aliases.each { alias ->
			def aliasName = settings."alias-name-${alias.id}"
//            log.debug "aliasName $aliasName alias $alias"
            if (aliasName && aliasName != alias.name) {
           		log.debug "updating alias $alias.name -> $aliasName for device $device.name"
           		alias.name = aliasName
            }
        }
    
	    // handle added aliases
    	if (state.newAliasId) {
    		def newAliasName = settings."alias-name-${state.newAliasId}"
	        if (newAliasName) {
  		      	log.debug "pageManageDevices: adding alias $newAliasName to $device.name"
        		device.aliases << [ id: state.newAliasId, name: newAliasName ]
	            state.newAliasId = null
    	    }
		    state.aliasDeviceId = null
    	}
    }
    
    if (!state.newAliasId) {
    	state.newAliasId = UUID.randomUUID().toString()
    }
    
	dynamicPage(name: "pageManageDevices", title: "Manage devices") {
    	state.devices.each { dev ->
    		section(dev.name) {
            	def room = dev.groupId ? getGroup(dev.groupId).name : "<not set>"
                def descr = dev.aliases*.name?.join("\n")
//                input name: "device-name-enabled-${dev.id}", type: "bool", title: "React to '${dev.name}'",
//                	required: true, defaultValue: true
                href name: "hrefDeviceAliases", title: "Edit Aliases", required: false,
                	description: descr,
                   	page: "pageDeviceAliases", params: [ id: dev.id, newAliasId: state.newAliasId ]
            	paragraph "Room: ${room}"
            }
        }
    }
}

def pageDeviceAliases(params) {
	log.debug "pageDeviceAliases params: $params"
    def dev = deviceFromId(params.id)
    //log.debug "pageDeviceAliases dev: $dev"
    state.aliasDeviceId = params.id
    
//	def delAliasId = params.delAliasId
//    if (delAliasId) {
//    	def delAlias = dev.aliases.find { it.id == delAliasId }
//    	log.debug "deleting $delAlias.name from $dev.name"
//    	dev.aliases.removeAll { it.id == delAliasId }
//    }
    
    log.debug "dev: $dev"
    def allAliases = dev.aliases?.name
    
	dynamicPage(name: "pageDeviceAliases", title: "Aliases for device '${dev.name}'") {
    	section {
        	paragraph "To rename an alias, change it in the 'Rename Alias' field.\n" +
				"To delete an alias, just rename it to <blank>.\n" +
				"To add an alias, type the new name into the 'New Alias' field."
        }
        dev.aliases.each { alias ->
    		section("Edit alias '${alias.name}'") {
				input name: "alias-name-${alias.id}", type: "text", title: "Rename Alias",
    		    	required: false, defaultValue: alias.name
            }
        }
        section("Add Alias") {
        	input name: "alias-name-${params.newAliasId}", type: "text", title: "New Alias",
            	required: false
        }
//        section("Delete Aliases") {
//        	input name: "tmpX", type: "enum", title: "Tap to deselect aliases",
//            	required: false, multiple: true,
//                options: allAliases, defaultValue: allAliases
//        }
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
	log.debug "installed() with settings: ${settings}"

	initialize()
}

def updated() {
	log.debug "updated()"

	unsubscribe()
	initialize()
}

def initialize() {
	// TODO: subscribe to attributes, devices, locations, etc.
    log.info "initialize"

	if (!state.devices) {
    	state.devices = []
    }
	if (!state.groups) {
    	state.groups = []
    }
    state.devices = []
    state.groups = []

	updateDevices()
    updateGroups()
    
    // FIXME, those don't seem to actually work
    subscribe(location, "deviceCreated", deviceUpdatesHandler, [filterEvents: false])
    subscribe(location, "deviceDeleted", deviceUpdatesHandler, [filterEvents: false])
    subscribe(location, "deviceUpdated", deviceUpdatesHandler, [filterEvents: false])
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

def getGroup(groupId) {
	return state.groups.find {it.id == groupId }
}

def getOrCreateGroup(groupId) {
	if (!groupId) {
    	return null
    }
	def group = getGroup(groupId)
    if (!group) {
    	group = [id: groupId, name: "group-$groupId", devices: []]
        state.groups << group
    }
    group
}

def groupName(groupId) {
	return getGroup(groupId).name
}

def updateGroupDevicesFromSettings() {
    state.groups.each { group ->
		def selectedDevices = settings."group-devices-${group.id}"
        if (selectedDevices) {
        	def selected = selectedDevices.collect { deviceName ->
               	state.devices.find { it.name == deviceName }
            }
            log.debug "group.devices $group.devices --- selected $selected*.id"
            if (group.devices != selected*.id) {
            	def prevDevices = group.devices.collect { deviceFromId(it).name }
            	log.debug "updateGroupDevicesFromSettings: group ${group.name}: ${prevDevices} -> ${selected*.name}"
	            group.devices = selected*.id
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

def deleteDevice(id) {
	log.info "deleteDevice: deleting ${deviceFromId(id).name} $id"
	// remove the from state.devices
    state.devices.removeAll { device -> device.id == id }
	// also remove them from groups that referenced them
    state.groups.each { group->
    	group.devices.removeAll { it == id }
    }
}

def addDevice(stDevice) {
	log.info "addDevice: adding $stDevice.displayName $stDevice.id"
    def device = [
    	id: stDevice.id,
        name: stDevice.displayName,
        groupId: stDevice.device.groupId
    ]
	state.devices << device
    if (device.groupId) {
		def group = getOrCreateGroup(device.groupId)
        if (group.devices.contains(device.id)) {
        	log.warn "group.devices: $group.devices --- device $device.id"
        }
		group.devices << device.id
    }
}

def updateDevice(stDevice) {
	log.debug "updateDevice: updating $stDevice.displayName $stDevice.id"
    def device = state.devices.find { it.id == stDevice.id }

	if (device.name != stDevice.displayName) {
    	log.info "renaming $device.name -> $stDevice.displayName"
	    device.name = stDevice.displayName
    }
    def newGroupId = stDevice.device.groupId
    if (device.groupId != newGroupId) {
    	log.info "changing device $curDevice.name groupId $curDevice.groupId -> $device.device.groupId"
        def OrigGroup = getGroup(device.groupId)
        def inOrigGroup = OrigGroup?.devices?.contains(device.id)
        if (inOrigGroup || (newGroupId && !getGroup(newGroupId))) {
    	    // only add to new group if it still was in the original group
	        // (that is, the user hadn't used preference to remove it from that group,
        	// as far as this app is concerned), or if the newGroup is new to us
            def newGroup = getOrCreateGroup(newGroupId)
            if (!newGroup.devices.contains(device.id)) {
		        newGroup.devices << device.id
            }
        }
	    // remove from original group (if in there)
	    origGroup?.devices?.removeAll { it == device.id }
        // update groupId
	    device.groupId = newGroupId
    }
}

def updateDevices() {
	// first, see whether devices have been removed
	def newDeviceIds = switches*.id
    def curDeviceIds = state.devices*.id
    def delDeviceIds = curDeviceIds - newDeviceIds

	// delDeviceIds contains the ids of those devices that have been removed
    delDeviceIds.each {	deleteDevice(it) }

	switches.each { device ->
    	if (state.devices*.id.contains(device.id)) {
        	updateDevice(device)
        } else {
        	addDevice(device)
        }
    }
    
    // debug log
	// state.devices.each { dev ->
	//     log.debug "DEVICE name: $dev.name id: $dev.id groupId: $dev.groupId"
	// }
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
    
    return prefix?.join(" ")
}

def updateGroups() {
	// guess room names from the devices in the room
    state.groups.each { group ->
    	def inputName = settings."group-name-${group.id}"
    	if (inputName) {
        	if (group.name != inputName) {
	        	log.warn "updateGroups: updating ${group.name} -> ${inputName}, id ${group.id}"
    	    	group.name = inputName
            }
        } else {
        	if (group.name == "group-$group.id") {
	    		def deviceNames = group.devices.collect { deviceFromId(it).name }
	        	def guessedName = guessRoomName(deviceNames)
                if (guessedName) {
                	group.name = guessedName
	                log.debug "updateGroups: guessed ${group.name}, id ${group.id}"
                }
            }
        }
    }

	// debug log
   	 state.groups.each { group ->
		def deviceNames = group.devices.collect { deviceId -> stDeviceFromId(deviceId).displayName }
    	log.debug "GROUP name: $group.name groupId: $group.id devices: $deviceNames"
    }
}

def listToSpeech(devices, addArticle=false) {
	if (addArticle) {
		devices = devices.collect { "the $it" }
    }

	def devicesSpeech = ""
    if (devices.size() > 1) {
		 devicesSpeech = devices[0..<-1].join(", ") + " and "
    }
    devicesSpeech += devices.last()
}

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

def apiAiRequestAsync(endpoint, body) {
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
    
    asynchttp_v1.post(apiAiResponseHandler, params)
}

def apiAiResponseHandler(response, data) {
	// log.debug "response.data: $response.data"
    if (response.hasError()) {
		log.error "apiAiResponseHandler: ${response.getErrorMessage()}"
    }
}

def newSession(sessionId) {
	//log.debug("newSession sessionId: $sessionId")
    def entities = [[name: "st_dimmer", devices: dimmers]]
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
    userEntities << [
    	sessionId: sessionId,
        name: "st_switch",
        extend: false,
        // FIXME, should only do this for devices with capability switch
        entries: state.devices.collect { dev ->
        	[ value: dev.id, synonyms: dev.aliases*.name ]
        }
    ]
    
	log.debug "newSession: userEntities $userEntities"
    apiAiRequestAsync("userEntities", userEntities)
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
    makeResponse speech
}

def onoffREST(result) {
    def deviceIds = result.parameters.devices
	def originalDevices = result.parameters."devices-original"
    def onoff = result.parameters."on-off"
    assert ["on", "off"].contains(onoff)

	def stDevices = deviceIds.collect { stDeviceFromId(it) }
    log.debug "onoffREST: stDevices: $stDevices onoff: $onoff"
	stDevices*."${onoff}"()

	makeResponse "Okay, turning ${onoff} ${listToSpeech(originalDevices, true)}."
}

def listSwitchesREST() {
	log.debug "listSwitchesREST"
	def speech = "Here are your switches. "

//	switches.each {
//		log.debug "switch: ${it.displayName} ${it.currentValue('switch')} supported ${it.supportedAttributes} capabilities ${it.capabilities}"
//		log.debug "properties ${it.properties} device ${it.device} device.properties ${it.device.properties}"
//		log.debug "group: ${it.device.groupId}"
//	    def attrs = it.getSupportedAttributes()
//        attrs.each { attr ->
//        	log.debug "attr: $attr getValues ${attr.getValues()}"
//        }
//	}

	// FIXME, for switches only
	state.devices.each { device ->
    	def stDevice = stDeviceFromId(device.id)
		speech += device.name + " is " + stDevice.currentValue("switch") + ". ";
	}

	makeResponse speech
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
