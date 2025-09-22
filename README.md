# bajajfinserv_api_sql_solver

Spring Boot app that:
- POSTs to generateWebhook endpoint
- Downloads question PDF (Google Drive link)
- Extracts text using PDFBox
- Heuristically creates an SQL `finalQuery`
- Stores result into H2
- POSTs `finalQuery` to testWebhook with Authorization header

## Prerequisites
- Java 17+
- Maven

## How to run
1. Set your details in `DemoApplication` (name, regNo, email) or pass env vars.
2. Build:

mvn clean package

3. Run:

java -jar target/webhook-solver-0.0.1-SNAPSHOT.jar

4. Watch logs for progress (generateWebhook, download, extract, post result).

## Notes
- For Google Drive file download: this code attempts a simple GET. If Drive returns HTML, convert the `/d/<ID>/view` URL to `https://drive.google.com/uc?export=download&id=<ID>`.
- Inspect `application.properties` to change endpoints.