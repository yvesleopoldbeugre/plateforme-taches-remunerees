const fs = require('fs');

// Minimal PNG IHDR reader: width/height are big-endian uint32 at bytes 16-23.
module.exports = function imgsize(path) {
  const fd = fs.openSync(path, 'r');
  const buf = Buffer.alloc(24);
  fs.readSync(fd, buf, 0, 24, 0);
  fs.closeSync(fd);
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
};
