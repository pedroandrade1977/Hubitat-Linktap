# Hubitat-Linktap
Driver for Linktap Taplinker automatic watering system

Pre-requisites:
1. You need your username and an apiKey from linktap. The apiKey can be obtained at https://www.link-tap.com/#!/api-for-developers by using your linktap credentials.
2. Linktap for the moment is a cloud-based automation. This means that Hubitat will be calling Linktap API for starting/stoping watering.
3. In order to receive actual status information from the cloud servers, you need to expose Hubitat data api outside your local network. For this, you should have have a static IP or DynDNS and setup port forwarding in your router. Port 39501 is used by Hubitat to listen to incoming messages. You can (and should) filter by MAC or IP your port forwarding rules in the router.
4. It is possible to use the driver without receiving the updates from the cloud server. In this case, all it can do is start and stop watering.

Installation:
1. Create both drivers (Linktap Controller and Linktap Taplinker) by copying and pasting driver code into Hubitat
2. Add virtual device and set type Linktap Controller
3. Enter your username and apiKey in preferences
4. If you intend to use the web hook for receiving data from linktap cloud servers, you need to enter your public static IP on "IP/DNS for Web Hook" and the IP of Linktap server in "MAC / IP Address". For me this has been "34.192.218.92", but it may vary and change over time. In Hubitat logs you may see received messages which have not been routed to a device for parsing, and this will indicate the IP that you should enter in this property.
5. If you are using the web hook, each tap linker will be identified and created as a child device automatically
6. If you are not using the web hook, you can create each tap linker by pressing the add taplinker command in the Controller device, providing the gateway Id, tap linker Id (you find these in the back of each device) and a label 

Using:
1. You can start watering with Open command, it will take the parameter at device level for duration
2. You can start watering with a specific duration with the Timed Start command
3. You can stop watering with the Close command
4. If you are using the web hook, you will get:
a) Events for start and end of watering sessions, either manually triggered in the device, the app, or through a schedule
b) Different types of alerts for exception situations
c) Battery and signal values
d) Flow rate (for Taplinker GS2)
e) Total volume of water at the end of a watering session (for Taplinker GS2)

NOTES
1: This is a beta version that I am releasing in the hope that device owners are willing to test and provide feedback. I will try to address any issues that are reported. I suggest supervised usage of the software until you verify all aspects are working as per your expectations, to prevent any damage (over/underwatering, plant damage, etc.)
2: Linktap APIs provide the ability to activate other watering modes besides manual, but it seemed to me that this is better achieved through the Linktap app than through home automation platform. I may be wrong, and if there are use cases let me know to try to implement.
