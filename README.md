# 💰 PriceComp — Smart Price Comparison Platform

> Paste any product URL from Amazon, Flipkart, Croma, Myntra and more — instantly compare prices across 10 e-commerce platforms with AI-powered buy recommendations.

---

## ✨ Features

- 🔍 **Paste any URL** — supports Amazon, Flipkart, Myntra, Meesho, Ajio, Nykaa, Snapdeal, Croma, TataCliq, RelianceDigital
- 📊 **Live Price Comparison** — side-by-side cards with Best Deal badge and % savings
- 📈 **Price History Charts** — 7/14-day interactive graphs powered by Recharts
- 🤖 **AI Recommendations** — Buy Now / Wait / Price Increasing with confidence score
- 🔔 **Price Alerts** — set target price and get email when it drops
- 📋 **Watchlist** — track products across sessions
- 🕵️ **Bot-resistant Scraping** — Playwright headless browser with stealth mode

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Frontend | React 18, Vite, Tailwind CSS, Recharts, Framer Motion |
| Backend | Java 17, Spring Boot 3.x, Spring Security, JPA |
| Database | PostgreSQL |
| Scraper | Python, FastAPI, Playwright, playwright-stealth |

---

## 🚀 Quick Start

You need **3 services** running simultaneously — scraper, backend, frontend.

### 1. Scraper Service (Python + Playwright)

```bash
cd scraper-service-fixed

# Install dependencies
pip install -r requirements.txt
playwright install chromium

# Start on port 8001
python -m uvicorn main:app --port 8001
```

### 2. Backend (Spring Boot)

```bash
# Create PostgreSQL database
createdb price_comparison

# Set credentials in:
# backend/src/main/resources/application.properties
#   spring.datasource.url=jdbc:postgresql://localhost:5432/price_comparison
#   spring.datasource.username=your_username
#   spring.datasource.password=your_password
#   scraper-service.url=http://localhost:8001

cd backend
mvnw.cmd spring-boot:run
```

API runs at: `http://localhost:8080/api`

### 3. Frontend (React)

```bash
# Create frontend/.env
echo "VITE_API_BASE_URL=http://localhost:8080/api" > frontend/.env

cd frontend
npm install
npm run dev
```

App runs at: **http://localhost:5173**

---

## 📡 API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/products/search` | Body: `{ "url": "https://amazon.in/..." }` |
| `GET` | `/api/products/{id}` | Product details + all platform prices |
| `GET` | `/api/products/{id}/prices` | Current prices across platforms |
| `GET` | `/api/products/{id}/history?days=14` | Price history chart data |
| `GET` | `/api/products/{id}/recommendation` | AI buy/wait recommendation |
| `POST` | `/api/products/{id}/track` | Add to watchlist |

---

## 🗂️ Project Structure

```
Price_Compare_web_app/
├── scraper-service-fixed/        # Python FastAPI scraper
│   ├── main.py                   # FastAPI app + endpoints
│   ├── orchestrator.py           # 3-layer scraping fallback
│   ├── requirements.txt
│   └── scrapers/
│       ├── base.py               # Shared Playwright logic
│       ├── amazon.py
│       ├── flipkart.py
│       ├── croma.py
│       ├── myntra.py
│       ├── meesho.py
│       ├── ajio.py
│       ├── nykaa.py
│       ├── snapdeal.py
│       ├── tatacliq.py
│       └── reliancedigital.py
│
├── backend/                      # Spring Boot API
│   └── src/main/java/com/pricecomparison/
│       ├── config/               # CORS, RestTemplate, Scheduler
│       ├── controller/           # REST endpoints
│       ├── service/              # Business logic
│       ├── scraper/              # Java scraper clients
│       ├── repository/           # JPA repositories
│       ├── model/                # JPA entities
│       ├── dto/                  # Request/Response DTOs
│       └── exception/            # Global error handling
│
└── frontend/                     # React + Vite
    └── src/
        ├── components/           # PriceComparison, Charts, Cards
        ├── pages/                # Home, ProductView, Watchlist, Alerts
        ├── context/              # App state
        ├── services/             # API calls
        └── utils/                # Helpers
```

---

## 🔍 How Scraping Works

```
User pastes URL
      ↓
Layer 1 — Playwright scrapes all 10 platforms concurrently
      ↓
Layer 2 — Google Discovery finds URLs for any failed platforms
      ↓
Layer 3 — Database cache fallback for still-missing platforms
      ↓
Frontend shows all platforms with Live / Cached / Not Available badges
```

---

## ⚙️ Optional Configuration

| Feature | How to enable |
|---------|--------------|
| AI recommendations | Set `OPENAI_API_KEY` in environment |
| H2 in-memory DB (dev) | Switch driver in `application-dev.properties` |
| Email alerts | Configure SMTP in `application.properties` |

---

## 📝 Notes

- This is an academic mini-project built for educational purposes
- Scraping respects rate limits (1 req/4s per domain)
- Prices may lag real-time by a few minutes due to cache
- Some platforms may show "Not Available" if anti-bot detection triggers
