package com.example.webhooksolver;

import com.example.webhooksolver.dto.WebhookResponse;
import com.example.webhooksolver.model.SolvedQuestion;
import com.example.webhooksolver.repo.SolvedQuestionRepository;
import com.example.webhooksolver.service.SqlSolver;
import com.example.webhooksolver.util.PdfUtil;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SpringBootApplication
public class DemoApplication {
    @Value("${app.generateWebhookUrl}")
    private String generateWebhookUrl;
    @Value("${app.testWebhookUrl}")
    private String testWebhookUrl;

    public static void main(String[] args) { SpringApplication.run(DemoApplication.class, args); }

    @Bean
    public RestTemplate restTemplate() { return new RestTemplate(); }

    @Bean
    public ApplicationRunner runner(RestTemplate restTemplate,
                                    SolvedQuestionRepository repo,
                                    SqlSolver solver) {
        return args -> {
            // ========== Edit these details for your submission ==========
            Map<String,String> body = Map.of(
                    "name","Your Name",
                    "regNo","REG12347",
                    "email","your.email@example.com"
            );
            // ==========================================================

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String,String>> req = new HttpEntity<>(body, headers);

            System.out.println("POST -> generateWebhook ...");
            ResponseEntity<WebhookResponse> resp;
            try {
                resp = restTemplate.exchange(
                        generateWebhookUrl, HttpMethod.POST, req, WebhookResponse.class);
            } catch (Exception ex) {
                System.err.println("Error calling generateWebhook: " + ex.getMessage());
                return;
            }

            if(resp.getStatusCode().is2xxSuccessful() && resp.getBody()!=null) {
                WebhookResponse wr = resp.getBody();
                System.out.println("Got webhook: " + wr.getWebhook());
                System.out.println("Got accessToken (truncated): " + (wr.getAccessToken()!=null ? wr.getAccessToken().substring(0, Math.min(10, wr.getAccessToken().length())) + "..." : "null"));

                // determine question link using last two digits of regNo
                String regNo = body.get("regNo");
                int lastTwo = extractLastTwoDigits(regNo);
                boolean isOdd = (lastTwo % 2) == 1;

                String q1 = "https://drive.google.com/file/d/1IeSI6l6KoSQAFfRihIT9tEDICtoz-G/view?usp=sharing";
                String q2 = "https://drive.google.com/file/d/143MR5cLFrlNEuHzzWJ5RHnEWuijuM9X/view?usp=sharing";
                String questionLink = isOdd ? q1 : q2;
                System.out.println("Selected question link: " + questionLink + " (lastTwo=" + lastTwo + ")");

                File pdf = new File(System.getProperty("java.io.tmpdir"), "question.pdf");
                try {
                    // convert drive view to uc?export=download if needed
                    String downloadUrl = convertDriveViewToDownload(questionLink);
                    PdfUtil.downloadFileFromUrl(downloadUrl, pdf);
                    System.out.println("Downloaded PDF to: " + pdf.getAbsolutePath());

                    String text = PdfUtil.extractText(pdf);
                    System.out.println("Extracted PDF text length: " + text.length());

                    Optional<String> sql = solver.solveFromText(text);
                    String finalQuery = sql.orElse("/* Unable to auto-solve: please open PDF and create final SQL manually */");
                    System.out.println("FinalQuery chosen:\n" + finalQuery);

                    SolvedQuestion s = new SolvedQuestion();
                    s.setRegNo(regNo);
                    s.setQuestionLink(questionLink);
                    s.setFinalQuery(finalQuery);
                    s.setCreatedAt(LocalDateTime.now());
                    repo.save(s);

                    HttpHeaders h2 = new HttpHeaders();
                    h2.setContentType(MediaType.APPLICATION_JSON);
                    if(StringUtils.hasText(wr.getAccessToken())) {
                        h2.setBearerAuth(wr.getAccessToken());
                    }
                    Map<String,String> body2 = Map.of("finalQuery", finalQuery);
                    HttpEntity<Map<String,String>> req2 = new HttpEntity<>(body2, h2);

                    System.out.println("POST -> testWebhook (sending finalQuery) ...");
                    ResponseEntity<String> resp2 = restTemplate.postForEntity(
                            testWebhookUrl, req2, String.class);

                    System.out.println("testWebhook response status: " + resp2.getStatusCode());
                    System.out.println("testWebhook response body: " + resp2.getBody());

                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.err.println("Error in flow: " + ex.getMessage());
                }
            } else {
                System.err.println("generateWebhook failed: " + resp.getStatusCode());
            }
        };
    }

    private static int extractLastTwoDigits(String regNo) {
        if(regNo==null) return 0;
        Pattern p = Pattern.compile("(\\d{2})\\D*$");
        Matcher m = p.matcher(regNo);
        if(m.find()) {
            return Integer.parseInt(m.group(1));
        }
        Matcher m2 = Pattern.compile("(\\d{1,2})$").matcher(regNo);
        if(m2.find()) return Integer.parseInt(m2.group(1));
        return 0;
    }

    // Convert google drive "view" url to direct download url if possible
    private static String convertDriveViewToDownload(String url) {
        if(url == null) return null;
        // pattern: /d/<id>/view
        Pattern p = Pattern.compile("/d/([A-Za-z0-9_-]+)");
        Matcher m = p.matcher(url);
        if(m.find()) {
            String id = m.group(1);
            return "https://drive.google.com/uc?export=download&id=" + id;
        }
        return url;
    }
}