export async function uploadChunks(
  file: File,
  chunkSize: number,
  received: Set<number>,
  options: {
    put: (index: number, blob: Blob) => Promise<void>;
    ack: (index: number) => void;
    signal: AbortSignal;
    onFailure?: () => void;
  },
) {
  const missing = Array.from({ length: Math.ceil(file.size / chunkSize) }, (_, i) => i).filter(
    (i) => !received.has(i),
  );
  let cursor = 0;
  let failed = false;
  let firstError: unknown;
  async function worker() {
    try {
      while (!failed && cursor < missing.length) {
        options.signal.throwIfAborted();
        const index = missing[cursor++];
        await options.put(
          index,
          file.slice(index * chunkSize, Math.min(file.size, (index + 1) * chunkSize)),
        );
        options.signal.throwIfAborted();
        if (!failed) options.ack(index);
      }
    } catch (error) {
      if (!failed) {
        failed = true;
        firstError = error;
        options.onFailure?.();
      }
      throw error;
    }
  }
  await Promise.allSettled([worker(), worker()]);
  if (failed) throw firstError;
}
