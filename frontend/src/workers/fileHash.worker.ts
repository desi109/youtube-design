import { createSHA256 } from "hash-wasm";
self.onmessage = async (event: MessageEvent<File>) => {
  try {
    const file = event.data;
    const hash = await createSHA256();
    hash.init();
    const step = 4 * 1024 * 1024;
    for (let offset = 0; offset < file.size; offset += step) {
      hash.update(new Uint8Array(await file.slice(offset, offset + step).arrayBuffer()));
      self.postMessage({ progress: Math.min(1, (offset + step) / file.size) });
    }
    self.postMessage({ hash: hash.digest("hex") });
  } catch {
    self.postMessage({ error: "The selected file could not be read. Please choose it again." });
  }
};
