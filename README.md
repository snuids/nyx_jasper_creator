# Nyx Jasper Creator

A Maven-based Java application that connects to ActiveMQ and listens to a specific queue for message processing.

## Features

- Connects to Apache ActiveMQ broker
- Listens to a configurable queue for JSON-formatted messages
- **Supports both TextMessage and BytesMessage formats**
- **Dynamically generates JasperReports PDF based on incoming messages**
- **Template path and parameters specified in JSON messages**
- **JSON datasource support: fetch data from URLs for dynamic reports**
- **Caches compiled report templates for optimal performance**
- **Publishes periodic status updates to a topic (every 5 seconds)**
- **Tracks message statistics (sent, received, errors)**
- **JSON-formatted status messages with module health information**
- **Template upload via JASPER_UPLOAD queue (base64-encoded JRXML)**
- Graceful shutdown handling
- Comprehensive logging with Logback
- Externalized configuration via properties file or environment variables
- Docker support for containerized deployment

## Prerequisites

- Java 11 or higher
- Maven 3.6+
- ActiveMQ broker (5.x) running locally or remotely
- Docker (optional, for containerized deployment)

## Project Structure

```
nyx_jasper_creator/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/nyx/jasper/
│   │   │       ├── Application.java              # Main application entry point
│   │   │       ├── QueueMessageListener.java     # JMS message listener
│   │   │       ├── StatusPublisher.java          # Periodic status publisher
│   │   │       ├── StatusMessage.java            # Status message model
│   │   │       ├── MessageData.java              # Incoming message model
│   │   │       ├── JasperReportGenerator.java    # Individual report generator
│   │   │       ├── ReportGeneratorManager.java   # Multi-template manager
│   │   │       └── JsonDataSourceHelper.java     # JSON datasource helper
│   │   └── resources/
│   │       ├── application.properties            # Application configuration
│   │       ├── logback.xml                       # Logging configuration
│   │       └── templates/
│   │           ├── report_template.jrxml         # Sample JRXML template
│   │           └── json_datasource_report.jrxml  # JSON datasource example
│   └── test/
│       └── java/
├── pom.xml                                       # Maven configuration
├── sample_message.json                           # Example message format
├── sample_message_with_datasource.json           # Example with JSON datasource
├── sample_data.json                              # Sample JSON data
└── README.md
```

## Configuration

The application can be configured using either:
1. **Properties file**: `src/main/resources/application.properties`
2. **Environment variables**: Override any property (takes precedence)

### Properties File Configuration

Edit `src/main/resources/application.properties` to configure your ActiveMQ connection:

```properties
# ActiveMQ broker URL
activemq.broker.url=tcp://localhost:61616

# Authentication credentials
activemq.username=admin
activemq.password=admin

# Queue name to listen to
activemq.queue.name=nyx.jasper.queue

# Upload queue for templates
activemq.upload.queue.name=JASPER_UPLOAD

# Status Topic Configuration
activemq.status.topic=RPN_MODULE_INFO
status.interval.seconds=5

# Module Information
module.name=nyx_jasper_creator
module.version=1.0.0
```

### Environment Variables Configuration

All properties can be overridden using environment variables:

```bash
export ACTIVEMQ_BROKER_URL=tcp://energy.marmar.ovh:61616
export ACTIVEMQ_USERNAME=admin
export ACTIVEMQ_PASSWORD=admin
export ACTIVEMQ_QUEUE_NAME=nyx.jasper.queue
export ACTIVEMQ_UPLOAD_QUEUE_NAME=JASPER_UPLOAD
export ACTIVEMQ_STATUS_TOPIC=RPN_MODULE_INFO
export STATUS_INTERVAL_SECONDS=5
export MODULE_NAME=NYX_Jasper_Creator
export MODULE_VERSION=1.0.0
export JASPER_OUTPUT_DIRECTORY=reports
```

Environment variables take precedence over properties file values.

### Status Message Format

The application publishes status messages every 5 seconds to the configured topic in the following JSON format:

```json
{
  "error": "OK",
  "type": "lifesign",
  "eventtype": "lifesign",
  "module": "nyx_jasper_creator",
  "version": "1.0.0",
  "alive": 1,
  "errors": 0,
  "internalerrors": 0,
  "heartbeaterrors": 0,
  "messages": 2,
  "received": {
    "/queue/nyx.jasper.queue": 2
  },
  "sent": {
    "/topic/RPN_MODULE_INFO": 60
  },
  "amqclientversion": "2.0.3",
  "starttimets": 1777640020.888352,
  "starttime": "2026-05-01 14:53:40.888352",
  "connections": 1
}
```

### Incoming Message Format

The application listens to the configured queue for JSON messages that trigger report generation. Each message must contain:

- **`template`** (required): Path to the JRXML template file (relative to classpath or absolute path)
- **`parameters`** (optional): Key-value pairs of parameters to pass to the report
- **`outputName`** (optional): Custom name for the generated report file
- **`jsonUrl`** (optional): URL to fetch JSON data for use as a datasource in the report

**Example message:**

```json
{
  "template": "templates/report_template.jrxml",
  "parameters": {
    "REPORT_TITLE": "Sample Report",
    "CUSTOMER_NAME": "John Doe",
    "ORDER_ID": "12345",
    "ORDER_DATE": "2026-05-16",
    "TOTAL_AMOUNT": "1250.00"
  },
  "outputName": "custom_report_name",
  "jsonUrl": "https://api.example.com/data.json"
}
```

**JSON Datasource:**

If `jsonUrl` is provided, the application will:
1. Fetch JSON data from the specified URL (HTTP/HTTPS)
2. Create a JasperReports JSON datasource from the fetched data
3. Use this datasource when filling the report (allows iteration over JSON arrays, etc.)
4. If the URL is invalid or fetch fails, an empty datasource is used instead

This is useful for reports that need to iterate over dynamic data from external APIs or services.

**Supported Message Types:**

The application accepts messages in both formats:
- **TextMessage**: JSON content as plain text
- **BytesMessage**: JSON content as UTF-8 encoded bytes

The application will:
1. Parse the JSON message
2. Load and compile the JRXML template (cached for performance)
3. Generate a PDF report with the provided parameters
4. Save the report to the configured output directory
5. Update statistics in the status message

## Building the Project

```bash
mvn clean package
```

This will create an executable JAR file in the `target/` directory.

## Running the Application

### Option 1: Using Maven

```bash
mvn exec:java -Dexec.mainClass="com.nyx.jasper.Application"
```

### Option 2: Using the JAR

```bash
java -jar target/nyx-jasper-creator-1.0.0.jar
```

### Option 3: Using Docker

See [DOCKER.md](DOCKER.md) for complete Docker deployment guide.

**Quick start:**

```bash
# Build the Docker image
docker build -t nyx-jasper-creator .

# Run with Docker
docker run -d \
  --name nyx-jasper-creator \
  -e ACTIVEMQ_BROKER_URL=tcp://energy.marmar.ovh:61616 \
  -e ACTIVEMQ_USERNAME=admin \
  -e ACTIVEMQ_PASSWORD=admin \
  -v $(pwd)/reports:/app/reports \
  -v $(pwd)/jasperdef:/app/jasperdef \
  nyx-jasper-creator

# Or use docker-compose
docker-compose up -d
```

## Setting up ActiveMQ

If you don't have ActiveMQ running, you can start it using Docker:

```bash
docker run -d --name activemq \
  -p 61616:61616 \
  -p 8161:8161 \
  apache/activemq-classic:latest
```

Access the ActiveMQ Web Console at: http://localhost:8161 (username: admin, password: admin)

## Testing the Application

You can send test messages to the queue using the ActiveMQ Web Console or using a simple producer:

1. Open the ActiveMQ Web Console (http://localhost:8161)
2. Navigate to "Queues"
3. Click on "Send To" for the queue: `nyx.jasper.queue`
4. Enter a JSON message in the format shown above (see [sample_message.json](sample_message.json))

**Example test message:**
```json
{
  "template": "templates/report_template.jrxml",
  "parameters": {
    "REPORT_TITLE": "Test Report",
    "CUSTOMER_NAME": "Test User"
  }
}
```

The application will:
- Receive and parse the JSON message
- Generate a PDF report using the specified template
- Save it to the `reports/` directory
- Log the success and update statistics

### Using JSON Datasources

To create reports that iterate over dynamic data from external APIs, include a `jsonUrl` in your message:

**Example message with JSON datasource:**
```json
{
  "template": "templates/json_datasource_report.jrxml",
  "parameters": {
    "REPORT_TITLE": "Product Inventory Report"
  },
  "jsonUrl": "https://api.example.com/products.json"
}
```

**How it works:**

1. The application fetches JSON data from the specified URL
2. Creates a JasperReports JSON datasource
3. The report template can iterate over JSON arrays using detail bands
4. Each element in the JSON array becomes a row in the report

**Example JSON data structure:**
```json
[
  {"name": "Product A", "value": "1250.00", "status": "Active"},
  {"name": "Product B", "value": "890.50", "status": "Active"}
]
A jasper report creator listening to ActiveMQ
