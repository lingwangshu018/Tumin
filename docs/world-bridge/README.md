# Tumin World Bridge v1（实验版）

目标：让任意网页小手机用同一份 JS 接入兔眠「异世界连接」的跨世界记忆。

## 1. 复制 SDK

把 `world-bridge.js` 复制到小手机项目，例如：

```html
<script src="/world-bridge.js"></script>
```

普通浏览器打开时不会报错；只有从兔眠「异世界连接」打开时，`WorldBridge.isAvailable()` 才会返回 `true`。

## 2. 建立世界 / 角色身份

```js
const connection = await WorldBridge.connect({
  worldId: "soap-phone",
  worldName: "SOAP PHONE",
  localCharacterId: "character-001",
  characterName: "角色名",
});

console.log(connection.globalCharacterId);
```

兔眠会为 `worldId + localCharacterId` 保存一个跨世界身份映射。v1 不会仅凭同名自动合并不同世界的角色。

## 3. 读取跨世界记忆

```js
const result = await WorldBridge.readMemory({ limit: 20 });
console.log(result.items);
```

## 4. 写回重要记忆

```js
await WorldBridge.writeMemory({
  type: "important_event",
  content: "今天一起去了海边，并约定下周再见。",
  importance: 80,
});
```

## 当前 v1 边界

- 跨世界记忆独立于兔眠原生助手记忆。
- 当前底层先使用兔眠本地轻量存储验证协议，后续可迁移 Room / 向量索引而不改变小手机 SDK。
- 每个世界第一次接入会独立生成跨世界角色 ID；后续需要兔眠 UI 来手动把多个世界角色绑定到同一个 `globalCharacterId`。
- 当前最多保留 500 条实验记忆，每条内容最多 4000 字符。
- `TuminFloatBridge` 继续保留，旧 float 互通不会因 World Bridge v1 被移除。
