# 连页 · Lianye

连页是一款在本机处理图片的长截图工具。默认采用暖白纸感、墨黑文字和陶橙强调色，跟随系统明暗模式。首页、关于和启动画面使用同一套取景框与越界长页标记。

## 现行品牌资产

- `tokens/tokens.json`：当前颜色与布局令牌；`brand` 字段为品牌颜色的唯一输入。
- `brand/logo_master.svg`：暖白背景、墨黑取景框、陶橙长页的当前 108×108 图标母版。
- `brand/logo_foreground.svg`、`logo_background.svg`、`logo_monochrome.svg`：透明自适应前景、背景和单色层。
- `brand/logo_symbol.svg`、`logo_symbol_dark.svg`：应用内主标记的浅色和深色版本。
- `brand/lianye/refined-01/geometry.json`：已确认的主版与小尺寸光学版几何。主版线重 5.8，桌面图标使用线重 7 的光学版。

普通桌面图标固定采用暖白底。深色页面采用暖白角标和浅陶橙长页。Android 12+ 启动画面指定对应明暗背景与透明标记；主题单色图标保留独立层，不将桌面遮罩或阴影烘进前景。

使用 Python 3 同步现行 SVG、Android 颜色/矢量及 Compose 品牌常量：

```powershell
python tool/sync_brand_assets.py
python tool/sync_brand_assets.py --check
```

`--check` 只读检查生成文件是否与输入一致，发现漂移时返回非零退出码。生成资产的文件头标明来源；Compose 主题通过 `Color.kt` 引用生成的 `BrandPalette.kt`。

## 历史资料

`brand/lianye/` 下的探索、独立方案、精修评审和交互原型保留为过程记录，其中配色和产品文案可能与现行应用不同。现行资产以此页列出的根目录 SVG 和 Android 资源为准。设计目录不作为 Android 资源 sourceSets，也不打包历史素材。

## 使用与验收

品牌标记不代替开始、结束、保存等操作图标。图形不嵌入应用名或说明，不依赖颜色表达“超过一屏”的功能关系。界面文案与字号以当前 Compose 组件和主题为准。

检查桌面遮罩与小尺寸识别、浅色/深色、中英文、大字体及实际手机显示。视觉检查与 Android 12+ 资源编译检查分开记录，不将 Android 10 实机结果当作更新版本系统的实机验收。
