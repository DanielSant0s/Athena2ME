/**
 * Bluetooth (JSR-82): capabilities, device inquiry, and SPP URL helper.
 *
 * Requires native bindings: os.bluetoothGetCapabilities, os.bluetoothInquiry, BTSocket.
 * On emulators or without JSR-82, getCapabilities().available may be 0.
 *
 * Example:
 *   var BT = require("/lib/bluetooth.js");
 *   console.log(JSON.stringify(BT.getCapabilities()));
 *   BT.discoverDevices({ timeoutMs: 15000 }).then(function (devices) { ... });
 *   var url = BT.sppUrl("00112233445566", 1);
 *   var sock = new BTSocket();
 *   sock.connect(url).then(function (s) { s.send(bytes); ... s.close(); });
 *
 * Limitations: no UUID service search in this module; btspp URL must match the peer.
 * authenticate/encrypt are negotiated by the stack.
 */

function getCapabilities() {
    if (typeof os.bluetoothGetCapabilities !== "function") {
        return {
            jsr82: 0,
            available: 0,
            powered: 0,
            name: "",
            address: "",
            error: "native bluetooth API missing"
        };
    }
    return os.bluetoothGetCapabilities();
}

function discoverDevices(options) {
    var ms = 0;
    if (options != null && options !== undefined && options.timeoutMs != null) {
        ms = options.timeoutMs | 0;
    }
    if (typeof os.bluetoothInquiry !== "function") {
        return Promise.reject(new Error("native bluetoothInquiry missing"));
    }
    return os.bluetoothInquiry(ms);
}

/**
 * @param address Bluetooth address (hex, with or without ":")
 * @param channel RFCOMM channel (default 1)
 * @param params suffix after ";" (default authenticate=false;encrypt=false)
 */
function sppUrl(address, channel, params) {
    var ch = channel === undefined || channel === null ? 1 : (channel | 0);
    var addr = ("" + address).replace(/:/g, "").toUpperCase();
    var p = params;
    if (p === undefined || p === null || p === "") {
        p = "authenticate=false;encrypt=false";
    }
    return "btspp://" + addr + ":" + ch + ";" + p;
}

exports.getCapabilities = getCapabilities;
exports.discoverDevices = discoverDevices;
exports.sppUrl = sppUrl;
