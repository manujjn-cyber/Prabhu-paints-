FROM python:3.12-slim
WORKDIR /app
COPY server /app
ENV DB_PATH=/data/shop.db
ENV PORT=8080
EXPOSE 8080
CMD ["python", "app.py"]
