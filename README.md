# 考研每日计划 Android

基于 `jihua.xlsx` 的离线考研每日待办 App。

## 当前功能
- 按 Excel 中的 Day 1～Day 128 与日期自动匹配
- 每日任务按时间段展示
- 英语、315、415、政治及晚间复盘分组呈现
- 勾选完成状态保存在手机本地，无需登录、无需联网
- 首页显示当日完成率
- 支持上一天/下一天查看计划
- Android 桌面小组件：显示当前 Day、日期和完成进度
- GitHub Actions 自动构建 Debug APK

## 时间安排说明
Excel 原表没有给每项任务提供具体时钟时间，因此 V1 根据任务类型建立了可执行的默认时间轴：07:30 单词、09:00 网课、10:30 英语、14:00 315、16:00 415、19:00 政治、22:00 晚间任务、22:30 复盘。后续可以把这些默认时间改成你的真实作息。

## 构建
GitHub Actions 会在 `main` 分支提交或手动运行时执行 `gradle :app:assembleDebug`，并上传 APK artifact。

Build pipeline initialized on 2026-08-15.
