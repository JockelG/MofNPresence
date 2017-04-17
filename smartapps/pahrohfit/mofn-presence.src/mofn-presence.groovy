/**
 *  MofN Presence
 *
 *  Copyright 2017 Rob Dailey
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
 * 04/17 Forked version for reliable presence
 */
 
definition(
    name: "MofN Presence",
    namespace: "pahrohfit",
    author: "pah roh fit",
    description: "Will set a simulated presence sensor based on MofN presence.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/Cat-SafetyAndSecurity.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/Cat-SafetyAndSecurity@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/Cat-SafetyAndSecurity@2x.png")


preferences {
    page(name: "virtSensDef", title: "Virtual Sensor Definition", uninstall: true, install: false, nextPage: "mofnSetup"){
		section("Select Presence Sensor Group") {
			input "presenceSensors", "capability.presenceSensor", title: "Presence Sensors Required", multiple: true, required: true
         }
		section("Select Virtual Presence Sensor to Update") {
        	paragraph("This will update a virtual presence sensor of status, based on meeting a threshold of linked presence devices.  You must have added a virtual presence sensor in your IDE for this to work!")
    		input "simulatedPresence", "device.simulatedPresenceSensor", title: "Simulated Presence Sensor", multiple: false, required: true
   		}
    }
    page(name: "mofnSetup", title: "M of N Setup", install: false, uninstall: true, nextPage: "stSettings") 	
    page(name: "stSettings", title: "SmartThings Settings", install: true, uninstall: true){
        section(name: "smartSettings", title: "Smart Settings"){
        	 mode(name: "modeMultiple", title: "Which Modes To Run Under", required: false)
             label(name: "label", title: "Name for this M of N Instance", required: false, multiple: false) 
        }
        section("Send Notifications?") {
            input("recipients", "contact", title: "Send notifications to") {
            	input("phone", "phone", title: "Warn with text message (optional)", description: "Phone Number", required: false)
       		}
            input("sendPush", "bool", required: false, title: "Send Push Notifications on Change?")
    	}
     }
}

def mofnSetup(){
	dynamicPage(name: "mofnSetup") {
    	section() {
        	paragraph "How many devices (M) of the full set of selected devices (N) do you require for the presense to be 'true'?"
   			input("presenceSensorsMofN", "enum", title: "Number Required (the M of the N)", options: mofnRange(), defaultValue: mofnRange("mofn"), required: true)
   		}
	}
}

def mofnRange(action){
	def cntr = 1
	def mofnRangeValues = []    
   	presenceSensors.each(){
        mofnRangeValues.add("${cntr}")
        cntr += 1
    }
    log.debug("settings panel options: ${mofnRangeValues}")
    if (action == "mofn") { log.debug("returning mofn: ${presenceSensorsMofN ?: cntr}"); return presenceSensorsMofN ?: cntr }
    else { return mofnRangeValues }
}
    

def installed() {
	log.debug "Installed with settings: ${settings}"
	unsubscribe()
    initialize()
}

def updated() {
	log.debug "Updated with settings: ${settings}"
	unsubscribe()
    initialize()
}

def initialize() {
    state.presentCntr = 0
    presenceSensors.each { 
      log.debug("initialization ${it.label ?: it.name} -> ${it.currentValue("presence")}")
      if (it.currentValue("presence") == "present"){
        state.presentCntr = state.presentCntr + 1  // add it
      } else if (it.currentValue("presence") == "not present"){
        state.presentCntr = state.presentCntr + 0  // do nothing
      }
    }
    updatePresence()
    log.info("initialization presenceCounter: ${state.presentCntr}, simulatedPresence: ${simulatedPresence.currentValue("presence")}")
    subscribe(presenceSensors, "presence", presenceHandler)
}

def presenceHandler(evt) {
    log.debug("new event found: ${evt}")
  	log.debug("MofN testing trigger event ${evt.displayName}-> ${evt.name}:${evt.value}, [${state.presentCntr}/${presenceSensorsMofN}]")
    if (evt.value == "present") {
        state.presentCntr = state.presentCntr + 1
    } else if (evt.value == "not present") {
        state.presentCntr = state.presentCntr - 1
    } else { log.error("unknown event value -> ${evt.value}") }
    updatePresence()
} 

def updatePresence() {
    log.debug("simulatedPresence ${simulatedPresence.currentValue("presence")}")
    if (state.presentCntr.toInteger() >= presenceSensorsMofN.toInteger()) {
        log.debug("updatePresence >= '${state.presentCntr}' | '${presenceSensorsMofN}'")
    	if (simulatedPresence.currentValue("presence") != "present") {
        	simulatedPresence.arrived()
            sendNotice("Present")
            log.info("MofN Arrival ${state.presentCntr}/${presenceSensorsMofN}")
        } else { log.debug("MofN reached, but state change not necessary") }
    } else {
        log.debug("updatePresence < '${state.presentCntr}' | '${presenceSensorsMofN}'")
        if (simulatedPresence.currentValue("presence") != "not present") {
            simulatedPresence.departed()
            sendNotice("Not Present")
            log.info("MofN Departure ${state.presentCntr}/${presenceSensorsMofN}")
        } else { log.debug("MofN is *not* reached, but state change not necessary") }
    }
}

def sendNotice(vsState) {
    log.debug("sendNotice called.")
	// check that Contact Book is enabled and recipients selected
	if (location.contactBookEnabled && recipients) {
    	sendNotificationToContacts("${simulatedPresence.displayName} state changed to '${vsState}'", recipients)
	} else if (phone) { // check that the user did select a phone number
    	sendSms(phone, "${simulatedPresence.displayName} state changed to '${vsState}'")
	} else { log.debug("sendNotice called.  no email/sms config defined.") }
    
    // send push notifications if defined
    if (sendPush) {
        sendPush("${simulatedPresence.displayName} state changed to '${vsState}'")
	} else { log.debug("sendNotice called.  no email/sms config defined.") }
}
