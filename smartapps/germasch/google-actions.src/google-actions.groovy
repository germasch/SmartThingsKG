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
    name: "Google Actions",
    namespace: "germasch",
    singleInstance: true,
    author: "Kai Germaschewski",
    description: "Interface with Actions on Google / Google Assistant",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    oauth: true)
{
	appSetting "API.AI client access token"
}


preferences {
	section { paragraph "Switches/Dimmers/Colored lights" }
	section ("Allow external service to control these things...") {
        	input "switches", "capability.switch", title: "Choose Switches/Lights (on/off)", multiple: true
            input "dimmers", "capability.switchLevel", title: "Choose Dimmers (on/off/level)", multiple: true
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
    if (!state.accessToken) log.error "Access token not defined. Ensure OAuth is enabled in the SmartThings IDE."
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
	log.debug("newSession sessionId: $sessionId")
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
    
    log.debug "newSession: userEntities $userEntities"
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
	log.debug "listSwitchesREST: ${switches}"
	def speech = "Here are your switches. ";
	switches.each {
//		log.debug "switch: ${it.displayName} ${it.currentValue('switch')} supported ${it.supportedAttributes} capabilities ${it.capabilities}"
//      log.debug "properties ${it.properties} device ${it.device} device.properties ${it.device.properties}"
//      log.debug "group: ${it.device.groupId}"
//	    def attrs = it.getSupportedAttributes()
//        attrs.each { attr ->
//        	log.debug "attr: $attr getValues ${attr.getValues()}"
//        }
		speech += it.displayName + " is " + it.currentValue("switch") + ". ";
	}

	return makeResponse(speech)
}

// TODO: implement event handlers