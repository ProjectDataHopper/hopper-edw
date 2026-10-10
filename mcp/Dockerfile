FROM python:3.12-slim

WORKDIR /app

# Install system dependencies if any
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install
COPY requirements.txt /app/
RUN pip install --no-cache-dir -r requirements.txt

# Copy server code and harvested knowledge
COPY server/ /app/server/
COPY harvester/output/ /app/output/

EXPOSE 8000

ENV PORT=8000
ENV PYTHONUNBUFFERED=1

CMD ["python", "server/main.py", "--transport", "sse", "--host", "0.0.0.0", "--port", "8000"]
