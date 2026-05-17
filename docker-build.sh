#!/bin/bash
# Quick start script for NYX Jasper Creator with Docker

set -e

echo "🚀 Building NYX Jasper Creator Docker image..."
docker build -t nyx-jasper-creator .

echo ""
echo "✅ Build complete!"
echo ""
echo "To run the container, use one of the following methods:"
echo ""
echo "1. Using docker-compose (recommended):"
echo "   docker-compose up -d"
echo ""
echo "2. Using docker run:"
echo "   docker run -d \\"
echo "     --name nyx-jasper-creator \\"
echo "     -e ACTIVEMQ_BROKER_URL=tcp://localhost:61616 \\"
echo "     -e ACTIVEMQ_USERNAME=admin \\"
echo "     -e ACTIVEMQ_PASSWORD=admin \\"
echo "     -v \$(pwd)/reports:/app/reports \\"
echo "     -v \$(pwd)/jasperdef:/app/jasperdef \\"
echo "     nyx-jasper-creator"
echo ""
echo "To view logs:"
echo "   docker logs -f nyx-jasper-creator"
echo ""
