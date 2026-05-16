# Creating Custom JRXML Templates

This guide explains how to create custom JasperReports templates for the Nyx Jasper Creator application.

## Template Parameters

When creating JRXML templates, you can define parameters that will be populated from the JSON message sent to the queue.

### Default Parameters

The following parameters are automatically provided by the application:

- **`MESSAGE_DATA`** (String): The raw JSON message data as a string
- **`GENERATION_TIME`** (String): Timestamp when the report was generated (format: "yyyy-MM-dd HH:mm:ss")

### Custom Parameters

You can define any additional parameters in your JRXML template. These should match the keys in the `parameters` object of your JSON message.

**Example parameter definition in JRXML:**

```xml
<parameter name="CUSTOMER_NAME" class="java.lang.String" isForPrompting="false">
    <defaultValueExpression><![CDATA["N/A"]]></defaultValueExpression>
</parameter>
```

**Corresponding JSON message:**

```json
{
  "template": "templates/my_report.jrxml",
  "parameters": {
    "CUSTOMER_NAME": "John Doe"
  }
}
```

## Supported Parameter Types

JasperReports supports various parameter types. When sending from JSON:

- **String**: `"parameters": {"NAME": "John Doe"}`
- **Integer**: `"parameters": {"COUNT": 42}`
- **Double**: `"parameters": {"AMOUNT": 1234.56}`
- **Boolean**: `"parameters": {"IS_ACTIVE": true}`

## Template Location

Templates can be:

1. **In the classpath**: Place in `src/main/resources/templates/`
   - Reference as: `"template": "templates/my_report.jrxml"`

2. **Absolute path**: Place anywhere on the filesystem
   - Reference as: `"template": "/path/to/my_report.jrxml"`

## Example Template Structure

```xml
<?xml version="1.0" encoding="UTF-8"?>
<jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports"
              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
              xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports
              http://jasperreports.sourceforge.net/xsd/jasperreport.xsd"
              name="custom_report" pageWidth="595" pageHeight="842">
    
    <!-- Define parameters -->
    <parameter name="REPORT_TITLE" class="java.lang.String"/>
    <parameter name="CUSTOM_DATA" class="java.lang.String"/>
    
    <!-- Title section -->
    <title>
        <band height="100">
            <textField>
                <reportElement x="0" y="0" width="555" height="40"/>
                <textElement textAlignment="Center">
                    <font size="20" isBold="true"/>
                </textElement>
                <textFieldExpression><![CDATA[$P{REPORT_TITLE}]]></textFieldExpression>
            </textField>
        </band>
    </title>
    
    <!-- Content section -->
    <detail>
        <band height="200">
            <textField>
                <reportElement x="0" y="0" width="555" height="100"/>
                <textFieldExpression><![CDATA[$P{CUSTOM_DATA}]]></textFieldExpression>
            </textField>
        </band>
    </detail>
    
</jasperReport>
```

## Testing Your Template

1. Create your JRXML template
2. Place it in `src/main/resources/templates/`
3. Send a test message to the queue:

```json
{
  "template": "templates/your_template.jrxml",
  "parameters": {
    "REPORT_TITLE": "Test Report",
    "CUSTOM_DATA": "This is test data"
  }
}
```

4. Check the `reports/` directory for the generated PDF

## Template Caching

The application caches compiled templates for performance. To reload a template after changes:

- Restart the application, or
- Implement a reload mechanism (future enhancement)

## Resources

- [JasperReports Documentation](https://jasperreports.sourceforge.net/documentation.html)
- [JRXML Reference](https://jasperreports.sourceforge.net/dtds/jasperreport.dtd)
- Sample template: [report_template.jrxml](src/main/resources/templates/report_template.jrxml)
