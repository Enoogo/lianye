# 自适应无障碍帮助

用户已要求直接完成系统自适应，不让用户选择品牌或系统，并保持帮助简洁。沿用当前首页和截图流程，不修改拼接、权限授予方式或已有草稿。

## 设计

自动识别制造商和品牌，优先识别荣耀、一加、realme 等子品牌，再识别厂商；增加 POCO、品牌字段缺省和大小写覆盖。荣耀独立于华为。帮助仅展示当前设备，不出现系统选择器。

不根据 Android 版本猜测 MIUI、HyperOS、ColorOS 或 MagicOS 版本。不同 ROM 的版本与 Android 版本不是一一对应，且菜单名称会变化。通过系统能力自动尝试服务详情、带定位参数的无障碍列表、普通无障碍列表、系统设置，并捕获缺失入口和权限拒绝。服务详情是 AOSP 系统接口，部分系统需要签名权限；它只能作为可失败的优化，不能作为必达承诺，不新增特权权限或反射。

指引从“无障碍”页面开始，使用三步短路径；将旧版菜单别名放在展开内容内。若只能打开设置首页，提示搜索“无障碍”。这比维护未经验证的 ROM 版本路径更稳妥。帮助卡显示自动识别的品牌和实际 Android 版本。

服务状态使用真实连接和系统开启状态：已连接优先，开关已开启但未连接单独提示，受限状态优先于普通未开启。AppOps 在 Android 13 之前不调用；13 及以后区分允许、明确阻止和未知。未知不等于已放行，也不等于明确阻止。回到应用时重新读取，连接变化即时更新。

自动模式的帮助卡只保留一个当前所需主操作：未开启时前往无障碍设置；受限时打开应用信息；已开启时管理设置。已开启隐藏开启指引和受限排障。找不到服务的路径、开关受限的处理按需折叠。Android 10–12 不展示 Android 13 的“允许受限制的设置”。没有受限菜单时不声称可以强制放行；说明遵循系统提示，并可返回“自己滑动”。手动模式说明无需无障碍，不展示自动权限引导。

视觉使用与首页一致的暖白/深色背景、墨色标题、细分隔和小面积状态色，步骤以细编号和短行呈现，不堆叠长箭头句子，不重复隐私文案。折叠区域和操作触达至少 48dp，窄屏、大字体可滚动。

## 范围与验证

- 纯模型测试覆盖品牌优先级、空字段、未知品牌、各权限状态和 Android 版本边界。
- 原生测试覆盖跳转参数与失败回退、真实 Huawei 帮助与设置跳转、各品牌注入指引、受限/已连接/未连接页面、手动无权限页、中文/英文/深色/1.8 倍字体。
- 完整单元、构建、Lint、零网络依赖和现有原生回归。
- 实机验证只证明 Huawei Android 10；其他品牌使用官方资料和注入测试，不能宣称多品牌实机通过。
- 测试前备份当前调试版数据，结束后按文件哈希恢复；不改正式版，不开启无障碍或捕获权限，不提交或发布。

## 核对资料

- [Android 系统设置 API](https://developer.android.com/reference/android/provider/Settings)：标准无障碍入口需要防范缺失活动。
- [AOSP Settings 源码](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/provider/Settings.java)：服务详情 action 与组件参数。
- [AOSP 设置清单](https://raw.githubusercontent.com/aosp-mirror/platform_packages_apps_settings/master/AndroidManifest.xml)：服务详情可能要求 OPEN_ACCESSIBILITY_DETAILS_SETTINGS。
- [Android 受限制的设置](https://support.google.com/android/answer/12623953)：Android 13 起的应用信息菜单与身份验证。
- [小米无障碍说明](https://www.mi.com/es/support/faq/details/KA-1131518/)：通用、已下载的应用，明确指出 HyperOS 版本和机型影响路径。
- [华为无障碍说明](https://consumer.huawei.com/cn/support/content/zh-cn15946913/)和[荣耀无障碍说明](https://www.honor.com/cn/support/content/zh-cn15869276/)：辅助功能、无障碍入口。
- [三星已安装应用说明](https://www.samsung.com/us/support/answer/ANS10001906/)：无障碍、已安装应用；旧文档使用已安装服务。
- [vivo 官方用户手册](https://de-gdpr-exstatic-vivofs.vivo.com/sFhAQhTYYDsNOJz1/1674014860906/9545f0bfb8834a57699a04063530d221.pdf)：Downloaded apps。
- OPPO 的“通用”入口保留现有指引，加入菜单别名和搜索兜底；当前官方公开检索未提供可验证的所有版本服务路径，不新增未经确认的具体版本断言。
