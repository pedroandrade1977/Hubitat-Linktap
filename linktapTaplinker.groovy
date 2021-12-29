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
 *
 *	Updates:
 *	Date: 2021-12-29	v0.1 Beta release
 */

import java.text.DecimalFormat
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

metadata {
	definition (name: "Linktap Taplinker", namespace: "HE_pfta", author: "Pedro Andrade") {
	capability "Battery"
    capability "LiquidFlowRate"
	capability "Valve"
	attribute "Alert_GatewayOnline", "boolean"
	attribute "Alert_DeviceOnline", "boolean"
	attribute "Alert_BatteryLow", "boolean"
	attribute "Alert_WaterCutOff", "boolean"
	attribute "Alert_HighFlow", "boolean"
	attribute "Alert_LowFlow", "boolean"
    attribute "Alert_ValveBroken", "boolean"
    attribute "Alert_DeviceFall", "boolean"
    attribute "Alert_Freeze", "number"
    attribute "gatewayId", "string"
    attribute "signal","number"
    attribute "workMode","string"
    attribute "deviceConnected","boolean"
    attribute "gatewayConnected","boolean"
    attribute "flowMeterStatus","string"
    attribute "wateringCycleSkipped","boolean"
        
    command "timedStart", ["Minutes"]
	}
    
    preferences {
        input(name: "autoClose", type: "number", title: "Minutes to activate watering on open", required: false, defaultValue: 30);
    } 

}

def close(){
    
    parent.sendCommand("activateInstantMode",[device.currentValue("gatewayId") as String, device.getDeviceNetworkId(),false,0]);
    if (!parent.hasWebHook()) {sendEvent(name: "valve", value: "closed")} // assumes closed valve
 
}

def open(){
    Integer param=autoClose as Integer;
    log.debug("Open command with autoclose: ${param}");
	parent.sendCommand("activateInstantMode",[device.currentValue("gatewayId") as String, device.getDeviceNetworkId(),true,param])
    if (!parent.hasWebHook()) {sendEvent(name: "valve", value: "open")} // assumes open valve
}

def timedStart(minutes) {
    log.debug("timedStart command with input: ${minutes}");
	parent.sendCommand("activateInstantMode",[device.currentValue("gatewayId") as String, device.getDeviceNetworkId(),true,minutes as Integer])
    if (!parent.hasWebHook()) {sendEvent(name: "valve", value: "open")} // assumes open valve
}
