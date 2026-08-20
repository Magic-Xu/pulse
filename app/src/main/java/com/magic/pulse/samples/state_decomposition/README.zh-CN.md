# 状态拆分示例（Advanced）

该示例展示了一个“大状态页面”按领域拆分为两个子状态：

- `ImageDomainState`
- `VideoDomainState`

## 展示点

- 将单页大状态拆为分域子状态
- 使用 `mvi-extensions` 的 `pulseMutationReducer { onSub(...) }` 路由 Mutation
- image 与 video 使用独立的 `Latest` TaskKey
- 通过显式 LifecycleOwner 收集 State 与 replay=0 Effect

## 这样设计的原因

- 分域隔离后 reducer 逻辑更小、更易排查
- 每类 mutation 只落在对应领域分支，职责边界更清晰
- ViewModel 仍保持 UI 单入口 `send(uiIntent)`，Task Token 会拒绝迟到 Mutation

建议在 Basic/Standard 已经出现明显复杂度压力时再引入本方案。
