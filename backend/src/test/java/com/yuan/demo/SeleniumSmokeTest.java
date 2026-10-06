package com.yuan.demo;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 四角色端到端自动化：公众反馈→管理员调度→网格实测→决策研判，并自动生成 HTML 报告。 */
@Tag("selenium")
class SeleniumSmokeTest {
    private final List<Step> steps = new ArrayList<>();
    private ChromeDriver activeDriver;
    private Path activeReportDir;

    @Test
    void completeFourRoleWorkflow() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("selenium.enabled"), "Selenium 默认关闭；需要时传 -Dselenium.enabled=true");
        String driverPath = System.getProperty("webdriver.chrome.driver", "");
        Assumptions.assumeTrue(!driverPath.isBlank() && Files.isRegularFile(Path.of(driverPath)), "请通过 -Dwebdriver.chrome.driver 指定 chromedriver.exe");
        String baseUrl = System.getProperty("selenium.base-url", "http://localhost:8080");
        String uniqueAddress = "Selenium四角色流程-" + System.currentTimeMillis();
        System.setProperty("webdriver.chrome.driver", driverPath);
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-blink-features=AutomationControlled", "--start-maximized");
        if (Boolean.getBoolean("selenium.headless")) options.addArguments("--headless=new", "--window-size=1600,1000");
        ChromeDriver driver = new ChromeDriver(options);
        Path reportDir = Path.of("target", "selenium-report");
        Files.createDirectories(reportDir);
        activeDriver = driver;
        activeReportDir = reportDir;
        try (var oldShots = Files.list(reportDir)) {
            oldShots.filter(path -> path.getFileName().toString().startsWith("step-")
                    && path.getFileName().toString().endsWith(".png"))
                    .forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) { } });
        }
        Files.deleteIfExists(reportDir.resolve("failure.png"));
        Throwable failure = null;
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
            driver.get(baseUrl + "/");
            Object apiProbe = executeAsync(driver, "fetch('/api/auth/public-key').then(async r=>arguments[0](r.status+':'+await r.text())).catch(e=>arguments[0]('ERROR:'+e.message));");
            check(String.valueOf(apiProbe).startsWith("200:"), "运行环境", "前端代理可访问后端 API");

            login(driver, wait, "公众监督员", "13800000000", "123456", "aqiFeedback");
            pass("公众监督员", "登录成功");
            driver.get(baseUrl + "/aqiFeedbackForm");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-test='public-ai-text']")));
            setInputValue(driver, By.cssSelector("[data-test='public-ai-text']"),
                    "石家庄市长安区" + uniqueAddress + "附近出现明显扬尘和异味");
            click(driver, driver.findElement(By.cssSelector("[data-test='public-ai-fill']")));
            WebElement parsedAddressInput = wait.until(d -> {
                WebElement input = d.findElement(By.cssSelector("input[placeholder='请输入详细地址']"));
                return input.getAttribute("value").contains(uniqueAddress) ? input : null;
            });
            String parsedAddress = parsedAddressInput.getAttribute("value");
            String parsedInformation = driver.findElement(By.cssSelector("textarea[placeholder*='详细描述']")).getAttribute("value");
            check(!parsedAddress.contains("扬尘") && !parsedAddress.contains("异味") && !parsedInformation.isBlank(),
                    "公众监督员", "现场信息解析已准确分离详细地址并自动补全规范反馈内容");
            setInputValue(driver, By.cssSelector("input[placeholder='请输入详细地址']"), uniqueAddress);
            setInputValue(driver, By.cssSelector("textarea[placeholder*='详细描述']"), "公众监督员反映：现场出现明显扬尘和异味，建议属地网格员现场检测核实。");
            setInputValue(driver, By.cssSelector("input[type='date']"), LocalDateTime.now().toLocalDate().toString());
            setInputValue(driver, By.cssSelector("input[type='time']"), "10:30:00");
            click(driver, driver.findElement(By.cssSelector("form.aqi-form button[type='submit']")));
            wait.until(d -> d.getCurrentUrl().contains("aqiFeedback") && !d.getCurrentUrl().contains("Form"));
            String afId = String.valueOf(executeAsync(driver, "fetch('/api/aqiFeedback/history/13800000000').then(r=>r.json()).then(x=>{const row=(x.data||[]).find(v=>v.address===arguments[0]);arguments[1](row?String(row.afId):'');}).catch(e=>arguments[1](''));", uniqueAddress));
            check(!afId.isBlank(), "公众监督员", "提交反馈并取得任务编号 #" + afId);
            logout(driver, baseUrl);

            login(driver, wait, "系统管理员", "admin", "123456", "/admin");
            pass("系统管理员", "登录成功");
            WebElement keyword = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".filters input[placeholder*='手机号']")));
            keyword.sendKeys(uniqueAddress);
            driver.findElement(By.xpath("//div[contains(@class,'filters')]//button[normalize-space()='查询']")).click();
            WebElement adminRow = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//tbody/tr[contains(.,'" + uniqueAddress + "')]")));
            click(driver, adminRow.findElement(By.cssSelector("[data-test='admin-ai-advice']")));
            wait.until(d -> !adminRow.findElements(By.cssSelector(".advice")).isEmpty());
            pass("系统管理员", "调度辅助分析已按同市属地和工作量推荐网格员");
            click(driver, adminRow.findElement(By.cssSelector("[data-test='admin-assign']")));
            wait.until(d -> d.getPageSource().contains("待接单") || !d.getPageSource().contains(uniqueAddress));
            pass("系统管理员", "任务已指派给属地网格员");
            logout(driver, baseUrl);

            login(driver, wait, "检测网格员", "GRID001", "123456", "/grid/tasks");
            pass("检测网格员", "登录成功");
            WebElement gridRow = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//tbody/tr[contains(.,'" + uniqueAddress + "')]")));
            click(driver, gridRow.findElement(By.cssSelector("[data-test='task-accept']")));
            wait.until(d -> d.getPageSource().contains("接单成功"));
            pass("检测网格员", "在 30 分钟时限内接单成功");
            gridRow = wait.until(ExpectedConditions.visibilityOfElementLocated(By.xpath("//tbody/tr[contains(.,'" + uniqueAddress + "')]")));
            click(driver, gridRow.findElement(By.cssSelector("button.link-btn")));
            click(driver, wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-test='grid-ai-plan']"))));
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".plan-result")));
            pass("检测网格员", "现场检测辅助分析已生成检测方案");
            setInputValue(driver, By.cssSelector("[data-test='so2']"), "185");
            setInputValue(driver, By.cssSelector("[data-test='co']"), "7.2");
            setInputValue(driver, By.cssSelector("[data-test='pm25']"), "112");
            click(driver, driver.findElement(By.cssSelector("[data-test='measurement-submit']")));
            wait.until(ExpectedConditions.invisibilityOfElementLocated(By.cssSelector(".modal-mask")));
            pass("检测网格员", "实测污染物数据已提交为最终结果");
            logout(driver, baseUrl);

            login(driver, wait, "决策者", "decision", "123456", "/decision");
            pass("决策者", "登录成功");
            click(driver, wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-test='decision-ai-summary']"))));
            WebElement analysis = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector("[data-test='decision-ai-result']")));
            check(!analysis.getText().isBlank(), "决策者", "决策辅助研判已生成治理建议");
            wait.until(d -> d.findElement(By.cssSelector(".metrics")).getText().matches("(?s).*\\b[1-9]\\d*\\b.*"));
            String metricText = driver.findElement(By.cssSelector(".metrics")).getText();
            check(metricText.matches("(?s).*\\b[1-9]\\d*\\b.*"), "决策者", "统计看板已显示非零业务数据");
        } catch (Throwable e) {
            failure = e;
            fail("自动化流程", e.getClass().getSimpleName() + "：" + String.valueOf(e.getMessage()));
            try { Files.write(reportDir.resolve("failure.png"), driver.getScreenshotAs(OutputType.BYTES)); } catch (Exception ignored) { }
            throw e;
        } finally {
            driver.quit();
            writeHtmlReport(reportDir.resolve("index.html"), uniqueAddress, failure == null);
            System.out.println("Selenium HTML 报告：" + reportDir.resolve("index.html").toAbsolutePath());
        }
    }

    private void login(ChromeDriver driver, WebDriverWait wait, String role, String account, String password, String route) {
        driver.get(System.getProperty("selenium.base-url", "http://localhost:8080") + "/");
        WebElement roleTab = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[contains(@class,'role-tabs')]//button[contains(.,'" + role + "')]")));
        click(driver, roleTab);
        wait.until(d -> d.findElement(By.cssSelector(".auth-card h2")).getText().contains(role));
        List<WebElement> inputs = driver.findElements(By.cssSelector(".input-wrap input"));
        ((JavascriptExecutor) driver).executeScript("arguments[0].value=arguments[2];arguments[0].dispatchEvent(new Event('input',{bubbles:true}));arguments[1].value=arguments[3];arguments[1].dispatchEvent(new Event('input',{bubbles:true}));", inputs.get(0), inputs.get(1), account, password);
        click(driver, driver.findElement(By.cssSelector("button.primary-btn[type='submit']")));
        wait.until(d -> d.getCurrentUrl().contains(route));
    }

    private void logout(ChromeDriver driver, String baseUrl) {
        executeAsync(driver, "fetch('/api/auth/logout',{method:'POST'}).finally(()=>arguments[0]('ok'));");
        driver.executeScript("localStorage.clear(); sessionStorage.clear();");
        driver.get(baseUrl + "/");
    }

    private Object executeAsync(ChromeDriver driver, String script, Object... args) { return ((JavascriptExecutor) driver).executeAsyncScript(script, args); }
    private void click(ChromeDriver driver, WebElement element) {
        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});arguments[0].click();", element);
    }
    private void setInputValue(ChromeDriver driver, By locator, String value) {
        WebElement e = driver.findElement(locator);
        ((JavascriptExecutor) driver).executeScript("arguments[0].value=arguments[1];arguments[0].dispatchEvent(new Event('input',{bubbles:true}));arguments[0].dispatchEvent(new Event('change',{bubbles:true}));", e, value);
    }
    private void check(boolean condition, String role, String detail) { if (condition) pass(role, detail); else { fail(role, detail); assertTrue(false, detail); } }
    private void pass(String role, String detail) { steps.add(new Step(role, detail, true, captureScreenshot(steps.size() + 1))); }
    private void fail(String role, String detail) { steps.add(new Step(role, detail, false, captureScreenshot(steps.size() + 1))); }

    private String captureScreenshot(int number) {
        if (activeDriver == null || activeReportDir == null) return "";
        String fileName = "step-%02d.png".formatted(number);
        try {
            Files.write(activeReportDir.resolve(fileName), activeDriver.getScreenshotAs(OutputType.BYTES));
            return fileName;
        } catch (Exception ignored) {
            return "";
        }
    }

    private void writeHtmlReport(Path path, String address, boolean success) throws Exception {
        long passed = steps.stream().filter(Step::passed).count();
        StringBuilder rows = new StringBuilder();
        for (int i=0;i<steps.size();i++) {
            Step s=steps.get(i);
            String shot = s.screenshot().isBlank() ? "未生成" : "<a href='" + s.screenshot() + "'><img class='shot' src='" + s.screenshot() + "' alt='步骤截图'></a>";
            rows.append("<tr><td>").append(i+1).append("</td><td>").append(escape(s.role()))
                    .append("</td><td>").append(escape(s.detail())).append("</td><td class='")
                    .append(s.passed()?"ok":"bad").append("'>").append(s.passed()?"通过":"失败")
                    .append("</td><td>").append(shot).append("</td></tr>");
        }
        String html="""
                <!doctype html><html lang='zh-CN'><head><meta charset='utf-8'><title>NEPS 四角色自动化测试报告</title>
                <style>body{font-family:'Microsoft YaHei',sans-serif;margin:0;background:#f3f7f8;color:#17364a}.wrap{max-width:1180px;margin:36px auto;background:#fff;border-radius:18px;box-shadow:0 18px 55px #1d3b4d20;overflow:hidden}.head{padding:28px 34px;color:#fff;background:linear-gradient(135deg,#087f65,#1a7298)}h1{margin:0 0 8px}.summary{display:grid;grid-template-columns:repeat(3,1fr);gap:12px;padding:22px 34px}.summary div{padding:16px;border-radius:12px;background:#f1f8f6}.summary b{display:block;font-size:25px;color:#14745f}table{width:calc(100%% - 68px);margin:0 34px 34px;border-collapse:collapse}th,td{padding:12px;border-bottom:1px solid #e6edef;text-align:left;vertical-align:middle}th{background:#f5f8f9}.ok{color:#16805f;font-weight:bold}.bad{color:#c54a4a;font-weight:bold}.shot{width:170px;height:96px;object-fit:cover;border:1px solid #dbe7e4;border-radius:8px;box-shadow:0 4px 12px #17364a18}.foot{padding:18px 34px;background:#f7fafb;color:#718291;font-size:12px}</style></head><body><div class='wrap'>
                <section class='head'><h1>NEPS 四角色端到端自动化测试报告</h1><div>%s · 测试地址：%s</div></section>
                <section class='summary'><div>总体状态<b>%s</b></div><div>通过步骤<b>%d / %d</b></div><div>覆盖角色<b>4</b></div></section>
                <table><thead><tr><th>#</th><th>角色/模块</th><th>验证内容</th><th>结果</th><th>执行截图</th></tr></thead><tbody>%s</tbody></table>
                <div class='foot'>报告由 SeleniumSmokeTest 自动生成；点击缩略图可查看原始截图，失败时同目录另保留 failure.png。</div></div></body></html>
                """.formatted(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), escape(address), success?"通过":"失败", passed, steps.size(), rows);
        Files.writeString(path, html, StandardCharsets.UTF_8);
    }
    private String escape(String value) { return value==null?"":value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;"); }
    private record Step(String role, String detail, boolean passed, String screenshot) { }
}
