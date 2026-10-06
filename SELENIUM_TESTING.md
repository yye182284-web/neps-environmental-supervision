# Selenium 四角色端到端测试

端到端测试会在浏览器中执行公众反馈、管理员派单、网格员实测和决策研判完整流程，并在 `backend/target/selenium-report` 生成截图与 HTML 报告。测试默认跳过，只有显式启用时才运行。

另有 `AiWorkspaceBrowserTest`，用于快速检查智能问答工作区和风险预研判页面。

## 前置条件

1. 后端已运行在 `http://localhost:9000`。
2. 前端已运行在 `http://localhost:8080`。
3. 本机已安装 Chrome，并下载与浏览器主版本兼容的 ChromeDriver。

## 执行

```powershell
cd backend
.\mvnw.cmd test `
  "-Dselenium.enabled=true" `
  "-Dselenium.headless=true" `
  "-Dwebdriver.chrome.driver=C:\tools\chromedriver.exe" `
  "-Dselenium.base-url=http://localhost:8080" `
  "-Dtest=SeleniumSmokeTest"
```

将 `C:\tools\chromedriver.exe` 替换为本机实际路径。测试会写入演示反馈和实测记录，建议只对本地演示数据库运行。
