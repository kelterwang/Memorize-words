# Stitch 设计资源：UI Prototype Design System

项目 ID：`2944537851381759161`。导出日期：2026-09-07。

## 页面与代码

| 用户指定项目 | HTML 原始代码 | 本地图片预览版 | 原尺寸截图 |
|---|---|---|---|
| 今日首页 | [code.html](01-home/code.html) | [preview.html](01-home/preview.html) | [PNG](01-home/screen.png) |
| 学生自测 - 答案隐藏态 | [code.html](05-student-hidden/code.html) | [preview.html](05-student-hidden/preview.html) | [PNG](05-student-hidden/screen.png) |
| 本轮测试统计与结算 | [code.html](08-round-summary/code.html) | [preview.html](08-round-summary/preview.html) | [PNG](08-round-summary/screen.png) |
| 学生自测 - 答案已核对态 | [code.html](06-student-revealed/code.html) | [preview.html](06-student-revealed/preview.html) | [PNG](06-student-revealed/screen.png) |
| 词库主页 | [code.html](09-library/code.html) | [preview.html](09-library/preview.html) | [PNG](09-library/screen.png) |

`code.html` 保留 Stitch 下载内容；`preview.html` 仅将页面内的远程图片地址替换为本地 `assets/` 文件。浏览器打开预览仍需联网加载 Tailwind CDN、Google Fonts 与 Material Symbols，不能作为完全离线网页使用。截图可直接离线查看。

这些文件是 Stitch 生成的界面原型，尚未接入 Android 应用或真实业务数据，原型中的交互和数字不代表现有应用行为。

## Design System

用户给出的 `asset-stub-assets_d98615e2e02e40a9a35d93176532464c` 是画布上的设计系统占位 ID，对应真实资源 `assets/d98615e2e02e40a9a35d93176532464c`，名称为 Morning Warmth Study。

- [完整 DESIGN.md](design-system/DESIGN.md)：颜色、字体、间距、形状及组件规范。
- [结构化原始资源 asset.json](design-system/asset.json)：Stitch 返回的完整设计系统对象。

此资源调用 `get_screen` 返回无效参数；通过 `list_design_systems` 已取回对应资产。该接口未提供独立截图或 HTML 下载地址，因此没有伪造或用其他页面替代设计系统图片。

## 来源与完整性

- `screens.json`：5 个页面的 ID、标题、尺寸及原始下载地址。
- `assets.json`：HTML 引用图片与本地文件的映射。
- `manifest.json`：本地文件大小及 SHA-256 校验值；设计系统图片/HTML 缺失原因也有记录。
- 下载使用 `curl -fL`，图片下载地址追加 Google 图片服务的 `=s0` 参数取得原尺寸。截图宽高与 MCP 返回的元数据一致。
- 导出文件保留上游空白格式；该目录的 `.gitattributes` 仅允许保留行末和文件末空白。

运行仓库根目录下的 `python3 -m unittest discover -s tests -v` 可检查页面 ID、文件校验值、PNG 结构与尺寸、设计系统映射，以及本地预览图片链接。
