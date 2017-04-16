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
    description: "Will set a simulated presence sensor based on MofN arrival/departure.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")


preferences {
	section("Select Presence Sensor Group") {
	input "presenceSensors", "capability.presenceSensor", title: "Presence Sensors Required", multiple: true, required: true, submitOnChange: true
	input "presenceSensorsMofN", "number", title: "Number Required (the M of the N)", multiple: false, required: true, range: "2..presenceSensors.size()", hideWhenEmpty: "presenceSensors"
    input "simulatedPresence", "device.simulatedPresenceSensor", title: "Simulated Presence Sensor", multiple: false, required: true
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
    state.presentCntr = 0
    presenceSensors.each { 
      log.debug("${it.label ?: it.name} -> ${it.currentValue("presence")}")
      if (it.currentValue("presence") == "present"){
        state.presentCntr = state.presentCntr + 1
      } else if (it.currentValue("presence") == "not present"){
        state.presentCntr = state.presentCntr - 1
      }
    }
    updatePresence()
    log.debug("presenceCounter: ${state.presentCntr}, simulatedPresence: ${simulatedPresence.currentValue("presence")}")
    subscribe(presenceSensors, "presence", presenceHandler)
}

def presenceHandler(evt) {
    log.debug("new event ${evt}")
    if (evt.value == "present") {
        state.presentCntr = state.presentCntr + 1
    } else if (evt.value == "not present") {
        state.presentCntr = state.presentCntr - 1
    } else { log.debug("Unknown event value -> ${evt.value}") }
  	log.debug("MofN Testing Trigger Event ${evt.displayName} : ${evt.device} : ${evt.name} : ${evt.value}")
    updatePresence()
} 


def updatePresence() {
    if (state.presentCntr >= presenceSensorsMofN) {
    	if (simulatedPresence.currentValue("presence") != "present") {
        	simulatedPresence.arrived()
            log.debug("MofN Arrival ${state.presentCntr}/${presenceSensorsMofN}")
        }
    } else {
        if (simulatedPresence.currentValue("presence") != "not present") {
            simulatedPresence.departed()
            log.debug("MofN Departure ${state.presentCntr}/${presenceSensorsMofN}")
        }
    }
}

