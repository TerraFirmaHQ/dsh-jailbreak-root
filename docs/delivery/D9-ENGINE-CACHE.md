# D9 引擎缓存

没有完成。不要把 spool 清理算成这一条。

本进程里 90 秒后能删的，只有 host root 的 spool 文件和已经结束的后台任务记录。见 `HostRootControl.releaseOldSpools` 和 `RootExec.releaseIdle`。

引擎侧没有对应接口。`runtime/bridge/dsh-go-bridge` 的 `/dsh-go/v1/` 路由是 inventory、occupancy、companion、plugin-config、sessions 删除和 native stream。没有一条路由可以在保留会话的前提下丢掉 DSH 的 session projection cache，或丢掉模型供应商那边的 prompt cache。`/compact` 会改写对话，不是释放缓存。

因此「会话空闲 90 秒后释放引擎侧缓存」这一标准没有达到。`HostRootControl.ENGINE_CACHE_GAP` 写的是同一句话，单测 `EngineCacheGapTest` 锁住这句，避免以后把 spool 清理标成通过。

20 轮手机内存和真实 token 没有测。`PERF-host.txt` 只是本机 JVM 上摘要函数的字节差和「字符数/4」，不能当成 Ace7 或模型账单。
