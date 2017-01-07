/**
 *  Google Actions
 *
 *  Copyright 2016 Kai Germaschewski
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
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
	    }
    }
    page(name: "pageManageGroups")
    	page(name: "pageRenameGroup")
    page(name: "pageManageDevices")
    	page(name: "pageDeviceAliases")
    page(name: "pageAbout")
    page(name: "pageUninstall")
}

def pageMain(params) {
	log.debug "pageMain params $params"
	// FIXME: the app should be first installed through OAuth, should make sure it was
    
    // should really be called on submit from pageDevices
    updateDevicesFromSettings()
    // should really be called on submit from pageManageGroups (IIRC)
	if (state.submitManageGroups) {
		updateGroupDevicesFromSettings()
        state.submitManageGroups = false
    }
    
    dynamicPage(name: "pageMain", title: "${textAppName()}", install: true) {
    	section("Settings") {
	        href(name: "hrefDevices", title: "Select devices", required: false,
            	description: "",
                page: "pageDevices")
            href(name: "hrefManageGroups", title: "Manage groups", required: false,
            	description: "",
				page: "pageManageGroups")
            href(name: "hrefManageDevices", title: "Manage devices", required: false,
            	description: "",
                page: "pageManageDevices")
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
    
    // FIXME, this isn't perfect, either, because it'll also be set if using "Back"
    state.submitManageGroups = true
    
	dynamicPage(name: "pageManageGroups", title: "Manage groups / rooms") {
    	// FIXME, this needs work for doing our own groups
        // we probably shouldn't allow changing the devices assigned to a ST group
        // FIXME, sorting state.devices would be better (but state.devices is a HashMap)
		def allDevices = state.devices.collect { deviceId, device ->
        	["$deviceId": device.name]
        }
        allDevices.sort { it.values().toArray()[0] }
 
		state.groups.each { groupId, group ->
    		section(group.name) {
                def selectedDevices = group.devices.findResults { state.devices[it]?.id }
                input name: "group-devices-${groupId}", type: "enum", title: "$group.name Devices", required: false,
                	options: allDevices, multiple: true, defaultValue: selectedDevices
				href name: "hrefRenameGroup", title: "Rename '$group.name'", required: false,
                	description: "", state: "complete",
                	page: "pageRenameGroup", params: [ id: groupId, name: group.name ]
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
	// if state.aliasDeviceId is set, we return from pageDeviceAliases,
    // so handle deleted, edited and added aliases
    if (state.aliasDeviceId) {
		def device = state.devices[state.aliasDeviceId]

		// remove aliases which now have an empty name
		device.aliases.removeAll { alias ->
			!settings."alias-name-${alias.id}"
        }

		// update aliases with new name(s)
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
    	}
	    state.aliasDeviceId = null
    }
    
    if (!state.newAliasId) {
    	state.newAliasId = UUID.randomUUID().toString()
    }
    
	dynamicPage(name: "pageManageDevices", title: "Manage devices") {
    	def sortedDevices = state.devices as LinkedHashMap
		sortedDevices = sortedDevices.sort { it.value.name }
    	sortedDevices.each { deviceId, device ->
    		section(device.name) {
            	def room = device.groupId ? state.groups[device.groupId].name : "<not set>"
            	paragraph "${room}", title: "ST Room"
                def descr = device.aliases*.name?.join("\n")
//                input name: "device-name-enabled-${dev.id}", type: "bool", title: "React to '${dev.name}'",
//                	required: true, defaultValue: true
                href name: "hrefDeviceAliases", title: "Aliases (tap to edit)", required: false,
                	description: descr, state: "complete",
                   	page: "pageDeviceAliases", params: [ id: deviceId, newAliasId: state.newAliasId ]
            }
        }
    }
}

def pageDeviceAliases(params) {
	log.debug "pageDeviceAliases params: $params"
    def dev = state.devices[params.id]
    //log.debug "pageDeviceAliases dev: $dev"
    state.aliasDeviceId = params.id
    
//	def delAliasId = params.delAliasId
//    if (delAliasId) {
//    	def delAlias = dev.aliases.find { it.id == delAliasId }
//    	log.debug "deleting $delAlias.name from $dev.name"
//    	dev.aliases.removeAll { it.id == delAliasId }
//    }
    
    log.debug "dev: $dev"
//    def allAliases = dev.aliases*.name
    
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
        section("GPLv3 License") {
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

  	//updateDevices()
    updateGroups()
    
    // FIXME, those don't seem to actually work
    subscribe(location, "deviceCreated", deviceUpdatesHandler, [filterEvents: false])
    subscribe(location, "deviceDeleted", deviceUpdatesHandler, [filterEvents: false])
    subscribe(location, "deviceUpdated", deviceUpdatesHandler, [filterEvents: false])
}

def deviceUpdatesHandler(evt) {
	log.debug "deviceUpdatedHandler: name $evt.name value $evt.value isStateChange: ${evt.isStateChange()} data $evt.data deviceId $evt.deviceId"
    def data = evt.data
    if (data instanceof String) {
    	data = parseJson(data)
    }
    switch(evt.name) {
    	case "DeviceUpdated":
	    	updateDevice(id: evt.deviceId, name: data.label, groupId: data.groupId)
            break
        case "DeviceCreated":
        	log.warn "deviceUpdatesHandler: new device $evt.deviceId data $data needs to be authorized to be usable."
            break
        case "DeviceDeleted":
        	log.warn "TBD"
            break
    }
}

def stDeviceFromId(deviceId) {
	return switches.find { it.id == deviceId }
}

def getOrCreateGroup(groupId) {
	if (!groupId) {
    	return null
    }
	def group = state.groups[groupId]
    if (!group) {
    	group = [name: "group-$groupId", devices: []]
        state.groups[groupId] = group
    }
    group
}

def updateGroupDevicesFromSettings() {
    state.groups.each { groupId, group ->
		def selectedIds = settings."group-devices-${groupId}"
        if (selectedIds) {
            log.debug "group.devices $group.devices --- selected $selectedIds"
            if (group.devices != selectedIds) {
            	def prevDevices = group.devices.collect { state.devices[it]?.name }
                def newDevices = selectedIds.collect { state.devices[it]?.name }
            	log.info "updateGroupDevicesFromSettings: group ${group.name}: ${prevDevices} -> ${newDevices}"
	            group.devices = selectedIds
            }
        }
    }
}

def updateGroupNamesFromSettings() {
	state.groups.each { groupId, group ->
        def name = settings."group-name-${groupId}"
        if (name && group.name != name) {
        	log.info "updateGroupNamesFromSettings: group name ${group.name} -> ${name}"
	        group.name = name
        }
    }
}

def deleteDevice(id) {
	log.info "deleteDevice: deleting ${state.devices[id].name} $id"
	// remove the from state.devices
    state.devices.remove(id)
	// also remove them from groups that referenced them
    state.groups.each { groupId, group ->
    	group.devices.removeAll { it == id }
        // if we just removed the last device in the group, delete it
        if (!group.devices) {
            state.groups.remove(groupId)
        }
    }
}

def addDevice(stDevice) {
	log.info "addDevice: adding $stDevice.displayName $stDevice.id"
    def device = [
        name: stDevice.displayName,
        groupId: stDevice.device.groupId,
        aliases: [[id: UUID.randomUUID().toString(), name: stDevice.displayName]] // FIXME toString nec?
    ]
    state.devices[stDevice.id] = device
    log.debug "addDevice: groupId $device.groupId"
    if (device.groupId) {
		def group = getOrCreateGroup(device.groupId)
        log.debug "addDevice: group $group"
        if (group.devices.contains(stDevice.id)) {
        	log.warn "addDevice: group.devices: $group.devices --- device $stDevice.id"
        } else {
			group.devices << stDevice.id
        }
    }
}

def updateDevice(info) {
	log.debug "updateDevice: updating $info.name $info.id"
    def device = state.devices[info.id]
    if (!device) {
    	// if it's not an authorized device, we don't care...
        // (this happens if we get called from the DeviceUpdated event)
    	return
    }

	if (device.name != info.name) {
    	log.info "renaming $device.name -> $info.name"
        // Also change principal alias if it's still the original device name
        if (device.aliases[0]?.name == device.name) {
        	device.aliases[0].name = info.name
        }
	    device.name = info.name
    }
    if (device.groupId != info.groupId) {
    	log.info "changing device $device.name groupId $device.groupId -> $info.groupId"
        def origGroup = state.groups[device.groupId]
        def inOrigGroup = origGroup?.devices?.contains(info.id)
        if (!device.groupId || inOrigGroup || (info.groupId && !state.groups[info.groupId])) {
    	    // only add to new group if it still was in the original group
	        // (that is, the user hadn't used preference to remove it from that group,
        	// as far as this app is concerned), or if the newGroup is new to us
            def newGroup = getOrCreateGroup(info.groupId)
            if (newGroup) {
	            if (!newGroup.devices.contains(info.id)) {
	            	log.info "adding device $device.name to group $newGroup.name"
			        newGroup.devices << info.id
                }
            }
        }
	    // remove from original group (if in there)
        if (origGroup?.devices?.contains(info.id)) {
        	log.info "removing device $device.name from group $origGroup.name"
		    origGroup?.devices?.removeAll { it == info.id }
        }
        // update groupId
	    device.groupId = info.groupId
    }
}

def updateDevicesFromSettings() {
	if (!state.devices) {
    	state.devices = [:]
    }
    if (!state.groups) {
    	state.groups = [:]
    }
	// first, see whether devices have been removed
	def newDeviceIds = switches*.id
    def curDeviceIds = state.devices*.key
    def delDeviceIds = curDeviceIds - newDeviceIds

	// delDeviceIds contains the ids of those devices that have been removed
    delDeviceIds.each {	deleteDevice(it) }

	switches.each { stDevice ->
    	if (state.devices[stDevice.id]) {
        	updateDevice(id: stDevice.id, name: stDevice.displayName, groupId: stDevice.device.groupId)
        } else {
        	addDevice(stDevice)
        }
    }
    
    // debug log
	// state.devices.each { deviceId, device ->
	//     log.debug "DEVICE name: $device.name id: $deviceId groupId: $device.groupId"
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
    state.groups.each { groupId, group ->
    	def inputName = settings."group-name-$groupId"
    	if (inputName) {
        	if (group.name != inputName) {
	        	log.warn "updateGroups: updating ${group.name} -> ${inputName}, id ${groupId}"
    	    	group.name = inputName
            }
        } else {
        	if (group.name == "group-$groupId") {
	    		def deviceNames = group.devices.collect { state.devices[it].name }
	        	def guessedName = guessRoomName(deviceNames)
                if (guessedName) {
                	group.name = guessedName
	                log.debug "updateGroups: guessed ${group.name}, id ${groupId}"
                }
            }
        }
    }

	// debug log
   	 state.groups.each { groupId, group ->
		def deviceNames = group.devices.collect { deviceId -> stDeviceFromId(deviceId).displayName }
    	log.debug "GROUP name: $group.name groupId: $groupId devices: $deviceNames"
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
    def userEntities = []

	userEntities << [
    	sessionId: sessionId,
        name: "st_switch",
        extend: false,
        // FIXME, should only do this for devices with capability switch
        entries: state.devices.collect { deviceId, device ->
        	[ value: deviceId, synonyms: device.aliases*.name ]
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
	state.devices.each { deviceId, device ->
    	def stDevice = stDeviceFromId(deviceId)
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
	"This program is free software: you can redistribute it and/or modify " +
	"it under the terms of the GNU General Public License as published by " +
	"the Free Software Foundation, either version 3 of the License, or " +
	"(at your option) any later version." +
	"\n\n" +
	"This program is distributed in the hope that it will be useful, " +
	"but WITHOUT ANY WARRANTY; without even the implied warranty of " +
	"MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the " +
	"GNU General Public License for more details." +
	"\n\n" +
	"You should have received a copy of the GNU General Public License " +
	"along with this program.  If not, see <http://www.gnu.org/licenses/>."
}
