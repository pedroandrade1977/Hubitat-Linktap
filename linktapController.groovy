/**
 *  Copyright 2021 Pedro Andrade
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
 *	Linktap Driver
 *
 * Author: Pedro Andrade
 * Some code was taken from Ecowitt WiFi Gateway Driver by Simon Burke (Original author Mirco Caramori - github.com/mircolino)
 *
 *	Updates:
 *	Date: 2021-12-29	v1.0 Initial release
 */

import java.text.DecimalFormat
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

private apiUrl()                    { "https://www.link-tap.com" }

metadata {
	definition (name: "Linktap Controller", namespace: "HE_pfta", author: "Pedro Andrade") {
    attribute "mac", "string";
    attribute "webHook", "string";
        
    command "addTapLinker", ["gatewayId","tapLinkerId","Label"]
}

    preferences {
        input(name: "apiKey", type: "string", title: "API Key", required:true)
        input(name: "username", type: "string", title: "Username", required:true)
        input (name: "logLevel", type: "number", defaultValue:2, title: "Log Level")
        input(name: "macAddress", type: "string", title: "MAC / IP Address", description: "Linktap Server IP address", defaultValue: "34.192.218.92", required: true);
        input(name: "webHook", type: "string", title: "IP/DNS for Web Hook", description: "IP or DynDNS domain", defaultValue: "", required: false);
    }

}


// ********************** Reused logging code from Ecowitt Wifi Gateway Hubitat Driver

Integer logGetLevel() {
  //
  // Get the log level as an Integer:
  //
  //   0) log only Errors
  //   1) log Errors and Warnings
  //   2) log Errors, Warnings and Info
  //   3) log Errors, Warnings, Info and Debug
  //   4) log Errors, Warnings, Info, Debug and Trace/diagnostic (everything)
  //
  // If the level is not yet set in the driver preferences, return a default of 2 (Info)
  // Declared public because it's being used by the child-devices as well
  //
  if (settings.logLevel != null) return (settings.logLevel.toInteger());
  return (2);
}

private void logError(String str) { log.error(str); }
private void logWarning(String str) { if (logGetLevel() > 0) log.warn(str); }
private void logInfo(String str) { if (logGetLevel() > 1) log.info(str); }
private void logDebug(String str) { if (logGetLevel() > 2) log.debug(str); }
private void logTrace(String str) { if (logGetLevel() > 3) log.trace(str); }
private void logData(Map data) {
  if (logGetLevel() > 3) {
    data.each {
      logTrace("$it.key = $it.value");
    }
  }
}

// ********************** Webhook Parsing

void parse(String msg) {
    logTrace("parse(): $msg");

    // Parse POST message
    Map data = parseLanMessage(msg);

    // Save only the body and discard the header
    String body = data["body"];
    logTrace("Body: ${body}");

    def slurper = new groovy.json.JsonSlurper();
    def result=slurper.parseText(body);

    String gatewayId=result.gatewayId?:null;
    String deviceId=result.deviceId?:null;
    if (gatewayId) {logTrace("Gateway: ${gatewayId}");}
    if (deviceId) {logTrace("Device: ${deviceId}");}

    // is the message for a device, or only for gateway?
    if (!deviceId && gatewayId && ((result.msg?:"")=="gatewayOnline" || (result.msg?:"")=="gatewayOffline")) { // gateway connectivity message
        logDebug("gateway connectivity");
        // update all devices for this gateway
        def tapLinkers=getChildDevices();
        tapLinkers.each {
            if((it.currentValue("gatewayId") as String)==gatewayId) {
                logDebug("found device for this gateway");
                if (result.msg=="gatewayOffline") {
                    logDebug("marking gateway offline");
                    it.sendEvent(name: "gatewayConnected", value: false);
                    it.sendEvent(name: "deviceConnected", value: false);
                } else {
                    logDebug("marking gateway online");
                    it.sendEvent(name: "gatewayConnected", value: true);
                }
            }
        }
    }
    
    // first we check if the device already exists
    if (deviceId) {
        def tapLinker=getChildDevice(deviceId);
        if (tapLinker==null) {
            logDebug("could not find tapLinker device, should create");
            tapLinker=addChildDevice("HE_pfta", "Linktap Taplinker", deviceId, [name: deviceId]);
            logDebug("created");
            // update basic attributes
            tapLinker.sendEvent(name: "gatewayId", value: gatewayId);
        } else {
            // if exists, check if gatewayId is the same
            String setting=tapLinker.currentValue("gatewayId") as String;
            logDebug("current gw: $setting");
            if (setting!=gatewayId) {
                // different gateway Id, device may have been reassigned to a different gateway
                logDebug("different gateway Id, will update");
                tapLinker.sendEvent(name: "gatewayId", value: gatewayId);
            }
        }

        // now we have a device, process incoming attributes if they exist
        if (result.battery) {
            logTrace("found battery: ${result.battery}");
            tapLinker.sendEvent(name: "battery", value: result.battery.split("%")[0]);
        }
        if (result.signal) {
            logTrace("found signal: ${result.signal}");
            tapLinker.sendEvent(name: "signal", value: result.signal);
        }
    
        // process events
        if (result.event) {
            logTrace("found event: ${result.event}");
            switch (result.event) {
                case "watering start": // update status to open, set working mode and update status to connected (cannot have event watering event if not connected)
                    tapLinker.sendEvent(name: "valve", value: "open");
                    tapLinker.sendEvent(name: "workMode", value: (result.workMode?:"U"));
                    tapLinker.sendEvent(name: "deviceConnected", value: true);
                    if ((result.workMode?:"U")!="M") { // if automatic watering, then reset skipped cycle indicator
                        tapLinker.sendEvent(name: "wateringCycleSkipped", value: false);
                    }
                break;
                case "watering end": // update status to closed, unset working mode and update status to connected (cannot have event watering event if not connected)
                    tapLinker.sendEvent(name: "valve", value: "closed");
                    tapLinker.sendEvent(name: "workMode", value: "0");
                    tapLinker.sendEvent(name: "deviceConnected", value: true);
                    tapLinker.sendEvent(name: "rate", value: "0");
                break;
                case "watering cycle skipped": // mark indicator that last cycle was skipped, and update status to connected (cannot have event watering event if not connected)
                    tapLinker.sendEvent(name: "wateringCycleSkipped", value: true);
                    tapLinker.sendEvent(name: "deviceConnected", value: true);
                break;
                case "gateway offline":
                    tapLinker.sendEvent(name: "Alert_GatewayOnline", value: true);
                break;
                case "gateway online":
                    tapLinker.sendEvent(name: "Alert_GatewayOnline", value: false);
                break;
                case "device offline":
                    tapLinker.sendEvent(name: "Alert_DeviceOnline", value: true);
                break;
                case "device online":
                    tapLinker.sendEvent(name: "Alert_DeviceOnline", value: false);
                break;
                case "battery low alert":
                    tapLinker.sendEvent(name: "Alert_BatteryLow", value: true);
                break;
                case "battery good":
                    tapLinker.sendEvent(name: "Alert_BatteryLow", value: false);
                break;
                case "water cut-off alert":
                    tapLinker.sendEvent(name: "Alert_WaterCutOff", value: true);
                break;
                case "unusually high flow alert":
                    tapLinker.sendEvent(name: "Alert_HighFlow", value: true);
                break;
                case "unusually low flow alert":
                    tapLinker.sendEvent(name: "Alert_LowFlow", value: true);
                break;
                case "valve broken alert":
                    tapLinker.sendEvent(name: "Alert_ValveBroken", value: true);
                break;
                case "device fall alert":
                    tapLinker.sendEvent(name: "Alert_DeviceFall", value: true);
                break;
                case "freeze alert":
                    tapLinker.sendEvent(name: "Alert_Freeze", value: true);
                break;
                case "alarm clear":
                    tapLinker.sendEvent(name: "Alert_WaterCutOff", value: false);
                    tapLinker.sendEvent(name: "Alert_HighFlow", value: false);
                    tapLinker.sendEvent(name: "Alert_LowFlow", value: false);
                    tapLinker.sendEvent(name: "Alert_ValveBroken", value: false);
                    tapLinker.sendEvent(name: "Alert_DeviceFall", value: false);
                    tapLinker.sendEvent(name: "Alert_Freeze", value: false);
                break;
                case "manual button pressed":
                    // don't really have something to do with this at this moment
                break;
            }
        }

        // process other messages
        if (result.msg) {
            logTrace("found msg: ${result.msg}");
            switch (result.msg) {
                case "deviceOffline":
                    tapLinker.sendEvent(name: "deviceConnected", value: false);
                break;
                case "deviceOnline":
                    tapLinker.sendEvent(name: "deviceConnected", value: true);
                break;
                case "flowMeterStatus":
                    tapLinker.sendEvent(name: "flowMeterStatus", value: "n/a");
                break;
                case "flowMeterValue":
                    tapLinker.sendEvent(name: "rate", value: ((result.vel/1000)?:0));
                break;
                case "wateringOff":
                    logDebug(((result.vel/1000)?:0) as String);
                    logDebug(((result.vol/1000)?:0) as String);
                    tapLinker.sendEvent(name: "wateringSessionEnd",value: ((result.vol/1000)?:0), unit: "LPM", descriptionText: "Watering session finished with average rate ${(result.vel/1000)?:0}");
                break;
                case "wateringOn":
                    // don't really have something to do with this at this moment
                break;
            }
        }

    
    }
    
}


// ************************ DNI code from Ecowitt Wifi Gateway Hubitat Driver

private String gatewayMacAddress() {
  //
  // Return the MAC or IP address as entered by the user, or the current DNI if one hasn't been entered yet
  //
  String address = settings.macAddress as String;

  if (address == null) {
    //
    // *** This is a timing hack ***
    // When the users sets the DNI at installation, we update the settings before
    // calling update() but when we get here the setting is still null!
    //
    address = device.getDeviceNetworkId();
  }

  return (address);
}

private Map dniIsValid(String str) {
  //
  // Return null if not valid
  // otherwise return both hex and canonical version
  //
  List<Integer> val = [];

  try {
    List<String> token = str.replaceAll(" ", "").tokenize(".:");
    if (token.size() == 4) {
      // Regular IPv4
      token.each {
        Integer num = Integer.parseInt(it, 10);
        if (num < 0 || num > 255) throw new Exception();
        val.add(num);
      }
    }
    else if (token.size() == 6) {
      // Regular MAC
      token.each {
        Integer num = Integer.parseInt(it, 16);
        if (num < 0 || num > 255) throw new Exception();
        val.add(num);
      }
    }
    else if (token.size() == 1) {
      // Hexadecimal IPv4 or MAC
      str = token[0];
      if ((str.length() != 8 && str.length() != 12) || str.replaceAll("[a-fA-F0-9]", "").length()) throw new Exception();
      for (Integer idx = 0; idx < str.length(); idx += 2) val.add(Integer.parseInt(str.substring(idx, idx + 2), 16));
    }
  }
  catch (Exception ignored) {
    val.clear();
  }

  Map dni = null;

  if (val.size() == 4) {
    dni = [:];
    dni.hex = sprintf("%02X%02X%02X%02X", val[0], val[1], val[2], val[3]);
    dni.canonical = sprintf("%d.%d.%d.%d", val[0], val[1], val[2], val[3]);
  }

  if (val.size() == 6) {
    dni = [:];
    dni.hex = sprintf("%02X%02X%02X%02X%02X%02X", val[0], val[1], val[2], val[3], val[4], val[5]);
    dni.canonical = sprintf("%02x:%02x:%02x:%02x:%02x:%02x", val[0], val[1], val[2], val[3], val[4], val[5]);
  }

  return (dni);
}

private String dniUpdate() {
  //
  // Get the gateway address (either MAC or IP) from the properties and, if valid and not done already, update the driver DNI
  // Return "error") invalid address entered by the user
  //           null) same address as before
  //             "") new valid address
  //
  logDebug("dniUpdate()");

  String error = "";
  String attribute = "mac";
  String setting = gatewayMacAddress();

  Map dni = dniIsValid(setting);
  if (dni) {

    if ((device.currentValue(attribute) as String) == dni.canonical) {
      // The address hasn't changed: we do nothing
      error = null;
    }
    else {
      // Save the new address as an attribute for later comparison
        sendEvent(name: "mac", value: dni.canonical);

      // Update the DNI
      device.setDeviceNetworkId(dni.hex);
    }
  }
  else {
    error = "\"${setting}\" is not a valid MAC or IP address";
  }

  return (error);
}


// **************** actions
private updateWebHook() {
    logDebug("current web hook: ${device.currentValue("webHook") as String}");
    logDebug("new web hook: ${settings?.webHook as String}");
    if ((device.currentValue("webHook") as String) == (settings?.webHook as String)) {
      // no changes, do nothing
    }
    else {
      logDebug("different, will replace");


        if ((settings.webHook?:"")=="") {
          // delete existing webhook
          sendCommand(apiDeleteWebHook(),[]);
          sendEvent(name: "webHook", value: "NONE");
        } else {
          // set new webhook
          sendCommand(apiSetWebHook(),["http://${settings.webHook}:39501/data"]);
          // Save the new webHook as an attribute for later comparison
          sendEvent(name: "webHook", value: settings?.webHook);
        }
    }
}

Boolean hasWebHook() {
    logDebug("HasWebHook: ${((settings.webHook?:"")!="")}");
    return ((settings.webHook?:"")!="");
}


void updated() {
  //
  // Called everytime the user saves the driver preferences
  //
    logDebug("updated()");

    // Clear previous states
    state.clear();

    // Update Device Network ID
    String error = dniUpdate();

    // Update webhook
    updateWebHook();
}


Boolean sendCommand(method,args = []) {
    
    logTrace("sendCommand(): called with arguments ${args}")
    
    def methods = [
	'deleteWebHookUrl': [
                    uri: "${apiUrl()}/api/$method",
                    body: ["username": "${settings.username as String}", "apiKey": "${settings.apiKey as String}"]
                    ],
    'setWebHookUrl': [
                    uri: "${apiUrl()}/api/$method",
                    body: ["username": settings.username, "apiKey": settings.apiKey, "webHookUrl": args[0]],
                    ],
    'activateInstantMode': [
                    uri: "${apiUrl()}/api/$method",
                    body: ["username": settings.username, "apiKey": settings.apiKey, "gatewayId": args[0], "taplinkerId": args[1], "action": args[2], "duration": args[3]],
                    ]
	]

	def request = methods.getAt(method)
  
    logTrace("sendCommand(): HTTP Request = ${request}")

    try {
        httpPostJson(request) { resp ->
            if (!resp.success) {
                return (false);
            } else {
                return (true);
            }
        }
    } catch (Exception e) {
        logError(e as String)
        return (false);
    }
}


def addTapLinker(gwId, tlId, tlLabel) {
    logDebug("gwId: $gwId");
    logDebug("tlId: $tlId");
    // check no web hook
    if (hasWebHook()) {
        logWarning("Will not create child device, it can be created automatically during first message to webhook")
    } else {
        def tapLinker=addChildDevice("HE_pfta", "Linktap Taplinker", tlId, [name: tlId, label: tlLabel]);
        logDebug("created");
        // update basic attributes
        tapLinker.sendEvent(name: "gatewayId", value: gwId);
   }
}
