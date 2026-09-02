/* global window */
(function (global) {
  const START_BYTE = 0xa1;
  const STOP_BYTE = 0x5a;
  const VERSION = 8;
  const HEADER_SIZE = 18;
  const SAMPLE_WIRE_SIZE = 21;

  function formatDateTime(epochMs) {
    const d = new Date(epochMs);
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} `
      + `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}.`
      + `${String(d.getMilliseconds()).padStart(3, "0")}`;
  }

  function formatFileSize(bytes) {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  }

  function parsePacket(data, syncBaseUnixMs, tagUptimeAtSync) {
    if (!(data instanceof ArrayBuffer)) {
      data = data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength);
    }
    const u8 = new Uint8Array(data);
    if (u8.length < HEADER_SIZE + SAMPLE_WIRE_SIZE + 1) return null;
    if (u8[0] !== START_BYTE) return null;
    if (u8[1] !== VERSION) return null;
    if (u8[u8.length - 1] !== STOP_BYTE) return null;

    const view = new DataView(data);
    const firstSampleNumber = view.getUint32(14, true);
    const baseTimestampMs = view.getUint32(18, true);
    const sampleCount = Math.floor((u8.length - HEADER_SIZE - 1) / SAMPLE_WIRE_SIZE);
    if (sampleCount < 1 || sampleCount > 10) return null;

    const rows = [];
    let offset = HEADER_SIZE;
    for (let i = 0; i < sampleCount; i++) {
      const flags = u8[offset];
      const accelX = view.getInt16(offset + 1, true);
      const accelY = view.getInt16(offset + 3, true);
      const accelZ = view.getInt16(offset + 5, true);
      const gyroX = view.getInt16(offset + 7, true);
      const gyroY = view.getInt16(offset + 9, true);
      const gyroZ = view.getInt16(offset + 11, true);
      const humidity = view.getUint16(offset + 13, true);
      const envTemp = view.getInt16(offset + 15, true);
      const bodyTemp = view.getInt16(offset + 17, true);
      const deltaMs = view.getUint16(offset + 19, true);
      offset += SAMPLE_WIRE_SIZE;

      const rawTs = baseTimestampMs + deltaMs;
      const uptimeRef = tagUptimeAtSync != null ? tagUptimeAtSync : baseTimestampMs;
      const relativeMs = rawTs - uptimeRef;
      const absMs = syncBaseUnixMs + relativeMs;

      rows.push({
        timestamp_ms: relativeMs,
        sample_number: firstSampleNumber + i,
        flags: `0x${flags.toString(16).padStart(2, "0")}`,
        accel_x: accelX,
        accel_y: accelY,
        accel_z: accelZ,
        gyro_x: gyroX,
        gyro_y: gyroY,
        gyro_z: gyroZ,
        humidity_x100: humidity,
        env_temp_x100: envTemp,
        body_temp_x100: bodyTemp,
        date_time: formatDateTime(absMs),
        si: [
          (accelX / 1000).toFixed(3),
          (accelY / 1000).toFixed(3),
          (accelZ / 1000).toFixed(3),
          (gyroX / 1000).toFixed(3),
          (gyroY / 1000).toFixed(3),
          (gyroZ / 1000).toFixed(3),
          (humidity / 100).toFixed(2),
          (envTemp / 100).toFixed(2),
          (bodyTemp / 100).toFixed(2),
        ].join(","),
      });
    }
    return rows;
  }

  function csvHeader(includeSi) {
    const raw = [
      "timestamp_ms", "sample_number", "flags",
      "accel_x", "accel_y", "accel_z",
      "gyro_x", "gyro_y", "gyro_z",
      "humidity_x100", "env_temp_x100", "body_temp_x100",
    ];
    if (!includeSi) return raw.concat("date_time").join(",");
    const si = [
      "accel_x_mps2", "accel_y_mps2", "accel_z_mps2",
      "gyro_x_rads", "gyro_y_rads", "gyro_z_rads",
      "humidity_pct", "env_temp_c", "body_temp_c",
    ];
    return raw.concat(si, "date_time").join(",");
  }

  function buildCsv(rows, includeSi) {
    const lines = [csvHeader(includeSi)];
    rows.forEach((row) => {
      const raw = [
        row.timestamp_ms, row.sample_number, row.flags,
        row.accel_x, row.accel_y, row.accel_z,
        row.gyro_x, row.gyro_y, row.gyro_z,
        row.humidity_x100, row.env_temp_x100, row.body_temp_x100,
      ];
      const si = includeSi ? row.si.split(",") : [];
      lines.push(raw.concat(si, row.date_time).join(","));
    });
    return lines.join("\n");
  }

  function downloadCsv(csv, fileName) {
    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName;
    a.click();
    URL.revokeObjectURL(url);
  }

  global.TagPacketCsv = {
    parsePacket,
    buildCsv,
    downloadCsv,
    formatDateTime,
    formatFileSize,
    HEADER_SIZE,
  };
}(window));
