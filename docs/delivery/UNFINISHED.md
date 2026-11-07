# 未完成

分清三类。下面只列没有完成的。实现和本机测试过的见 SPEC-COVERAGE.md。

## 代码里做不到，不是环境借口

1. **D9 引擎缓存。** 桥没有释放 session projection cache 或供应商 prompt cache 的路由。spool 清理不算完成。缺的接口写在 D9-ENGINE-CACHE.md。
2. **eta。** 依赖里没有唯一的 eta 包。同名插件有多个，没有选。见 REUSE.md。

## 真机未验证

3. 没有连上 Ace7，也没有 ARM64 系统镜像。分类器、审批、root 关闭不 su、图标、165Hz、覆盖安装都没有在那台手机上跑。
4. D9 的 20 轮手机内存和真实 token 没有测。PERF-host.txt 不能当成那组数。

## 已经补上、不再算未完成

- 关于页「检查更新」索引行已删。
- 使用统计和对话日志上传的入口已从欢迎页和隐私页拿掉。产品路径不上传。
- 干净目录构建见 BUILD.md 和旁边的 build-clean.log。
