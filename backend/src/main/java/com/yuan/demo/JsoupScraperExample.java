package com.yuan.demo;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 教师笔记中的 Jsoup HTML 解析示例。
 * 该示例是独立命令行工具，不参与系统启动，也不会修改业务数据库。
 */
public final class JsoupScraperExample {
    private JsoupScraperExample() {
    }

    public static void main(String[] args) throws IOException {
        String keyword = args.length > 0 && !args[0].isBlank() ? args[0] : "书籍";
        int page = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        String url = "https://search.dangdang.com/?key="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&act=input&page_index=" + page;

        Document document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "Chrome/120.0.0.0 Safari/537.36")
                .timeout(10_000)
                .get();
        Elements items = document.select("div#search_nature_rg li");
        for (Element item : items) {
            String title = item.select("p.name a").attr("title");
            String price = item.select("span.search_now_price").text();
            String oldPrice = item.select("span.search_pre_price").text();
            String discount = item.select("span.search_discount").text();
            String shop = item.select("p.search_shangjia a").attr("title");
            System.out.printf("%s%n现价:%s 原价:%s 折扣:%s 商家:%s%n----------%n",
                    title, price, oldPrice, discount, shop);
        }
    }
}
