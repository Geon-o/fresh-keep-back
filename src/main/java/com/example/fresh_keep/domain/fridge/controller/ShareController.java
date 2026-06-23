package com.example.fresh_keep.domain.fridge.controller;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ShareController {

    @GetMapping(value = "/share/fridge", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public String shareFridgeLanding(@RequestParam("uuid") String uuid) {
        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset=\"UTF-8\">\n" +
                "    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                "    <title>FreshKeep 냉장고 공유</title>\n" +
                "    <style>\n" +
                "        body {\n" +
                "            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n" +
                "            background-color: #F8FAFC;\n" +
                "            color: #0F172A;\n" +
                "            display: flex;\n" +
                "            flex-direction: column;\n" +
                "            align-items: center;\n" +
                "            justify-content: center;\n" +
                "            height: 100vh;\n" +
                "            margin: 0;\n" +
                "            padding: 20px;\n" +
                "            box-sizing: border-box;\n" +
                "            text-align: center;\n" +
                "        }\n" +
                "        .card {\n" +
                "            background: white;\n" +
                "            padding: 30px;\n" +
                "            border-radius: 20px;\n" +
                "            box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.05), 0 8px 10px -6px rgba(0, 0, 0, 0.05);\n" +
                "            max-width: 400px;\n" +
                "            width: 100%;\n" +
                "        }\n" +
                "        h2 {\n" +
                "            color: #0F172A;\n" +
                "            margin-top: 0;\n" +
                "            font-size: 22px;\n" +
                "        }\n" +
                "        p {\n" +
                "            color: #475569;\n" +
                "            font-size: 14px;\n" +
                "            line-height: 1.6;\n" +
                "            margin-bottom: 25px;\n" +
                "        }\n" +
                "        .btn {\n" +
                "            display: inline-block;\n" +
                "            background-color: #4F46E5;\n" +
                "            color: white;\n" +
                "            text-decoration: none;\n" +
                "            padding: 12px 24px;\n" +
                "            border-radius: 12px;\n" +
                "            font-weight: 600;\n" +
                "            font-size: 15px;\n" +
                "            transition: background-color 0.2s;\n" +
                "            border: none;\n" +
                "            cursor: pointer;\n" +
                "        }\n" +
                "        .btn:hover {\n" +
                "            background-color: #4338CA;\n" +
                "        }\n" +
                "        .loader {\n" +
                "            border: 3px solid #F3F3F3;\n" +
                "            border-top: 3px solid #4F46E5;\n" +
                "            border-radius: 50%;\n" +
                "            width: 30px;\n" +
                "            height: 30px;\n" +
                "            animation: spin 1s linear infinite;\n" +
                "            margin: 20px auto;\n" +
                "        }\n" +
                "        @keyframes spin {\n" +
                "            0% { transform: rotate(0deg); }\n" +
                "            100% { transform: rotate(360deg); }\n" +
                "        }\n" +
                "    </style>\n" +
                "</head>\n" +
                "<body>\n" +
                "    <div class=\"card\">\n" +
                "        <div id=\"content\">\n" +
                "            <h2>FreshKeep 앱 연결 중 ❄️</h2>\n" +
                "            <p>기기에 설치된 FreshKeep 앱을 실행하고 있습니다. 잠시만 기다려 주세요...</p>\n" +
                "            <div class=\"loader\"></div>\n" +
                "        </div>\n" +
                "    </div>\n" +
                "    <script>\n" +
                "        const uuid = \"" + uuid + "\";\n" +
                "        const appSchema = \"freshkeep://share-fridge?uuid=\" + uuid;\n" +
                "        \n" +
                "        // 앱 기동 시도\n" +
                "        window.location.href = appSchema;\n" +
                "        \n" +
                "        // 2.5초 후 응답이 없을 때 (앱이 설치되지 않은 환경 대응)\n" +
                "        setTimeout(function() {\n" +
                "            document.getElementById(\"content\").innerHTML = `\n" +
                "                <h2>앱 설치가 필요합니다 📲</h2>\n" +
                "                <p>냉장고 공유 기능을 사용하려면 스마트폰에 <b>FreshKeep</b> 앱이 설치되어 있어야 합니다.<br/><br/>앱을 설치하신 뒤, QR 코드를 다시 스캔하거나 공유 링크를 터치해 주세요.</p>\n" +
                "                <a href=\"https://play.google.com/store\" class=\"btn\">FreshKeep 앱 설치하기</a>\n" +
                "            `;\n" +
                "        }, 2500);\n" +
                "    </script>\n" +
                "</body>\n" +
                "</html>";
    }
}
