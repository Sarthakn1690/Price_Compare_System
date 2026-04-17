@echo off
echo Starting PriceCompare SaaS Stack...

echo [1/3] Starting Python Scraper Service (Port 8000)...
start /B cmd /c "cd scraper-service-fixed && pip install -r requirements.txt && playwright install chromium && uvicorn main:app --port 8000"

echo [2/3] Starting Java Backend (Port 8080)...
start /B cmd /c "cd backend && mvnw.cmd spring-boot:run"

echo [3/3] Starting React Frontend (Port 5173)...
start /B cmd /c "cd frontend && npm install && npm run dev"

echo All services are starting up. 
echo - Frontend: http://localhost:5173
echo - Backend:  http://localhost:8080
echo - Scrapers: http://localhost:8000/health
pause
