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
    name: "MofN Authentication",
    namespace: "pahrohfit",
    author: "pah roh fit",
    description: "Will set a simulated presence sensor based on MofN state and presence.  Requires M of the defined N devices have left and reappear in the same occurance.",
    category: "Safety & Security",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/Cat-SafetyAndSecurity.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/Cat-SafetyAndSecurity@4x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/SafetyAndSecurity/Cat-SafetyAndSecurity@2x.png")


preferences {
    page(name: "virtSensDef", title: "Virtual Sensor Definition", uninstall: true, install: false, nextPage: "mofnSetup"){
		section("Select Presence Sensor Group") {
			input "presenceSensors", "capability.presenceSensor", title: "Presence Sensors Required", multiple: true, required: true
         }
		section("Select Virtual Presence Sensor to Update") {
        	paragraph("This will update a virtual presence sensor of status, based on meeting a threshold of linked presence devices part/join.  You must have added a virtual presence sensor in your IDE for this to work!")
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
        	paragraph "How many devices (M) of the full set of selected devices (N) do you require for the part/join to be 'true'?"
   			input("presenceSensorsMofN", "enum", title: "Number Required (the M of the N)", options: mofnRange(), defaultValue: mofnRange("mofn"), required: true)
   		}
    	section() {
        	paragraph "How long of a window for devices to show up next to each other (in minutes)?  The default/minimal polling interval for the hub is 2 minutes."
   			input("mofnWindow", "number", title: "Minute Window for M of N Assurance Validation?", defaultValue: "2", range: 2..10, required: true)
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

def currentPresent() { 
	def presentCntr = presenceSensors.findAll { person -> 
    	 def presenceState = person.currentValue("presence")
         presenceState == 'present' ? true : false
    }
    return presentCntr.size
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
    state.mDevices = [:]
    presenceSensors.each { 
      state.mDevices.put(it.label, '')
      log.debug("initialization [last seen] ${it.label ?: it.displayName} -> 'never'")
    }
    log.info("initialization presenceCounter: ${currentPresent()}, simulatedPresence: ${simulatedPresence.currentValue("presence")}, ${state.mDevices}")
    subscribe(presenceSensors, "presence", presenceHandler)
}

def presenceHandler(evt) {
  	log.info("MofN testing trigger event ${evt.displayName}-> ${evt.name}:${evt.value}, [${currentPresent()}/${presenceSensorsMofN}]")
    if (evt.value == "present") {
        state.mDevices[evt.displayName] = now()
    } else if (evt.value == "not present") {
        state.mDevices[evt.displayName] = ''
    } else { log.error("unknown event value -> ${evt.value}") }
    updatePresence(evt)
} 

def updatePresence(evt) {
    log.debug("simulatedPresence ${simulatedPresence.currentValue("presence")}")
    def devName = evt.displayName
    def curEpoch = now() as Long
    def currPresCntr = currentPresent().toInteger()
    
    // if we suspect M of N is met, try to validate
    if (currPresCntr >= presenceSensorsMofN.toInteger()) {
        log.info("updatePresence met '${currPresCntr}' >= '${presenceSensorsMofN}' ... validating")
        // check each M device, and make sure the bit has flipped from 1 to 0 (present then gone, and now back)
        def mofnCount = [:]
        presenceSensors.each {
            if (state.mDevices.get(it.displayName).isNumber()) {
	           	 // skip anything that hasn't checked in yet
	            log.debug("epoch values -> ${state.mDevices.get(it.displayName)} : ${curEpoch}")
	        	if (it.currentValue("presence") == "present" && state.mDevices.get(it.displayName) >= (curEpoch - (1000 * 60 * mofnWindow))){
	            	// if the device is marked present, and the last time it was marked present was within our mofnWindow time, count it
	                mofnCount.put(it.displayName, state.mDevices.get(it.displayName))
	                log.debug("including ${it} in mofnCount due to recent timestamp of ${state.mDevices.get(it.displayName)}")
	            } else {  log.debug("skipping ${it} in mofnCount due to recent timestamp of ${state.mDevices.get(it.displayName)}") }
            } else { log.debug("skipping ${it} in mofnCount as it has no previous timestamp entry") } 
        }
        // if we meet the live validation, mark it present
        if (mofnCount.size() >= presenceSensorsMofN.toInteger()){ 
    		if (simulatedPresence.currentValue("presence") != "present") {
       			simulatedPresence.arrived()
           		sendNotice("Present Attestation")
           		log.info("MofN Arrival Attestation ${currPresCntr}/${presenceSensorsMofN} -> ${mofnCount}")
       		} else { log.debug("MofN attestation reached, but state change not necessary") }
        }
    } else {
        log.info("updatePresence not yet met '${currPresCntr}' < '${presenceSensorsMofN}'")
        if (simulatedPresence.currentValue("presence") != "not present") {
            simulatedPresence.departed()
            sendNotice("Not Present")
            log.info("MofN Departure ${currPresCntr}/${presenceSensorsMofN}")
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