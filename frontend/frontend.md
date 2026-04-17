frontend/
├── public/                     
│   ├── index.html
│   └── favicon.ico
│
├── src/
│   ├── components/             # Reusable components
│   │   ├── Header/
│   │   ├── ProductCard/
│   │   ├── PriceDisplay/
│   │   └── Loader/
│   │
│   ├── pages/                  # Full page components
│   │   ├── Home.jsx
│   │   ├── Compare.jsx
│   │   ├── History.jsx
│   │   └── NotFound.jsx
│   │
│   ├── utils/                  # Helper functions
│   │   └── helpers.js
│   │
│   ├── services/               # API calls
│   │   └── productService.js
│   │
│   ├── hooks/                  # Custom hooks
│   │   └── useFetchPrices.js
│   │
│   ├── context/                # If you use global state
│   │   └── AppContext.js
│   │
│   ├── App.jsx
│   ├── index.js
│   └── index.css
│
├── package.json
├── .gitignore
└── README.md