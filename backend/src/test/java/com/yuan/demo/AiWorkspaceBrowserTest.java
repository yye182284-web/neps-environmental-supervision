package com.yuan.demo;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Focused visual smoke test for the local RAG and explainable incident-triage workspace. */
@Tag("selenium")
class AiWorkspaceBrowserTest {

    @Test
    void shouldRenderAssistantAndRiskWorkbench() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("selenium.enabled"), "Selenium 默认关闭");
        String driverPath = System.getProperty("webdriver.chrome.driver", "");
        Assumptions.assumeTrue(!driverPath.isBlank() && Files.isRegularFile(Path.of(driverPath)), "需要 ChromeDriver");
        String baseUrl = System.getProperty("selenium.base-url", "http://localhost:8080");
        System.setProperty("webdriver.chrome.driver", driverPath);

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new", "--window-size=1600,1000", "--disable-blink-features=AutomationControlled");
        ChromeDriver driver = createDriver(options);
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(35));
            driver.get(baseUrl + "/");
            click(driver, wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//div[contains(@class,'role-tabs')]//button[contains(.,'系统管理员')]"))));
            List<WebElement> inputs = driver.findElements(By.cssSelector(".input-wrap input"));
            setValue(driver, inputs.get(0), "admin");
            setValue(driver, inputs.get(1), "123456");
            click(driver, driver.findElement(By.cssSelector("button.primary-btn[type='submit']")));
            wait.until(current -> current.getCurrentUrl().contains("/admin"));

            driver.get(baseUrl + "/ai");
            WebElement shell = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".ai-shell")));
            assertThat(shell.getText()).contains("环境智能协作中心", "本地知识检索", "数据研判");

            click(driver, driver.findElement(By.xpath("//div[contains(@class,'workspace-tabs')]//button[contains(.,'风险预研判')]")));
            WebElement riskInput = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".risk-input-panel textarea")));
            setValue(driver, riskInput, "今晚22点，某小学东侧化工园附近持续出现刺鼻异味和大量黑烟，有居民眼睛刺痛。");
            click(driver, driver.findElement(By.cssSelector(".risk-controls button")));
            WebElement result = wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".score-card")));
            assertThat(result.getText()).contains("紧急", "96");

            Path report = Path.of("target", "selenium-report");
            Files.createDirectories(report);
            Files.write(report.resolve("ai-workspace.png"), driver.getScreenshotAs(OutputType.BYTES));
        } finally {
            driver.quit();
        }
    }

    private ChromeDriver createDriver(ChromeOptions options) {
        try {
            return new ChromeDriver(options);
        } catch (WebDriverException exception) {
            Assumptions.assumeTrue(false,
                    "ChromeDriver 与本机 Chrome 不兼容，请更新驱动后再运行：" + exception.getMessage());
            throw exception;
        }
    }

    private void setValue(ChromeDriver driver, WebElement element, String value) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].value=arguments[1];arguments[0].dispatchEvent(new Event('input',{bubbles:true}));",
                element, value);
    }

    private void click(ChromeDriver driver, WebElement element) {
        ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({block:'center'});arguments[0].click();", element);
    }
}
