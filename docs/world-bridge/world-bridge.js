/*
 * Tumin World Bridge SDK v1
 * Copy this file into any web-based phone/world project.
 *
 * Browser: works as a safe no-op host detector.
 * Tumin Isekai Connection: connects to window.TuminWorldBridge.
 */
(function (global) {
  "use strict";

  const PROTOCOL = "tumin-world-bridge";
  const VERSION = 1;
  let session = null;

  function host() {
    return global && global.TuminWorldBridge ? global.TuminWorldBridge : null;
  }

  function parseResult(raw) {
    if (raw == null || raw === "") return null;
    if (typeof raw === "object") return raw;
    try {
      return JSON.parse(String(raw));
    } catch (error) {
      return { ok: false, error: "Invalid World Bridge response", raw: String(raw) };
    }
  }

  function call(method, args) {
    const bridge = host();
    if (!bridge || typeof bridge[method] !== "function") {
      return Promise.resolve({ ok: false, unavailable: true, error: "Tumin World Bridge is not available" });
    }
    try {
      return Promise.resolve(parseResult(bridge[method].apply(bridge, args || [])));
    } catch (error) {
      return Promise.resolve({ ok: false, error: error instanceof Error ? error.message : String(error) });
    }
  }

  const WorldBridge = {
    protocol: PROTOCOL,
    version: VERSION,

    isAvailable() {
      return Boolean(host());
    },

    async getCapabilities() {
      return call("getCapabilities", []);
    },

    async connect(options) {
      const config = options || {};
      const worldId = String(config.worldId || "").trim();
      const worldName = String(config.worldName || worldId).trim();
      const localCharacterId = String(config.localCharacterId || config.characterId || "").trim();
      const characterName = String(config.characterName || "").trim();

      if (!worldId) throw new Error("WorldBridge.connect: worldId is required");
      if (!localCharacterId) throw new Error("WorldBridge.connect: localCharacterId is required");

      const result = await call("connect", [worldId, worldName, localCharacterId, characterName]);
      if (result && result.ok) {
        session = {
          worldId,
          worldName,
          localCharacterId,
          characterName,
          globalCharacterId: result.globalCharacterId,
        };
      }
      return result;
    },

    getSession() {
      return session ? Object.assign({}, session) : null;
    },

    async readMemory(options) {
      const config = options || {};
      const globalCharacterId = String(config.globalCharacterId || (session && session.globalCharacterId) || "").trim();
      const limit = Math.max(1, Math.min(100, Number(config.limit || 20) || 20));
      if (!globalCharacterId) throw new Error("WorldBridge.readMemory: connect first or provide globalCharacterId");
      return call("readMemories", [globalCharacterId, limit]);
    },

    async writeMemory(memory) {
      const item = memory || {};
      if (!session) throw new Error("WorldBridge.writeMemory: connect first");
      const content = String(item.content || "").trim();
      if (!content) throw new Error("WorldBridge.writeMemory: content is required");

      return call("writeMemory", [
        session.globalCharacterId,
        session.worldId,
        session.localCharacterId,
        String(item.type || "memory"),
        content,
        Math.max(0, Math.min(100, Number(item.importance == null ? 50 : item.importance) || 0)),
      ]);
    },
  };

  global.WorldBridge = WorldBridge;
})(typeof window !== "undefined" ? window : globalThis);
