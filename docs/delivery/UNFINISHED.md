# 未完成

分清三类。下面只列没有完成的。实现和本机测试过的见 SPEC-COVERAGE.md。

## 代码里做不到，不是环境借口

1. **D9 引擎缓存。** 桥没有释放 session projection cache 或供应商 prompt cache 的路由。spool 清理不算完成。缺的接口写在 D9-ENGINE-CACHE.md。
2. **eta。** 依赖里没有唯一的 eta 包。同名插件有多个，没有选。见 REUSE.md。

## 这轮没有拿真引擎打出的

- 渠道提示词的组装在 Dart 和 `channel_prompt.mjs` 里测过。没有 `DSH_PREFIX`，没有把两个渠道的 HTTP 体送到外部模型。引擎若在 assemble 的 context 里不提供 provider id，钩子用默认文本，不用另一家渠道的文本。会话历史文件没有被打开或改写。

## 真机未验证

3. 没有连上 Ace7，也没有 ARM64 系统镜像。分类器、审批、root 关闭不 su、图标、165Hz、覆盖安装都没有在那台手机上跑。
4. D9 的 20 轮手机内存和真实 token 没有测。PERF-host.txt 不能当成那组数。

## 已经补上、不再算未完成

- 关于页「检查更新」索引行已删。G1/G5 的关于页测试这轮重跑通过。
- 使用统计和对话日志上传的入口已从欢迎页和隐私页拿掉。产品路径不上传，有 host 测试。
- D2/D6/D7/D9 本应用缓存/D11/D12/R2/R3 序列化/R4/P2/G4 有 host 测试，见 HOST-TESTS.md。
- 干净目录构建见 BUILD.md 和 build-clean.log。这轮 versionCode 是 11071905，源码是 `49a5ffb`。11071738 和 11071753 不是这轮的包。
- B1/B2/B3、D1 的 17 例、D2 两按钮对话框、D3 自动允许、D4 审计字段、D7 UTF-8 分页有 host 测试。真机未验证，不能写成 P0 全过。
