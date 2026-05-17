# REST API Documentation

## Overview

The NYX Jasper Creator provides a REST API as an alternative to the ActiveMQ queue-based approach for generating reports. The API accepts the same message format as the queue.

## Base URL

```
http://localhost:8080
```

## Endpoints

### 1. Generate Report

Generate a JasperReports PDF from a JRXML template.

**Endpoint:** `POST /api/v1/generate-report`

**Content-Type:** `application/json`

**Request Body:**

Same format as ActiveMQ queue messages:

```json
{
  "template": "templates/report_template.jrxml",
  "parameters": {
    "REPORT_TITLE": "Sample Report",
    "CUSTOMER_NAME": "John Doe",
    "ORDER_ID": "12345",
    "ORDER_DATE": "2026-05-17",
    "TOTAL_AMOUNT": "1250.00"
  },
  "outputName": "custom_report_name",
  "jsonUrl": "https://api.example.com/data.json"
}
```

**Fields:**
- `template` (required): Path to the JRXML template file
- `parameters` (optional): Key-value pairs of parameters to pass to the report
- `outputName` (optional): Custom name for the generated report file
- `jsonUrl` (optional): URL to fetch JSON data for use as a datasource

**Success Response:**

```json
{
  "success": true,
  "message": "Report generated successfully",
  "reportPath": "reports/report_20260517_103208_194.pdf",
  "template": "templates/report_template.jrxml"
}
```

**Error Responses:**

```json
{
  "error": "Missing required field",
  "message": "Field 'template' is required"
}
```

```json
{
  "error": "Report generation failed",
  "message": "Template not found: templates/invalid.jrxml"
}
```

### 2. Health Check

Check if the application is running.

**Endpoint:** `GET /health`

**Response:**

```json
{
  "status": "UP",
  "module": "NYX_Jasper_Creator",
  "version": "1.0.0"
}
```

### 3. Get Status

Get current application status and statistics.

**Endpoint:** `GET /api/v1/status`

**Response:**

```json
{
  "error": "OK",
  "type": "lifesign",
  "eventtype": "lifesign",
  "module": "NYX_Jasper_Creator",
  "version": "1.0.0",
  "alive": 1,
  "errors": 0,
  "internalerrors": 0,
  "heartbeaterrors": 0,
  "messages": 15,
  "received": {
    "/queue/nyx.jasper.queue": 12,
    "/queue/JASPER_UPLOAD": 3
  },
  "sent": {
    "/topic/RPN_MODULE_INFO": 125
  },
  "amqclientversion": "2.0.3",
  "starttimets": 1715943020.888352,
  "starttime": "2026-05-17 10:37:00.888352",
  "connections": 1,
  "icon": "calendar"
}
```

## Configuration

### Enable/Disable REST API

Set in `application.properties`:
```properties
rest.api.enabled=true
rest.api.port=8080
```

Or via environment variables:
```bash
export REST_API_ENABLED=true
export REST_API_PORT=8080
```

### Docker Configuration

The Docker container exposes port 8080 by default. Map it to a host port:

```bash
docker run -p 8080:8080 nyx-jasper-creator
```

Or in `docker-compose.yml`:
```yaml
ports:
  - "8080:8080"
```

## Example Usage

### cURL

```bash
# Generate a report
curl -X POST http://localhost:8080/api/v1/generate-report \
  -H "Content-Type: application/json" \
  -d '{
    "template": "templates/report_template.jrxml",
    "parameters": {
      "REPORT_TITLE": "Test Report",
      "CUSTOMER_NAME": "Jane Smith"
    }
  }'

# Check health
curl http://localhost:8080/health

# Get status
curl http://localhost:8080/api/v1/status
```

### Python

```python
import requests

# Generate a report
response = requests.post(
    'http://localhost:8080/api/v1/generate-report',
    json={
        'template': 'templates/report_template.jrxml',
        'parameters': {
            'REPORT_TITLE': 'Test Report',
            'CUSTOMER_NAME': 'Jane Smith'
        }
    }
)

print(response.json())
```

### JavaScript/Node.js

```javascript
// Generate a report
fetch('http://localhost:8080/api/v1/generate-report', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
  },
  body: JSON.stringify({
    template: 'templates/report_template.jrxml',
    parameters: {
      REPORT_TITLE: 'Test Report',
      CUSTOMER_NAME: 'Jane Smith'
    }
  })
})
.then(response => response.json())
.then(data => console.log(data));
```

## Error Handling

The API uses standard HTTP status codes:

- `200 OK`: Report generated successfully
- `400 Bad Request`: Invalid request (missing fields, invalid JSON)
- `500 Internal Server Error`: Report generation failed

## Security Considerations

⚠️ **Important**: The REST API does not include authentication by default. For production use, consider:

1. Adding authentication (API keys, JWT, etc.)
2. Using HTTPS/TLS
3. Rate limiting
4. Firewall rules to restrict access
5. Running behind a reverse proxy (nginx, Apache)

## Performance

- Templates are compiled and cached for better performance
- Concurrent requests are supported
- Report generation is synchronous (response waits for PDF generation)
