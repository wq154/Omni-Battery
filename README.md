# Omni Battery / 万能电池

GT15 LightTech Sky 专用 Forge 1.20.1 能源缓冲模组，为空岛科技线提供便携电池与跨阶段能源缓冲。

## Target

- Minecraft: 1.20.1
- Loader: Forge 47.x
- Java: 17
- Main pack: GT15 LightTech Sky
- Owner: wq154

## Features

- 低级/中级/高级/精英/终极电池与方块
- 用于 GT15 主线的电网缓冲和机器启动保护
- 服务空岛建厂体验，减少早中期掉电挫败感
- 后期与 GT15 Core 能源保险继电器联动
- 不跳过 GregTech 发电与电压推进，只提供缓冲玩法

## Repository

https://github.com/wq154/Omni-Battery

## Build

```bat
REM Use Java 17
set JAVA_HOME=C:\Program Files\Zulu\zulu-17
set PATH=%JAVA_HOME%\bin;%PATH%

REM If Gradle is installed
gradle build
```

如果没有本机 Gradle，后续建议补 Gradle Wrapper：

```bat
gradle wrapper --gradle-version 8.8
.\gradlew.bat build
```

## Notes

本模组是 GT15 LightTech Sky 的包专用自创模组，但从现在开始单独维护，方便后续独立修改、打包和发布。
