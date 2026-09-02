/* global window */
(function (global) {
  const STREAM = "7f5e0a10-4c1d-4b9a-9c22-a1b2c3d4e5f6";
  const SENSOR_DATA = "7f5e0a11-4c1d-4b9a-9c22-a1b2c3d4e5f6";
  const COMMAND = "7f5e0a12-4c1d-4b9a-9c22-a1b2c3d4e5f6";
  const CMD_START = 0x01;
  const CMD_STOP = 0x02;

  let bleDevice = null;
  let bleServer = null;
  let commandChar = null;
  let notifyChar = null;
  let onPacket = null;
  let onDisconnect = null;

  function isSupported() {
    return typeof navigator !== "undefined" && !!navigator.bluetooth;
  }

  function startPayload(unixMs) {
    const buf = new ArrayBuffer(9);
    const view = new DataView(buf);
    view.setUint8(0, CMD_START);
    view.setBigInt64(1, BigInt(Math.trunc(unixMs)), true);
    return buf;
  }

  function stopPayload() {
    return Uint8Array.from([CMD_STOP]).buffer;
  }

  async function pickAndConnect() {
    if (!isSupported()) {
      throw new Error("Web Bluetooth not supported. Use Chrome on Android.");
    }

    bleDevice = await navigator.bluetooth.requestDevice({
      filters: [{ services: [STREAM] }],
      optionalServices: [STREAM],
    });

    bleDevice.addEventListener("gattserverdisconnected", () => {
      commandChar = null;
      notifyChar = null;
      bleServer = null;
      if (onDisconnect) onDisconnect();
    });

    showConnecting(`Connecting to ${bleDevice.name || "Tag"}…`);
    bleServer = await bleDevice.gatt.connect();
    const service = await bleServer.getPrimaryService(STREAM);
    commandChar = await service.getCharacteristic(COMMAND);
    notifyChar = await service.getCharacteristic(SENSOR_DATA);
    await notifyChar.startNotifications();
    notifyChar.addEventListener("characteristicvaluechanged", (event) => {
      if (onPacket) onPacket(event.target.value.buffer);
    });

    return {
      name: bleDevice.name || "Tag",
      id: bleDevice.id,
      rssi: -70,
    };
  }

  function showConnecting(msg) {
    const overlay = document.getElementById("connecting-overlay");
    const text = document.getElementById("connecting-text");
    if (overlay && text) {
      text.textContent = msg;
      overlay.classList.add("show");
    }
  }

  function hideConnecting() {
    const overlay = document.getElementById("connecting-overlay");
    if (overlay) overlay.classList.remove("show");
  }

  async function writeCommand(buffer) {
    if (!commandChar) throw new Error("Not connected");
    await commandChar.writeValue(buffer);
  }

  async function startRecording(unixMs) {
    await writeCommand(startPayload(unixMs));
  }

  async function stopRecording() {
    await writeCommand(stopPayload());
  }

  async function disconnect() {
    if (notifyChar) {
      try {
        await notifyChar.stopNotifications();
      } catch (e) { /* ignore */ }
    }
    if (bleDevice && bleDevice.gatt.connected) {
      bleDevice.gatt.disconnect();
    }
    commandChar = null;
    notifyChar = null;
    bleServer = null;
    bleDevice = null;
  }

  function isConnected() {
    return !!(bleDevice && bleDevice.gatt && bleDevice.gatt.connected);
  }

  global.TagBleClient = {
    isSupported,
    isConnected,
    pickAndConnect,
    startRecording,
    stopRecording,
    disconnect,
    hideConnecting,
    set onPacket(cb) { onPacket = cb; },
    set onDisconnect(cb) { onDisconnect = cb; },
  };
}(window));
